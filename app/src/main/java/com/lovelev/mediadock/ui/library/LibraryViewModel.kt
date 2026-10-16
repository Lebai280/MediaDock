package com.lovelev.mediadock.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.repo.DetailFillState
import com.lovelev.mediadock.data.repo.ScanState
import com.lovelev.mediadock.data.repo.ScrapeState
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    /** 扫描/刮削状态来自应用作用域的仓库，所以退出本页/旋屏都不会把它们打断。 */
    val scan: StateFlow<ScanState> = container.library.scan
    val scrape: StateFlow<ScrapeState> = container.library.scrape
    val fill: StateFlow<DetailFillState> = container.details.fill

    /** 库里全部条目（已合并手工改名）。 */
    private val allItems: StateFlow<List<MediaItemEntity>> = container.library.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 已缓存的详情。搜索要按 类型/国家/演员 过滤，靠的就是它 ——
     * 而详情是「打开详情页才拉」的懒加载，所以**不一定齐**（用「补齐影片信息」补齐）。
     */
    private val allDetails: StateFlow<List<TitleDetail>> = container.library.details
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 搜索词：Compose 状态给输入框显示，Flow 参与 combine。 */
    var query by mutableStateOf("")
        private set
    private val queryFlow = MutableStateFlow("")

    /**
     * 改搜索词。方法名不能叫 `setQuery` —— 会和 `var query` 合成的 setter 撞 JVM 签名
     * （Kotlin 报 Platform declaration clash，见 WORKLOG 坑 6）。
     */
    fun editQuery(value: String) {
        query = value
        queryFlow.value = value
    }

    /** 搜索要显示的那一批。规则见 [filterLibraryItems]（纯逻辑，有单测）。 */
    val visible: StateFlow<List<MediaItemEntity>> =
        combine(allItems, allDetails, queryFlow) { items, details, q ->
            filterLibraryItems(items, details, q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 海报块上那条进度条：path → 0..1。 */
    val progress: StateFlow<Map<String, Float>> = container.progress.observeAll()
        .map { rows ->
            rows.filter { it.durationMs > 0L }
                .associate { it.path to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 首页那条「随机推荐」。进首页时换一批，也可以手动换。 */
    var recommended by mutableStateOf<List<WallEntry>>(emptyList())
        private set

    /** 首页的分类行（数据驱动，规则见 [genreRows]）。详情没补齐就出不来，这是预期的。 */
    val genreSections: StateFlow<List<GenreRow>> =
        combine(allItems, allDetails) { items, details -> genreRows(items, details) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 收藏的 path 集合 —— 推荐理由要用（「你收藏过的」「你收藏的《X》也是动画」）。 */
    private val favoritePaths: StateFlow<Set<String>> = container.favorites.observePaths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * path → 推荐理由（首页大卡片下面那行）。规则与优先级见 [recommendReason]。
     * 推不出理由的条目**不在这个表里**，界面那一行就不画（不编话凑数）。
     */
    val recommendReasons: StateFlow<Map<String, String>> =
        combine(allItems, allDetails, progress, favoritePaths) { items, details, prog, favorites ->
            val byPath = details.associateBy { it.path }
            val itemsByPath = items.associateBy { it.path }
            fun genresOf(path: String): List<String> = byPath[path]?.genres
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
                .orEmpty()

            // 收藏里出现过的类型 → 一部收藏的片名（用来做「你收藏的《X》也是动画」那条）
            val favoriteGenres = buildMap {
                favorites.forEach { path ->
                    val title = itemsByPath[path]?.parsedTitle?.takeIf { it.isNotBlank() } ?: return@forEach
                    genresOf(path).forEach { genre -> putIfAbsent(genre, title) }
                }
            }

            items
                .associate { item ->
                    item.path to recommendReason(
                        genres = genresOf(item.path),
                        rating = item.rating,
                        progress = prog[item.path],
                        isFavorite = item.path in favorites,
                        favoriteGenres = favoriteGenres,
                    )
                }
                .filterValues { it.isNotBlank() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun reshuffle() {
        recommended = pickRandom(groupWall(allItems.value))
    }

    fun toggleScan() {
        // 取消只取消扫描本身，别顺带把刮削的进度提示也抹掉
        if (scan.value is ScanState.Running) container.library.cancelScan() else container.library.start()
    }

    fun toggleScrape() {
        if (scrape.value is ScrapeState.Running) container.library.cancelScrape() else container.library.scrapeStart()
    }

    /** 补齐影片信息：跑着就取消，否则把已刮削条目缺的详情一次拉齐。 */
    fun toggleFillDetails() {
        if (fill.value is DetailFillState.Running) {
            container.details.cancelFill()
        } else {
            container.details.fillMissing(allItems.value)
        }
    }

    init {
        // 库第一次到位时先铺一批随机推荐；之后靠 reshuffle() 换
        viewModelScope.launch {
            allItems.collect { if (recommended.isEmpty() && it.isNotEmpty()) reshuffle() }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container) }
        }
    }
}

/**
 * 随机挑一批（最多 [RECOMMEND_COUNT] 块）。
 */
private fun pickRandom(wall: List<WallEntry>): List<WallEntry> =
    if (wall.size <= RECOMMEND_COUNT) wall.shuffled() else wall.shuffled().take(RECOMMEND_COUNT)

private const val RECOMMEND_COUNT = 5
