package com.lovelev.mediadock.ui.browse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.core.model.SourcePaths
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavEntry
import com.lovelev.mediadock.data.webdav.WebDavException
import com.lovelev.mediadock.data.webdav.WebDavUrl
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** 面包屑的一节。[path] 是相对数据源根的累积路径，根为 `""`。 */
data class Crumb(val path: String, val label: String)

sealed interface BrowseState {
    data object Loading : BrowseState

    data class Ready(val entries: List<WebDavEntry>) : BrowseState

    /** 网络 / 路径失效 / 解析失败 —— 给一个重试入口。 */
    data class Failed(val message: String) : BrowseState

    /**
     * 401 / 429。**界面不给重试入口**：目标服务器的登录失败锁定按来源 IP 计数，
     * 反复试错会把所有设备的 WebDAV 一起锁死。恢复路径只有「重新登录」。
     */
    data class Blocked(val message: String) : BrowseState
}

class BrowserViewModel(private val container: AppContainer) : ViewModel() {

    /** 当前目录，相对数据源根（根 = `""`）。 */
    var path by mutableStateOf("")
        private set

    var state by mutableStateOf<BrowseState>(BrowseState.Loading)
        private set

    /** 可浏览的 WebDAV 源；多源时界面顶部可切换。 */
    var sources by mutableStateOf<List<SourceConfig>>(emptyList())
        private set

    var selectedSourceId by mutableStateOf<String?>(null)
        private set

    private var root: HttpUrl? = null
    private var authHeader: String? = null

    /** 源内相对路径 → 跨源唯一路径（播放队列用）。 */
    fun qualify(key: String): String =
        selectedSourceId?.let { SourcePaths.qualify(SourceId(it), key) } ?: key

    /** 收到 401/429 后置位：此后不再自动发任何请求，直到用户重新登录（重建本 VM）。 */
    private var blocked = false

    /** 正在取的目录。同一目录不会并发取第二次 —— 连点/返回键不会变成请求风暴。 */
    private val inFlight = HashSet<String>()

    /**
     * 导航意图序号。结果回来时若已不是最新意图就只入缓存、不改界面 ——
     * 否则慢请求会盖掉用户已经切换到的新目录。
     */
    private var nav = 0L

    /** 目录缓存：首次进入才 PROPFIND，往回走和点面包屑都直接命中。 */
    private val cache = HashMap<String, List<WebDavEntry>>()

    val canGoUp: Boolean get() = path.isNotEmpty()

    val rootLabel: String
        get() = sources.firstOrNull { it.id == selectedSourceId }?.name
            ?: root?.host.orEmpty()

    init {
        bootstrap()
    }

    /** 根 → … → 当前目录。 */
    fun crumbs(): List<Crumb> = buildList {
        add(Crumb("", rootLabel))
        var acc = ""
        for (seg in path.split('/')) {
            if (seg.isEmpty()) continue
            acc = if (acc.isEmpty()) seg else "$acc/$seg"
            add(Crumb(acc, seg))
        }
    }

    /** 进入目录。命中缓存直接切，否则发一次 PROPFIND。 */
    fun open(target: String) {
        if (blocked) return
        if (target == path && state is BrowseState.Ready) return

        val cached = cache[target]
        if (cached != null) {
            nav++ // 界面立刻切走，作废任何在途结果
            path = target
            state = BrowseState.Ready(cached)
            return
        }
        request(target)
    }

    fun goUp() {
        if (!canGoUp) return
        open(path.substringBeforeLast('/', ""))
    }

    /** 显式刷新当前目录：丢缓存重取。 */
    fun refresh() {
        if (blocked) return
        cache.remove(path)
        request(path)
    }

    /** 失败后重试；若连凭据都没读到，则整个重新初始化。 */
    fun retry() {
        if (blocked) return
        if (root == null) bootstrap() else request(path)
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val list = container.sources.refresh().filter {
                it.type == SourceType.WEBDAV && it.enabled && it.credentialOk
            }
            sources = list
            val chosen = list.firstOrNull { it.id == selectedSourceId } ?: list.firstOrNull()
            if (chosen == null) {
                state = BrowseState.Failed("数据源不可用，请重新添加")
                return@launch
            }
            applySource(chosen)
        }
    }

    /** 切换浏览源：重置路径、缓存与 401 封锁状态，从该源根目录重新开始。 */
    fun selectSource(id: String) {
        val chosen = sources.firstOrNull { it.id == id } ?: return
        if (chosen.id == selectedSourceId) return
        applySource(chosen)
    }

    private fun applySource(source: SourceConfig) {
        val base = WebDavUrl.rootCollection(source.url, source.rootPath)
        if (base == null) {
            state = BrowseState.Failed("数据源地址无效，请重新填写")
            return
        }
        selectedSourceId = source.id
        root = base
        authHeader = WebDavClient.basicAuth(source.username, source.password)
        blocked = false
        path = ""
        cache.clear()
        request("")
    }

    private fun request(target: String) {
        val base = root ?: return
        val header = authHeader ?: return
        if (blocked || target in inFlight) return

        nav++
        val token = nav
        inFlight += target
        state = BrowseState.Loading

        viewModelScope.launch {
            try {
                val entries = container.webDavClient
                    .propfind(
                        collection = WebDavUrl.child(base, target, isCollection = true),
                        root = base,
                        basicAuthHeader = header,
                    )
                    // 文件夹优先，再按名字
                    .sortedWith(compareByDescending<WebDavEntry> { it.isDirectory }.thenBy { it.name })

                cache[target] = entries
                if (token == nav) {
                    path = target
                    state = BrowseState.Ready(entries)
                }
            } catch (e: WebDavException) {
                // 单次尝试即返回，绝不重试
                when (e) {
                    is WebDavException.Unauthorized, is WebDavException.Locked -> {
                        // 认证坏了就是坏了，不看序号：立刻停手，交给用户重新登录
                        blocked = true
                        state = BrowseState.Blocked(e.userMessage)
                    }

                    else -> if (token == nav) state = BrowseState.Failed(e.userMessage)
                }
            } finally {
                inFlight -= target
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { BrowserViewModel(container) }
        }
    }
}
