package com.lovelev.mediadock.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lovelev.mediadock.core.data.WorkCard
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.repo.DetailFillState
import com.lovelev.mediadock.data.repo.ScanState
import com.lovelev.mediadock.data.repo.ScrapeState
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    /** 扫描/刮削状态来自应用作用域的仓库，所以退出本页/旋屏都不会把它们打断。 */
    val scan: StateFlow<ScanState> = container.library.scan
    val scrape: StateFlow<ScrapeState> = container.library.scrape
    val fill: StateFlow<DetailFillState> = container.details.fill

    /** 海报墙：works 分页，不再把全库条目读进内存分组。 */
    val wall: Flow<PagingData<WorkCard>> =
        container.libraryQuery.worksPager().cachedIn(viewModelScope)

    /**
     * 搜索页仍在用全量条目（E1.4 会改成 FTS 分页）；
     * 首页不再收集它们，只有「补齐影片信息」按需读取。
     */
    private val allItems: StateFlow<List<MediaItemEntity>> = container.library.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val allDetails: StateFlow<List<TitleDetail>> = container.library.details
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allLibraryItems: StateFlow<List<MediaItemEntity>> get() = allItems
    val allLibraryDetails: StateFlow<List<TitleDetail>> get() = allDetails

    /** 搜索词（搜索页用）。 */
    var query by mutableStateOf("")
        private set
    private val queryFlow = MutableStateFlow("")

    fun editQuery(value: String) {
        query = value
        queryFlow.value = value
    }

    val visible: StateFlow<List<MediaItemEntity>> =
        combine(allItems, allDetails, queryFlow) { items, details, q ->
            filterLibraryItems(items, details, q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 首页随机推荐（有界查询）。 */
    var recommended by mutableStateOf<List<WorkCard>>(emptyList())
        private set

    var recommendReasons by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    private val _genreSections = MutableStateFlow<List<GenreCardRow>>(emptyList())
    val genreSections: StateFlow<List<GenreCardRow>> = _genreSections.asStateFlow()

    init {
        viewModelScope.launch { refreshHeader() }
    }

    /** 换一批推荐。 */
    fun reshuffle() {
        viewModelScope.launch { refreshRecommendations() }
    }

    /**
     * 重新取推荐与分类行（扫描/刮削完成后调用）。
     * 两者都只查有界样本，不会把 20k 库读进内存。
     */
    fun refreshHeader() {
        viewModelScope.launch {
            _genreSections.value = genreCardRows(
                container.libraryQuery.recentWorks(limit = 200),
            )
            refreshRecommendations()
        }
    }

    private suspend fun refreshRecommendations() {
        val cards = container.libraryQuery.randomWorks(limit = 5)
        val favorites = container.libraryQuery.favoriteWorks(limit = 20)
        val favoriteGenres = buildMap {
            favorites.forEach { card ->
                card.genres.forEach { genre -> putIfAbsent(genre, card.title) }
            }
        }
        recommended = cards
        recommendReasons = cards.associate { card ->
            card.id to recommendReason(
                genres = card.genres,
                rating = card.rating,
                progress = card.progressFraction.takeIf { it > 0f },
                isFavorite = card.isFavorite,
                favoriteGenres = favoriteGenres,
            )
        }.filterValues { it.isNotBlank() }
    }

    fun toggleScan() {
        if (scan.value is ScanState.Running) container.library.cancelScan() else container.library.start()
    }

    fun toggleScrape() {
        if (scrape.value is ScrapeState.Running) {
            container.library.cancelScrape()
        } else {
            container.library.scrapeStart()
        }
    }

    /** 补齐影片信息：跑着就取消，否则把已刮削条目缺的详情一次拉齐。 */
    fun toggleFillDetails() {
        if (fill.value is DetailFillState.Running) {
            container.details.cancelFill()
        } else {
            viewModelScope.launch {
                container.details.fillMissing(container.library.items.first())
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(container) }
        }
    }
}
