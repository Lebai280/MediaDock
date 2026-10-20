package com.lovelev.mediadock.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.local.Playlist
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val creating: Boolean = false,
    val draftName: String = "",
    val pendingDelete: Long? = null,
)

/** 片单列表：数据来自仓库，新建/删除对话框与会话草稿留在 ViewModel 里，旋屏不丢。 */
class PlaylistsViewModel(private val container: AppContainer) : ViewModel() {

    private val form = MutableStateFlow(PlaylistsUiState())

    val state: StateFlow<PlaylistsUiState> =
        combine(container.playlists.playlists(), form) { playlists, formState ->
            formState.copy(playlists = playlists)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlaylistsUiState(),
        )

    fun startCreate() = form.update { it.copy(creating = true, draftName = "") }

    fun cancelCreate() = form.update { it.copy(creating = false, draftName = "") }

    fun updateDraft(name: String) = form.update { it.copy(draftName = name) }

    fun requestDelete(id: Long) = form.update { it.copy(pendingDelete = id) }

    fun cancelDelete() = form.update { it.copy(pendingDelete = null) }

    /** 创建成功后返回新片单 id；名称为空时关闭对话框并返回 null。 */
    suspend fun create(): Long? {
        val name = form.value.draftName.trim()
        if (name.isEmpty()) {
            cancelCreate()
            return null
        }
        val id = container.playlists.create(name)
        form.update { it.copy(creating = false, draftName = "") }
        return id
    }

    suspend fun delete(id: Long) {
        form.update { it.copy(pendingDelete = null) }
        container.playlists.delete(id)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlaylistsViewModel(container) }
        }
    }
}
