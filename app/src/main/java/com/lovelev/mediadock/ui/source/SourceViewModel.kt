package com.lovelev.mediadock.ui.source

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.subtitle.OpenSubtitlesCredentials
import com.lovelev.mediadock.data.webdav.ShareConfig
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavEntry
import com.lovelev.mediadock.data.webdav.WebDavException
import com.lovelev.mediadock.data.webdav.WebDavUrl
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Success(val count: Int, val preview: List<String>) : TestState
    data class Failure(val message: String, val retryBlocked: Boolean) : TestState
}

class SourceViewModel(private val container: AppContainer) : ViewModel() {

    /** 编辑已有源时非空；新建时为空。 */
    var editingId by mutableStateOf<String?>(null)
        private set

    /** 数据源类型；WebDAV / SMB / Emby / Jellyfin。 */
    var type by mutableStateOf(SourceType.WEBDAV)
        private set

    var name by mutableStateOf("")
        private set
    var url by mutableStateOf("")
        private set
    var share by mutableStateOf("")
        private set
    var apiKey by mutableStateOf("")
        private set
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var rootPath by mutableStateOf("")
        private set

    var state by mutableStateOf<TestState>(TestState.Idle)
        private set

    /**
     * 认证失败后封锁重试，直到用户改动输入为止。
     * 目的：防止无意识的连点把服务器账户触发锁定（锁定按来源 IP 计数，会连累其他设备）。
     */
    private var retryBlocked = false

    /**
     * 从剪贴板里认出「分享配置」之后的提示（给界面显示一行）。
     * 分享给朋友的场景：那边复制过来，这边自动填好，不用手输。
     */
    var sharedHint by mutableStateOf<String?>(null)
        private set

    private var sharedApplied = false

    /**
     * 进页面时自动应用剪贴板里的分享配置。**每个 ViewModel 只应用一次**：
     * 旋屏重建时 AddSourceScreen 会再次读到剪贴板，不能覆盖用户已经改过的输入。
     * 手动「粘贴分享配置」仍走 [pasteShared]，可以重复应用。
     */
    fun applySharedOnce(text: String?) {
        if (sharedApplied) return
        sharedApplied = true
        applyShared(text)
    }

    /**
     * 把一段文本当分享配置填进来。**认不出来就什么都不动**，返回 false。
     * 认出来了返回 true（界面据此提示「已填入」）。
     */
    fun applyShared(text: String?): Boolean {
        val source = ShareConfig.parse(text.orEmpty()) ?: return false
        url = source.url
        username = source.username
        password = source.password
        rootPath = source.rootPath
        // 填进来等于用户改了输入：解除 401 封锁、清掉旧的成败结论
        retryBlocked = false
        state = TestState.Idle
        // TMDB 是全局设置，分享文本里带了就一并存上。
        // 只在上面 parse 认得这段文本时才取 —— 否则剪贴板里任意包含「tmdb」字样的文本
        // 就会把用户的密钥覆盖掉。
        val tmdb = ShareConfig.parseTmdb(text.orEmpty())
        if (tmdb != null) viewModelScope.launch { container.credentials.saveTmdbSecret(tmdb) }
        // 字幕源凭据同样只在认得出这段文本时才写；缺的字段沿用本机已有的，避免把账号密码冲掉
        val subtitle = ShareConfig.parseSubtitle(text.orEmpty())
        if (subtitle != null) {
            viewModelScope.launch {
                val current = container.credentials.loadSubtitleCredentials()
                container.credentials.saveSubtitleCredentials(
                    OpenSubtitlesCredentials(
                        apiKey = subtitle.apiKey.ifBlank { current?.apiKey.orEmpty() },
                        username = subtitle.username.ifBlank { current?.username.orEmpty() },
                        password = subtitle.password.ifBlank { current?.password.orEmpty() },
                    ),
                )
            }
        }
        val extras = buildList {
            if (tmdb != null) add("TMDB 密钥")
            if (subtitle != null) add("字幕源")
        }
        sharedHint = "已填入分享配置 · ${source.url}" +
            if (extras.isNotEmpty()) "（含 ${extras.joinToString(" / ")}）" else ""
        return true
    }

    /** 手动点「粘贴分享配置」：认不出来要说一声，避免用户认为点击无响应。 */
    fun pasteShared(text: String?) {
        if (!applyShared(text)) sharedHint = "剪贴板里没识别到分享配置"
    }

    fun selectType(value: SourceType) {
        if (value == type) return
        type = value
        state = TestState.Idle
        retryBlocked = false
    }

    fun editName(v: String) { name = v; onEdited() }
    fun editUrl(v: String) { url = v; onEdited() }
    fun editShare(v: String) { share = v; onEdited() }
    fun editApiKey(v: String) { apiKey = v; onEdited() }
    fun editUsername(v: String) { username = v; onEdited() }
    fun editPassword(v: String) { password = v; onEdited() }
    fun editRootPath(v: String) { rootPath = v; onEdited() }

    private fun onEdited() {
        if (retryBlocked) {
            retryBlocked = false
            state = TestState.Idle
        } else if (state is TestState.Success) {
            // 改动输入后旧的"成功"结论不再作数
            state = TestState.Idle
        }
    }

    /** 编辑已有源：载入其配置（含解密后的凭据）。 */
    fun loadForEdit(id: String) {
        viewModelScope.launch {
            val source = container.sources.get(id) ?: return@launch
            editingId = source.id
            type = source.type
            name = source.name
            url = source.url
            username = source.username
            password = source.password
            rootPath = source.rootPath
            share = source.share
            apiKey = source.apiKey.orEmpty()
            state = TestState.Idle
        }
    }

    fun test() {
        if (state is TestState.Running || retryBlocked) return
        when (type) {
            SourceType.WEBDAV -> testWebDav()
            SourceType.SMB -> testSmb()
            SourceType.EMBY, SourceType.JELLYFIN -> testServer()
            SourceType.LOCAL -> Unit
        }
    }

    private fun testWebDav() {
        val root = WebDavUrl.rootCollection(url, rootPath)
        if (root == null) {
            state = TestState.Failure("地址格式不正确", retryBlocked = false)
            return
        }
        if (username.isBlank()) {
            state = TestState.Failure("请填写用户名", retryBlocked = false)
            return
        }

        state = TestState.Running
        viewModelScope.launch {
            try {
                val entries = container.webDavClient.propfind(
                    collection = root,
                    root = root,
                    basicAuthHeader = WebDavClient.basicAuth(username, password),
                )
                state = TestState.Success(entries.size, preview(entries))
                persist(sourceNameFallback = WebDavUrl.parse(url)?.host ?: "WebDAV")
            } catch (e: WebDavException) {
                // 单次尝试即返回，绝不在此处重试
                val block = e is WebDavException.Unauthorized || e is WebDavException.Locked
                retryBlocked = block
                state = TestState.Failure(e.userMessage, retryBlocked = block)
            }
        }
    }

    private fun testSmb() {
        if (url.isBlank()) {
            state = TestState.Failure("请填写主机地址", retryBlocked = false)
            return
        }
        if (share.isBlank()) {
            state = TestState.Failure("请填写共享名", retryBlocked = false)
            return
        }
        state = TestState.Running
        viewModelScope.launch {
            try {
                val entries = com.lovelev.mediadock.data.smb.SmbTester.listRoot(
                    hostRaw = url,
                    shareName = share,
                    username = username,
                    password = password,
                )
                state = TestState.Success(entries.size, entries.take(6))
                persist(sourceNameFallback = com.lovelev.mediadock.data.smb.SmbUrl.hostOf(url) ?: "SMB")
            } catch (e: Exception) {
                val authError = e.message?.contains("密码") == true
                retryBlocked = authError
                state = TestState.Failure(e.message ?: "连接失败", retryBlocked = authError)
            }
        }
    }

    private fun testServer() {
        if (url.isBlank()) {
            state = TestState.Failure("请填写服务器地址", retryBlocked = false)
            return
        }
        if (apiKey.isBlank() && username.isBlank()) {
            state = TestState.Failure("请填写账号密码或 API Key", retryBlocked = false)
            return
        }
        state = TestState.Running
        viewModelScope.launch {
            try {
                val auth = if (apiKey.isBlank()) {
                    container.embyClient.authenticate(url, username, password)
                } else {
                    null
                }
                val items = container.embyClient.items(
                    baseUrl = url,
                    auth = auth,
                    apiKey = apiKey.trim().ifBlank { null },
                    startIndex = 0,
                    limit = 6,
                )
                state = TestState.Success(items.size, items.map { it.name }.take(6))
                // 登录成功后把 AccessToken 作为后续同步/播放的凭据保存（同样加密）
                persist(
                    sourceNameFallback = WebDavUrl.parse(url)?.host ?: "媒体服务器",
                    apiKeyOverride = auth?.token,
                )
            } catch (e: com.lovelev.mediadock.data.emby.EmbyClient.Failure.Auth) {
                retryBlocked = true
                state = TestState.Failure(e.message ?: "认证失败", retryBlocked = true)
            } catch (e: Exception) {
                state = TestState.Failure(e.message ?: "连接失败", retryBlocked = false)
            }
        }
    }

    private suspend fun persist(sourceNameFallback: String, apiKeyOverride: String? = null) {
        val id = editingId ?: generateId(type)
        container.sources.save(
            SourceConfig(
                id = id,
                type = type,
                name = name.trim().ifBlank { sourceNameFallback },
                url = url.trim(),
                username = username,
                password = password,
                rootPath = rootPath.trim(),
                share = share.trim(),
                apiKey = (apiKeyOverride ?: apiKey).trim().ifBlank { null },
            ),
        )
    }

    private suspend fun generateId(type: SourceType): String {
        val known = container.sources.refresh().map { it.id }.toSet()
        if (type == SourceType.WEBDAV && "webdav" !in known) return "webdav"
        val prefix = when (type) {
            SourceType.WEBDAV -> "webdav"
            SourceType.SMB -> "smb"
            SourceType.EMBY -> "emby"
            SourceType.JELLYFIN -> "jellyfin"
            SourceType.LOCAL -> "local"
        }
        return "$prefix-" + UUID.randomUUID().toString().replace("-", "").take(8)
    }

    private fun preview(entries: List<WebDavEntry>): List<String> =
        entries
            .sortedWith(compareByDescending<WebDavEntry> { it.isDirectory }.thenBy { it.name })
            .take(6)
            .map { if (it.isDirectory) "${it.name}/" else it.name }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SourceViewModel(container) }
        }
    }
}
