package com.lovelev.mediadock.ui.settings

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

    /** 本地媒体目录显示成什么（取最后一段路径，别把整串 content:// 甩给用户）。 */
    var localFolderLabel by mutableStateOf("")
        private set

    var localHint by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            val savedSource = container.credentials.load()
            sourceHost = savedSource?.let { WebDavUrl.parse(it.url)?.host }.orEmpty()
            localFolderLabel = container.credentials.loadLocalTree().orEmpty().let(::friendlyFolderName)
        }
    }

    /**
     * 用户选好目录了：**先把授权持久化**再落盘。
     *
     * 不 take 的话只在本次进程有效，重启后那棵树就打不开了 —— 表现是「扫过一次，下次全没了」。
     */
    fun onLocalFolderPicked(uri: Uri) {
        viewModelScope.launch {
            val taken = runCatching {
                container.appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.isSuccess
            if (!taken) {
                localHint = "拿不到这个文件夹的长期权限，换一个试试"
                return@launch
            }
            container.credentials.saveLocalTree(uri.toString())
            localFolderLabel = friendlyFolderName(uri.toString())
            localHint = "已选中，开始扫描…"
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

    /** 移除本地目录：授权和库里的本地条目一起清掉。WebDAV 那边一根汗毛都不动。 */
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

    /** `content://…/tree/primary%3AMovies` → `Movies`。取不出来就退回「已选择」。 */
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
     * 没有数据源返回 null —— 界面据此提示，别把空文本复制给用户。
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
     * 启动时那次静默检查**失败不吭声**（别一进设置就挂个红字），命中新版本才留个标记。
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
