package com.lovelev.mediadock.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaybackProgress
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val recent: List<PlaybackProgress> = emptyList(),
    val items: List<MediaItemEntity> = emptyList(),
    val byPath: Map<String, MediaItemEntity> = emptyMap(),
)

/** 播放历史：进度与媒体库元数据合并在这里，队列构建也放在这里。 */
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    val ui: StateFlow<HistoryUiState> =
        combine(container.progress.observeRecent(), container.library.items) { recent, items ->
            HistoryUiState(
                recent = recent,
                items = items,
                byPath = items.associateBy { it.path },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState(),
        )

    fun queueFor(path: String): List<String> = historyPlayQueue(path, ui.value.items)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container) }
        }
    }
}
