package com.lovelev.mediadock.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.ui.nav.initialRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 应用级 UI 状态：主题、起始路由，以及「要播什么 / 要看什么」的内存桥。
 *
 * 组装根只把 AppContainer 交给这个 ViewModel 和 [UiFactories]；composable 不再接触容器。
 */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    val themeMode: StateFlow<String> = container.credentials.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = container.credentials.cachedThemeMode,
        )

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            val sources = container.sources.refresh()
            _startRoute.value = initialRoute(sources.any { it.enabled })
        }
    }

    /** 401/429 或退出登录后的唯一出口：需显式解开应用作用域仓库里的封锁。 */
    fun relogin() {
        container.library.reset()
    }

    fun selectPlayback(path: String, queue: List<String>) {
        container.playbackSelection.set(path, queue)
    }

    fun selectDetail(paths: List<String>) {
        container.detailSelection.set(paths)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(container) }
        }
    }
}
