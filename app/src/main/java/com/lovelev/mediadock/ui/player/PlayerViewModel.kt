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
import com.lovelev.mediadock.data.MediaTypes
import com.lovelev.mediadock.data.prefs.StoredSource
import com.lovelev.mediadock.data.repo.LocalSubtitle
import com.lovelev.mediadock.data.subtitle.SubtitleHit
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavUrl
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.player.PlaybackService
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl

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

    /** 实际要播的队列：选中的文件兜底成一个单元素队列，保证永不为空。 */
    private val queue: List<String> = container.playbackSelection.let { selection ->
        selection.queue.ifEmpty { listOfNotNull(selection.path) }
    }

    /**
     * 队列的内容指纹，用来判断「控制器上挂的是不是同一批」——见 [PlayerScreen]。
     * **必须和上面那个 queue 同源**，否则旋屏时会误判成「不是同一批」而重新 set，播放被拽回开头。
     */
    val queueSignature: String = queue.joinToString("\u0000")

    var controller by mutableStateOf<MediaController?>(null)
        private set

    /** 连不上播放服务（正常情况下不该发生，见 WORKLOG 坑 14 的兜底说明）。 */
    var connectFailed by mutableStateOf(false)
        private set

    var state by mutableStateOf<PlayerState>(PlayerState.Loading)
        private set

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

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
            // 这一步顺带把凭据刷进 credentials.cached，媒体请求用它补 Authorization 头
            val saved = container.credentials.load()
            val root = saved?.let { WebDavUrl.rootCollection(it.url, it.rootPath) }
            if (saved == null || root == null) {
                state = PlayerState.Unavailable("数据源不可用，请重新添加")
                return@launch
            }

            val subtitles = findSubtitles(root, saved)
            state = PlayerState.Ready(
                items = queue.map {
                    Playable(
                        path = it,
                        url = WebDavUrl.child(root, it).toString(),
                        subtitleUrls = subtitles[it].orEmpty()
                            .map { sub -> WebDavUrl.child(root, sub).toString() },
                    )
                },
                startIndex = queue.indexOf(selected).coerceAtLeast(0),
                startPositionMs = container.progress.resumePositionMs(selected) ?: 0L,
            )
        }
    }

    /**
     * 找同级目录的字幕：**一次 PROPFIND** 拿整个目录，本地按文件名配对。
     *
     * 为什么在播放时现查、而不是扫描时存进库：
     *  - 从「浏览」Tab 直接播的文件可能压根没进过媒体库，存库那条路够不着；
     *  - 一次用户触发的请求，不是高频轮询；
     *  - 不会留下过期的字幕记录。
     * 拿不到就算了（超时/401/目录没了都不该挡住播放）。
     */
    private suspend fun findSubtitles(
        root: HttpUrl,
        saved: StoredSource,
    ): Map<String, List<String>> {
        val folder = queue.first().substringBeforeLast('/', "")
        return try {
            val siblings = container.webDavClient.propfind(
                collection = WebDavUrl.child(root, folder, isCollection = true),
                root = root,
                basicAuthHeader = WebDavClient.basicAuth(saved.username, saved.password),
            )
            val subtitles = siblings.filter { !it.isDirectory && MediaTypes.isSubtitleFile(it.name) }
            if (subtitles.isEmpty()) {
                emptyMap()
            } else {
                queue.associateWith { videoPath ->
                    val videoName = videoPath.substringAfterLast('/')
                    subtitles.filter { MediaTypes.matchesSubtitle(videoName, it.name) }.map { it.path }
                }
            }
        } catch (e: CancellationException) {
            throw e   // 取消要原样抛出，别当成业务失败吞掉
        } catch (e: Exception) {
            emptyMap()
        }
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

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlayerViewModel(container) }
        }
    }
}
