package com.lovelev.mediadock.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** 收藏页 = 拿媒体库那批数据、只留被收藏的。不需要额外的数据库查询。 */
class FavoritesViewModel(container: AppContainer) : ViewModel() {

    val items: StateFlow<List<MediaItemEntity>> =
        combine(container.library.items, container.favorites.observePaths()) { all, favoritePaths ->
            if (favoritePaths.isEmpty()) emptyList() else all.filter { it.path in favoritePaths }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 海报块底部的进度条，和媒体库页同一份数据。 */
    val progress: StateFlow<Map<String, Float>> = container.progress.observeAll()
        .map { rows ->
            rows.filter { it.durationMs > 0L }
                .associate { it.path to (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FavoritesViewModel(container) }
        }
    }
}
