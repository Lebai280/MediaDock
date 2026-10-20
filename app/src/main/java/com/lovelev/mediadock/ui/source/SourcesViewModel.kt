package com.lovelev.mediadock.ui.source

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.webdav.ShareConfig
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourcesUiState(
    val sources: List<SourceConfig> = emptyList(),
    val detail: SourceConfig? = null,
    val pendingDelete: SourceConfig? = null,
    val impact: Int? = null,
    val hint: String? = null,
)

/** 数据源管理：列表、启停、分享与删除（含影响数）。对话框状态留在 ViewModel，旋屏不丢。 */
class SourcesViewModel(private val container: AppContainer) : ViewModel() {

    private val form = MutableStateFlow(SourcesUiState())

    val state: StateFlow<SourcesUiState> =
        combine(container.sources.sources, form) { sources, formState ->
            formState.copy(sources = sources)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SourcesUiState(),
        )

    init {
        viewModelScope.launch { container.sources.refresh() }
    }

    fun showDetail(source: SourceConfig) = form.update { it.copy(detail = source) }

    fun dismissDetail() = form.update { it.copy(detail = null) }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { container.sources.setEnabled(id, enabled) }
    }

    fun requestDelete(source: SourceConfig) {
        form.update { it.copy(pendingDelete = source, impact = null) }
        viewModelScope.launch {
            val count = container.sources.visibleFileCount(source.id)
            // 用户可能已经关掉对话框并换了目标，只有当前目标才写入影响数
            form.update { if (it.pendingDelete?.id == source.id) it.copy(impact = count) else it }
        }
    }

    fun cancelDelete() = form.update { it.copy(pendingDelete = null, impact = null) }

    /**
     * 删除源。顺序是硬约束：先取消该源的扫描，再删注册表与文件数据，
     * 否则扫描任务会继续往已删除的源里写。
     */
    fun deleteConfirmed() {
        val source = form.value.pendingDelete ?: return
        form.update { it.copy(pendingDelete = null, impact = null) }
        viewModelScope.launch {
            container.library.cancelScanFor(source.id)
            container.sources.remove(source.id)
            form.update { it.copy(hint = "已删除") }
        }
    }

    /** 分享配置只含地址、账号、密码与根路径；文本拼装在 VM，剪贴板仍由 UI 处理。 */
    fun shareText(source: SourceConfig): String? = runCatching {
        ShareConfig.build(
            url = source.url,
            username = source.username,
            password = source.password,
            rootPath = source.rootPath,
        )
    }.getOrNull()

    fun showHint(text: String) = form.update { it.copy(hint = text) }

    fun clearHint() = form.update { it.copy(hint = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SourcesViewModel(container) }
        }
    }
}
