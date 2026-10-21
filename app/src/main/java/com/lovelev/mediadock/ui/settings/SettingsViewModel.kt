package com.lovelev.mediadock.ui.settings

import androidx.core.content.ContextCompat
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.ui.theme.ThemeMode
import com.lovelev.mediadock.data.prefs.AudioChannelCap
import com.lovelev.mediadock.data.prefs.RefreshMatchingMode
import com.lovelev.mediadock.data.prefs.SubtitleSize
import com.lovelev.mediadock.data.repo.ScanState
import com.lovelev.mediadock.BuildConfig
import com.lovelev.mediadock.data.subtitle.OpenSubtitlesCredentials
import com.lovelev.mediadock.data.update.UpdateManifest
import com.lovelev.mediadock.data.update.UpdateResult
import com.lovelev.mediadock.data.webdav.ShareConfig
import com.lovelev.mediadock.data.webdav.WebDavUrl
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 版本检查在界面上的状态。 */
sealed interface UpdateUiState {
    /** 没查过，或启动时那次静默检查失败（静默失败不打扰用户）。 */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    data class Available(val manifest: UpdateManifest) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    var tmdbSecret by mutableStateOf("")
        private set

    var saved by mutableStateOf(false)
        private set

    /** 主题三态（跟随系统 / 浅色 / 深色）。 */
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    // 不能叫 setThemeMode —— 会和 `var themeMode` 自动生成的 setter 撞名（WORKLOG 问题 6）
    fun pickThemeMode(mode: ThemeMode) {
        themeMode = mode
        viewModelScope.launch { container.credentials.saveThemeMode(mode.stored) }
    }

    var refreshMatchingMode by mutableStateOf(container.playbackSettings.refreshMatching())
        private set

    var audioChannelCap by mutableStateOf(container.playbackSettings.audioChannelCap())
        private set

    fun pickRefreshMatching(mode: RefreshMatchingMode) {
        refreshMatchingMode = mode
        container.playbackSettings.setRefreshMatching(mode)
    }

    fun pickAudioChannelCap(cap: AudioChannelCap) {
        audioChannelCap = cap
        container.playbackSettings.setAudioChannelCap(cap)
    }

    var subtitleSize by mutableStateOf(container.playbackSettings.subtitleSize())
        private set

    fun pickSubtitleSize(size: SubtitleSize) {
        subtitleSize = size
        container.playbackSettings.setSubtitleSize(size)
    }

    /** 数据源那一行显示的主机名（只显示 host，不显示账号密码）。 */
    var sourceHost by mutableStateOf("")
        private set

    /** 字幕源（OpenSubtitles）凭据：Api-Key 搜、账号密码下载。 */
    var subtitleKey by mutableStateOf("")
        private set
    var subtitleUser by mutableStateOf("")
        private set
    var subtitlePass by mutableStateOf("")
        private set
    var subtitleSaved by mutableStateOf(false)
        private set

    /**
     * 影片数。这个 flow **必须 Eagerly** —— 用的是 `WhileSubscribed` 时没人订阅就永远不开始收集，
     * 而这里只读它的 `.value`（不是 collectAsState），实测就显示成 0。
     */
    val itemCount: StateFlow<Int> = container.library.items
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 数据源数量。和影片数一样用 Eagerly，设置页打开时必须有值。 */
    val sourceCount: StateFlow<Int> = container.sources.sources
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** 本地媒体目录显示成什么（取最后一段路径，不得将完整 content:// 字符串直接展示给用户）。 */
    var localFolderLabel by mutableStateOf("")
        private set

    var localHint by mutableStateOf<String?>(null)
        private set

    /** 二次确认框。放在 ViewModel 里，旋屏后仍保留。 */
    var confirming by mutableStateOf<Confirmable?>(null)
        private set

    fun requestConfirm(action: Confirmable) {
        confirming = action
    }

    fun dismissConfirm() {
        confirming = null
    }

    init {
        viewModelScope.launch {
            val savedSource = container.credentials.load()
            sourceHost = savedSource?.let { WebDavUrl.parse(it.url)?.host }.orEmpty()
            localFolderLabel = container.credentials.loadLocalTree().orEmpty().let(::friendlyFolderName)
        }
        // 提示由**扫描状态**驱动，不得固定为「正在…」；
        // 此前实现中扫描完成后提示未更新，表现为卡住。
        // 主题由存储状态驱动，避免仅在本地保存副本。
        viewModelScope.launch {
            container.credentials.themeMode.collect { themeMode = ThemeMode.fromStored(it) }
        }
        viewModelScope.launch {
            container.library.scan.collect { state ->
                localHint = when (state) {
                    is ScanState.Running ->
                        if (state.files > 0) "正在扫描…已找到 ${state.files} 个" else "正在扫描…"

                    is ScanState.Done ->
                        "已扫到 ${state.files} 个视频" +
                            if (state.truncated) "（达到上限，可能不完整）" else ""

                    is ScanState.Failed -> state.message
                    // Idle：保持提示，避免清除刚完成的扫描结果
                    ScanState.Idle -> localHint
                }
            }
        }
    }

    /**
     * 选择目录后，**先持久化授权**再保存配置。
     *
     * 未持久化时授权仅在当前进程有效，重启后无法访问该目录。
     */
    /**
     * 扫描系统媒体库。**这是本地媒体的推荐入口**：仅需一次权限授权，
     * 整库通过一次查询返回；SAF 逐目录遍历较慢。
     *
     * 权限由界面层申请后再调用此处（权限申请需要 Activity 结果，只能在 composable 中发起）。
     */
    fun scanMediaStore() {
        viewModelScope.launch {
            localHint = "正在读系统媒体库…"
            container.library.startMediaStore()
        }
    }

    /** 扫媒体库要的权限：13+ 是 `READ_MEDIA_VIDEO`，再往下只有 `READ_EXTERNAL_STORAGE`。 */
    fun mediaPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasMediaPermission(): Boolean =
        ContextCompat.checkSelfPermission(container.appContext, mediaPermission()) ==
            PackageManager.PERMISSION_GRANTED

    fun noteMediaPermissionDenied() {
        localHint = "未授予视频权限，可使用「指定文件夹」。"
    }

    fun clearLocalHint() {
        localHint = null
    }

    fun onLocalFolderPicked(uri: Uri) {
        viewModelScope.launch {
            val taken = runCatching {
                container.appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (!taken) {
                localHint = "无法获取该文件夹的持久权限，请选择其他文件夹。"
                return@launch
            }
            container.credentials.saveLocalTree(uri.toString())
            localFolderLabel = friendlyFolderName(uri.toString())
            localHint = "已选择，正在扫描…"
            container.library.startLocal(uri)
        }
    }

    fun scanLocal() {
        viewModelScope.launch {
            val uri = container.credentials.loadLocalTree() ?: return@launch
            localHint = "重新扫描中…"
            container.library.startLocal(Uri.parse(uri))
        }
    }

    /** 移除本地目录：同时清除授权与库中的本地条目，不影响 WebDAV 数据。 */
    fun removeLocal() {
        viewModelScope.launch {
            val tree = container.credentials.loadLocalTree()
            if (tree != null) {
                runCatching {
                    container.appContext.contentResolver.releasePersistableUriPermission(
                        Uri.parse(tree),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            container.credentials.clearLocalTree()
            container.library.clearLocalItems()
            localFolderLabel = ""
            localHint = "已移除本地文件夹"
        }
    }

    /** `content://…/tree/primary%3AMovies` → `Movies`。无法提取时回退为「已选择」。 */
    private fun friendlyFolderName(treeUri: String): String {
        if (treeUri.isBlank()) return ""
        return runCatching {
            DocumentsContract.getTreeDocumentId(Uri.parse(treeUri))
                .substringAfterLast(':', missingDelimiterValue = "")
                .substringAfterLast('/')
                .takeIf { it.isNotBlank() }
        }.getOrNull() ?: "已选择"
    }

    fun load() {
        if (tmdbSecret.isNotEmpty()) return
        viewModelScope.launch {
            container.credentials.loadTmdbSecret()?.let { tmdbSecret = it }
        }
    }

    fun editTmdbSecret(v: String) {
        tmdbSecret = v
        saved = false
    }

    fun save() {
        if (tmdbSecret.isBlank()) return
        viewModelScope.launch {
            container.credentials.saveTmdbSecret(tmdbSecret)
            // 凭据换过了：把「没凭据」造成的封锁解开，否则回去还是刮不了
            container.library.onSecretChanged()
            saved = true
        }
    }

    fun clearLibrary() {
        viewModelScope.launch { container.library.clearLibrary() }
    }

    /**
     * 生成「分享给朋友」的文本（读一次已保存的数据源）。
     * 没有数据源返回 null —— 界面据此提示，不得复制空文本。
     */
    suspend fun buildShareText(): String? = container.credentials.load()?.let {
        ShareConfig.build(
            url = it.url,
            username = it.username,
            password = it.password,
            rootPath = it.rootPath,
            // TMDB 是全局设置、不随数据源走，但一起发过去朋友装上就能刮削，省得再问一遍
            tmdbSecret = container.credentials.loadTmdbSecret(),
        )
    }

    fun loadSubtitles() {
        if (subtitleKey.isNotEmpty()) return
        viewModelScope.launch {
            container.credentials.loadSubtitleCredentials()?.let {
                subtitleKey = it.apiKey
                subtitleUser = it.username
                subtitlePass = it.password
            }
        }
    }

    fun editSubtitleKey(v: String) { subtitleKey = v; subtitleSaved = false }
    fun editSubtitleUser(v: String) { subtitleUser = v; subtitleSaved = false }
    fun editSubtitlePass(v: String) { subtitlePass = v; subtitleSaved = false }

    fun saveSubtitles() {
        if (subtitleKey.isBlank() || subtitleUser.isBlank() || subtitlePass.isBlank()) return
        viewModelScope.launch {
            container.credentials.saveSubtitleCredentials(
                OpenSubtitlesCredentials(
                    apiKey = subtitleKey.trim(),
                    username = subtitleUser.trim(),
                    password = subtitlePass,
                ),
            )
            subtitleSaved = true
        }
    }

    /** 退出登录：只清数据源凭据（TMDB key 保留），顺带解开 401/429 的封锁。 */
    fun logout() {
        viewModelScope.launch {
            container.credentials.clearSource()
            container.library.reset()
        }
    }

    var update by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        private set

    /**
     * 查在线更新。[manual] = 用户在设置里点的 —— 那次要把「检查中 / 连不上」都说出来；
     * 启动时那次静默检查**失败不吭声**（避免进入设置页即显示错误提示），命中新版本才留个标记。
     */
    fun checkUpdate(manual: Boolean) {
        if (update is UpdateUiState.Checking) return
        viewModelScope.launch {
            if (manual) update = UpdateUiState.Checking
            update = when (val result = container.updateChecker.check(BuildConfig.VERSION_CODE)) {
                is UpdateResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateResult.Available -> UpdateUiState.Available(result.manifest)
                is UpdateResult.Failed ->
                    if (manual) UpdateUiState.Failed(result.message) else UpdateUiState.Idle
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
