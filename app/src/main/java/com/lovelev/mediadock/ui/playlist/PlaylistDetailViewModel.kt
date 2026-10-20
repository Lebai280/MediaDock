package com.lovelev.mediadock.ui.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaylistItem
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PlaylistDetailUiState(
    val title: String = "片单",
    val rows: List<PlaylistItem> = emptyList(),
    val byPath: Map<String, MediaItemEntity> = emptyMap(),
    val sortedRows: List<PlaylistItem> = emptyList(),
    val sort: PlaylistSort = PlaylistSort.DEFAULT,
)

/** 单个片单：条目、排序、分享与移除。排序/提示留在 ViewModel，旋屏不丢。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModel(
    private val container: AppContainer,
    private val id: Long,
) : ViewModel() {

    private val sort = MutableStateFlow(PlaylistSort.DEFAULT)
    private val rows: Flow<List<PlaylistItem>> = container.playlists.items(id)

    // 空片单不能走 observeByPaths：SQLite 的 `IN ()` 是语法错误。
    private val items: Flow<List<MediaItemEntity>> = rows
        .map { list -> list.map { it.path } }
        .distinctUntilChanged()
        .flatMapLatest { paths ->
            if (paths.isEmpty()) flowOf(emptyList()) else container.library.observeByPaths(paths)
        }

    val state: StateFlow<PlaylistDetailUiState> =
        combine(container.playlists.playlists(), rows, items, sort) { lists, rows, items, sort ->
            val byPath = items.associateBy { it.path }
            PlaylistDetailUiState(
                title = lists.firstOrNull { it.id == id }?.name ?: "片单",
                rows = rows,
                byPath = byPath,
                sortedRows = sortPlaylistRows(rows, byPath, sort),
                sort = sort,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaylistDetailUiState(),
        )

    var sortOpen by mutableStateOf(false)
        private set

    var hint by mutableStateOf<String?>(null)
        private set

    fun openSortMenu() {
        sortOpen = true
    }

    fun closeSortMenu() {
        sortOpen = false
    }

    fun setSort(value: PlaylistSort) {
        sort.value = value
        sortOpen = false
    }

    fun clearHint() {
        hint = null
    }

    fun showHint(text: String) {
        hint = text
    }

    suspend fun playQueue(path: String): List<String> = container.playlists.playQueue(path)

    suspend fun remove(path: String) = container.playlists.remove(id, path)

    fun shareText(): String = buildPlaylistShareText(
        name = state.value.title,
        sortedRows = state.value.sortedRows,
        byPath = state.value.byPath,
    )

    companion object {
        fun factory(container: AppContainer, id: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlaylistDetailViewModel(container, id) }
        }
    }
}
