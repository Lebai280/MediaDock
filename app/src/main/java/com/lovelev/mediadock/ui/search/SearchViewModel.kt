package com.lovelev.mediadock.ui.search

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
import com.lovelev.mediadock.core.data.WorksFilter
import com.lovelev.mediadock.core.data.WorksSort
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val type: String = "全部",
    val sort: String = "最新更新",
    val genre: String = "全部",
    val region: String = "全部",
    val year: String = "全部",
)

data class SearchOptions(
    val genres: List<String> = listOf("全部"),
    val regions: List<String> = listOf("全部"),
    val years: List<String> = listOf("全部"),
)

/**
 * 搜索页：FTS4 + SQL 过滤/排序 + Paging 3。
 * 输入防抖 250ms；筛选条件变化立即重新查询（也走同一防抖）。
 */
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    var options by mutableStateOf(SearchOptions())
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val results = _state
        .debounce(250)
        .map { it.toFilter() }
        .distinctUntilChanged()
        .flatMapLatest { container.libraryQuery.worksPager(it) }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch { loadOptions() }
    }

    fun editQuery(value: String) = _state.update { it.copy(query = value) }
    fun setType(value: String) = _state.update { it.copy(type = value) }
    fun setSort(value: String) = _state.update { it.copy(sort = value) }
    fun setGenre(value: String) = _state.update { it.copy(genre = value) }
    fun setRegion(value: String) = _state.update { it.copy(region = value) }
    fun setYear(value: String) = _state.update { it.copy(year = value) }

    private suspend fun loadOptions() {
        // 筛选选项只取最近 500 部作品，避免 20k 库全量加载。
        val cards = container.libraryQuery.recentWorks(limit = 500)
        options = SearchOptions(
            genres = listOf("全部") + topGenres(cards, limit = 10),
            regions = listOf("全部") + topRegions(cards, limit = 8),
            years = yearOptions(cards),
        )
    }

    private fun SearchUiState.toFilter(): WorksFilter = WorksFilter(
        query = query,
        type = type.takeUnless { it == "全部" }.orEmpty(),
        genre = genre.takeUnless { it == "全部" }.orEmpty(),
        country = region.takeUnless { it == "全部" }.orEmpty(),
        year = year.takeUnless { it == "全部" }.orEmpty(),
        sort = when (sort) {
            "影片评分" -> WorksSort.RATING
            "最新上映" -> WorksSort.YEAR
            else -> WorksSort.RECENT
        },
    )

    private fun topGenres(cards: List<WorkCard>, limit: Int): List<String> =
        cards.flatMap { it.genres }
            .groupingBy { it }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(limit)

    private fun topRegions(cards: List<WorkCard>, limit: Int): List<String> =
        cards.mapNotNull { it.country }
            .groupingBy { it }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .map { it.key }
            .take(limit)

    private fun yearOptions(cards: List<WorkCard>): List<String> {
        val years = buildSet {
            cards.forEach { card ->
                (card.year ?: card.releaseDate?.take(4)?.toIntOrNull())?.let(::add)
            }
        }.sortedDescending()
        return (listOf("全部", "2020年代", "2010年代") + years.take(8).map { it.toString() })
            .distinct()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(container) }
        }
    }
}
