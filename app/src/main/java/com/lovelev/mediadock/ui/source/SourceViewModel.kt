package com.lovelev.mediadock.ui.source

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.prefs.StoredSource
import com.lovelev.mediadock.data.webdav.ShareConfig
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavEntry
import com.lovelev.mediadock.data.webdav.WebDavException
import com.lovelev.mediadock.data.webdav.WebDavUrl
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.launch

sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Success(val count: Int, val preview: List<String>) : TestState
    data class Failure(val message: String, val retryBlocked: Boolean) : TestState
}

class SourceViewModel(private val container: AppContainer) : ViewModel() {

    var url by mutableStateOf("")
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
        sharedHint = "已填入分享配置 · ${source.url}"
        return true
    }

    /** 手动点「粘贴分享配置」：认不出来要说一声，别让人以为点了没反应。 */
    fun pasteShared(text: String?) {
        if (!applyShared(text)) sharedHint = "剪贴板里没识别到分享配置"
    }

    fun editUrl(v: String) { url = v; onEdited() }
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

    fun test() {
        if (state is TestState.Running || retryBlocked) return

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
                // 只有真正连通了才保存凭据
                container.credentials.save(
                    StoredSource(url = url.trim(), username = username, password = password, rootPath = rootPath.trim()),
                )
            } catch (e: WebDavException) {
                // 单次尝试即返回，绝不在此处重试
                val block = e is WebDavException.Unauthorized || e is WebDavException.Locked
                retryBlocked = block
                state = TestState.Failure(e.userMessage, retryBlocked = block)
            }
        }
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
