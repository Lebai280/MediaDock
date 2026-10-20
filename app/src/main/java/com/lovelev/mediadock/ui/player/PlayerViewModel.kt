package com.lovelev.mediadock.ui.player

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lovelev.mediadock.data.local.LocalMedia
import com.lovelev.mediadock.core.model.MediaTypes
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.core.model.SourcePaths
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.core.model.TitleParser
import com.lovelev.mediadock.data.repo.LocalSubtitle
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.subtitle.SubtitleHit
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavUrl
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.player.PlaybackService
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** 队列里的一条：媒体 id 必须是 [path]，进度就是按它落的库。 */
data class Playable(
    val path: String,
    val url: String,
    val subtitleUrls: List<String>,
)

sealed interface PlayerState {
    data object Loading : PlayerState

    /**
     * @param items 播放队列（同目录的视频），按显示顺序。
     * @param startIndex [items] 里点中的那个。
     * @param startPositionMs 续播位置，没有进度就是 0。
     */
    data class Ready(
        val items: List<Playable>,
        val startIndex: Int,
        val startPositionMs: Long,
    ) : PlayerState

    data class Unavailable(val message: String) : PlayerState
}

/**
 * 播放页。
 *
 * **媒体控制器（MediaController）由这个 ViewModel 持有，不放在 composable 里** —— 这是有意的：
 *
 *  - 控制器在 ViewModel 里能**跨配置变更存活**（旋屏时不会被重建，所以不会重新缓冲）。
 *  - 更重要的是「离开播放页就暂停」需要一个**准确的离开信号**。[onCleared] 只在导航条目
 *    真被弹出时触发；旋屏/配置变更会保留 ViewModel，所以不会误触发。
 *    之前试过两条路都不行：`isChangingConfigurations` 会被「旋回竖屏」的配置变更骗到；
 *    「销毁时读当前路由」也不行 —— composable 的销毁发生得比路由状态更新更早，读到的还是自己。
 */
class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    /** 实际要播的队列：选中的文件回退成一个单元素队列，保证永不为空。 */
    private val queue: List<String> = container.playbackSelection.let { selection ->
        selection.queue.ifEmpty { listOfNotNull(selection.path) }
    }

    /**
     * 队列的内容指纹，用来判断「控制器上挂的是不是同一批」——见 [PlayerScreen]。
     * **必须和上面那个 queue 同源**，否则旋屏时会误判成「不是同一批」而重新 set，播放被拽回开头。
     */
    val queueSignature: String = queue.joinToString("\u0000")

    /** 本次进入播放页是否要按 [PlaybackSelection.path] 切换（同队列重选条目时为 true）。 */
    val needsSelectionApply: Boolean
        get() = container.playbackSelection.needsApply

    fun markSelectionApplied() {
        container.playbackSelection.markApplied()
    }

    var controller by mutableStateOf<MediaController?>(null)
        private set

    /** 连不上播放服务（正常情况下不该发生，见 WORKLOG 问题 14 的回退说明）。 */
    var connectFailed by mutableStateOf(false)
        private set

    var state by mutableStateOf<PlayerState>(PlayerState.Loading)
        private set

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    /** path → 备选版本的播放地址（按选源规则排序，失败时依次尝试）。 */
    private val fallbackUrls = mutableMapOf<String, MutableList<String>>()

    /** 取出当前条目下一个备选地址；没有返回 null。401/429 时调用方不得调用。 */
    fun takeFallback(path: String?): String? {
        if (path == null) return null
        return synchronized(fallbackUrls) {
            fallbackUrls[path]?.removeFirstOrNull()
        }
    }

    init {
        connect()
        start()
    }

    private fun connect() {
        val context = container.appContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val result = runCatching { future.get() }
                controller = result.getOrNull()
                connectFailed = result.isFailure
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    override fun onCleared() {
        // 「离开播放页」的定义就在这里：ViewModel 被清掉 = 导航条目被弹出（旋屏不会）。
        // 暂停但**不放掉通知** —— 服务还在，用户能从通知栏接着看。
        controller?.pause()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onCleared()
    }

    private fun start() {
        val selected = container.playbackSelection.path
        if (selected == null || queue.isEmpty()) {
            state = PlayerState.Unavailable("没有选中要播放的文件")
            return
        }

        viewModelScope.launch {
            synchronized(fallbackUrls) { fallbackUrls.clear() }
            // 刷新注册表：DataSource 用的是同一份内存快照
            val allSources = container.sources.refresh().filter { it.enabled && it.credentialOk }
            val byId = allSources.associateBy { it.id }
            val known = byId.keys
            // 本地条目不需要数据源 —— 整队都是本地的就不该因为没配数据源而拒绝播放
            val allLocal = queue.all(LocalMedia::isLocal)
            if (!allLocal && byId.isEmpty()) {
                state = PlayerState.Unavailable("数据源不可用，请重新添加")
                return@launch
            }

            val subtitleUrls: Map<String, List<String>> =
                if (allLocal) emptyMap() else findSubtitles(byId)

            val items = queue.mapNotNull { path ->
                val url = container.playableUrlResolver.resolve(path, byId, known)
                    ?: return@mapNotNull null
                // 同作品/同集在其它源的版本，作为播放失败时的备选（按选源规则排序）
                val alternatives = container.library.versionsOf(path)
                    .map { it.path }
                    .filter { it != path }
                    .mapNotNull { alt -> container.playableUrlResolver.resolve(alt, byId, known) }
                if (alternatives.isNotEmpty()) {
                    synchronized(fallbackUrls) { fallbackUrls[path] = alternatives.toMutableList() }
                }
                Playable(
                    path = path,
                    url = url,
                    subtitleUrls = subtitleUrls[path].orEmpty(),
                )
            }
            if (items.isEmpty()) {
                state = PlayerState.Unavailable("解析不出可播的地址")
                return@launch
            }
            state = PlayerState.Ready(
                items = items,
                startIndex = items.indexOfFirst { it.path == selected }.coerceAtLeast(0),
                startPositionMs = container.progress.resumePositionMs(selected) ?: 0L,
            )
        }
    }

    /**
     * 找同级目录的字幕：**每个数据源一次 PROPFIND**，本地按文件名配对。
     *
     * 为什么在播放时现查、而不是扫描时存进库：
     *  - 从「浏览」Tab 直接播的文件可能压根没进过媒体库，存库那条路够不着；
     *  - 一次用户触发的请求，不是高频轮询；
     *  - 不会留下过期的字幕记录。
     * 拿不到就算了（超时/401/目录没了都不该挡住播放）。SMB/媒体服务器暂不做同级发现。
     */
    private suspend fun findSubtitles(
        sourcesById: Map<String, SourceConfig>,
    ): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        val groups = queue
            .filter { !LocalMedia.isLocal(it) }
            .groupBy { path ->
                SourcePaths.parseKnown(path, sourcesById.keys)?.sourceId?.value
                    ?: SourceId.WEBDAV.value
            }
        for ((sourceId, paths) in groups) {
            val source = sourcesById[sourceId] ?: continue
            if (source.type != SourceType.WEBDAV || !source.credentialOk) continue
            val root = WebDavUrl.rootCollection(source.url, source.rootPath) ?: continue
            val folder = SourcePaths.keyOf(paths.first()).substringBeforeLast('/', "")
            try {
                val siblings = container.webDavClient.propfind(
                    collection = WebDavUrl.child(root, folder, isCollection = true),
                    root = root,
                    basicAuthHeader = WebDavClient.basicAuth(source.username, source.password),
                )
                val subtitles = siblings.filter { !it.isDirectory && MediaTypes.isSubtitleFile(it.name) }
                if (subtitles.isEmpty()) continue
                paths.forEach { videoPath ->
                    val videoName = SourcePaths.keyOf(videoPath).substringAfterLast('/')
                    result[videoPath] = subtitles
                        .filter { MediaTypes.matchesSubtitle(videoName, it.name) }
                        .map { WebDavUrl.child(root, it.path).toString() }
                }
            } catch (e: CancellationException) {
                throw e   // 取消要原样抛出，不得作为业务失败吞掉
            } catch (e: Exception) {
                // 该源取不到字幕不影响播放
            }
        }
        return result
    }

    /** 当前字幕源的展示名（现在只有 OpenSubtitles）。 */
    val subtitleProviderName: String get() = container.subtitles.active.name

    /** 字幕源凭据配好了没 —— 没配就引导去设置页。 */
    suspend fun subtitlesConfigured(): Boolean = container.subtitles.isConfigured()

    suspend fun searchSubtitles(
        query: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        languages: String,
    ): List<SubtitleHit> = container.subtitles.search(query, year, season, episode, languages)

    suspend fun downloadSubtitle(hit: SubtitleHit): LocalSubtitle = container.subtitles.download(hit)

    /** 本地选来的字幕字节，存到应用目录再挂载。 */
    fun saveSubtitle(name: String, bytes: ByteArray): LocalSubtitle = container.subtitles.save(name, bytes)

    // ---- 旋转后仍需保留的界面状态 ----

    var locked by mutableStateOf(false)
        private set

    var infoOpen by mutableStateOf(false)
        private set

    var searchOpen by mutableStateOf(false)
        private set

    var diagnosticsOpen by mutableStateOf(false)
        private set

    var deviceCapabilities by mutableStateOf<com.lovelev.mediadock.core.model.DeviceCapabilities?>(null)
        private set

    val playbackDiagnostics: kotlinx.coroutines.flow.StateFlow<com.lovelev.mediadock.player.diagnostics.PlaybackDiagnostics> =
        container.playbackDiagnostics.state

    val refreshMatching: Boolean
        get() = container.playbackSettings.refreshMatching() ==
            com.lovelev.mediadock.data.prefs.RefreshMatchingMode.AUTO

    val maxAudioChannels: Int
        get() = container.playbackSettings.audioChannelCap().max

    fun updateLocked(value: Boolean) {
        locked = value
    }

    fun updateInfoOpen(value: Boolean) {
        infoOpen = value
    }

    fun updateSearchOpen(value: Boolean) {
        searchOpen = value
    }

    fun updateDiagnosticsOpen(value: Boolean) {
        diagnosticsOpen = value
        if (value && deviceCapabilities == null) {
            viewModelScope.launch {
                deviceCapabilities = container.deviceCapabilities.probe(force = false)
            }
        }
    }

    // ---- 在线字幕搜索：结果与输入留在 ViewModel，旋屏不丢 ----

    var subtitleQuery by mutableStateOf("")
        private set

    var subtitleLanguages by mutableStateOf("zh-cn,en")
        private set

    var subtitleBusy by mutableStateOf(false)
        private set

    var subtitleResults by mutableStateOf<List<SubtitleHit>>(emptyList())
        private set

    var subtitleNotice by mutableStateOf<String?>(null)
        private set

    var subtitlesConfigured by mutableStateOf(true)
        private set

    private var subtitlePath: String? = null

    /** 新文件才重新预填；同一文件旋屏回来保留用户已输入的查询。 */
    fun prepareSubtitleSearch(path: String) {
        if (subtitlePath == path) return
        subtitlePath = path
        val parsed = parseTitle(path)
        subtitleQuery = parsed.title
        subtitleLanguages = "zh-cn,en"
        subtitleResults = emptyList()
        subtitleNotice = null
        subtitleBusy = false
    }

    fun updateSubtitleQuery(value: String) {
        subtitleQuery = value
    }

    fun updateSubtitleLanguages(value: String) {
        subtitleLanguages = value
    }

    fun showSubtitleNotice(text: String?) {
        subtitleNotice = text
    }

    fun updateSubtitleBusy(busy: Boolean) {
        subtitleBusy = busy
    }

    fun loadSubtitlesConfigured() {
        viewModelScope.launch { subtitlesConfigured = container.subtitles.isConfigured() }
    }

    fun searchSubtitlesNow(path: String) {
        val keyword = subtitleQuery.trim()
        if (keyword.isEmpty()) return
        val parsed = parseTitle(path)
        viewModelScope.launch {
            subtitleBusy = true
            subtitleNotice = null
            subtitleResults = emptyList()
            try {
                subtitleResults = searchSubtitles(
                    query = keyword,
                    year = parsed.year,
                    season = parsed.season,
                    episode = parsed.episode,
                    languages = subtitleLanguages,
                )
                if (subtitleResults.isEmpty()) subtitleNotice = "未找到匹配字幕"
                AppLog.d("player", "subtitle search '$keyword' -> ${subtitleResults.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                subtitleNotice = e.message ?: "搜索失败"
            } finally {
                subtitleBusy = false
            }
        }
    }

    private fun parseTitle(path: String) = TitleParser.parse(
        fileName = path.substringAfterLast('/'),
        folderName = path.substringBeforeLast('/', "").substringAfterLast('/'),
        inSeasonDir = TitleParser.seasonOfPath(path) != null,
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlayerViewModel(container) }
        }
    }
}
