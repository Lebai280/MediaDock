package com.lovelev.mediadock.ui.nav

import androidx.lifecycle.ViewModelProvider
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.ui.browse.BrowserViewModel
import com.lovelev.mediadock.ui.detail.DetailViewModel
import com.lovelev.mediadock.ui.favorites.FavoritesViewModel
import com.lovelev.mediadock.ui.history.HistoryViewModel
import com.lovelev.mediadock.ui.library.LibraryViewModel
import com.lovelev.mediadock.ui.player.NowPlayingViewModel
import com.lovelev.mediadock.ui.player.PlayerViewModel
import com.lovelev.mediadock.ui.playlist.PlaylistDetailViewModel
import com.lovelev.mediadock.ui.playlist.PlaylistsViewModel
import com.lovelev.mediadock.ui.search.SearchViewModel
import com.lovelev.mediadock.ui.settings.SettingsViewModel
import com.lovelev.mediadock.ui.settings.CapabilitiesViewModel
import com.lovelev.mediadock.ui.source.SourceViewModel
import com.lovelev.mediadock.ui.source.SourcesViewModel

/**
 * 页面 ViewModel 工厂集合。只暴露工厂，不暴露仓库或网络客户端；
 * 组装根在 Activity 里构造一次，NavHost 拿着它创建各页面 ViewModel，不再接触 AppContainer。
 */
class UiFactories(private val container: AppContainer) {
    val nowPlaying: ViewModelProvider.Factory = NowPlayingViewModel.factory(container)
    val library: ViewModelProvider.Factory = LibraryViewModel.factory(container)
    val favorites: ViewModelProvider.Factory = FavoritesViewModel.factory(container)
    val browser: ViewModelProvider.Factory = BrowserViewModel.factory(container)
    val settings: ViewModelProvider.Factory = SettingsViewModel.factory(container)
    val capabilities: ViewModelProvider.Factory = CapabilitiesViewModel.factory(container)
    val search: ViewModelProvider.Factory = SearchViewModel.factory(container)
    val detail: ViewModelProvider.Factory = DetailViewModel.factory(container)
    val player: ViewModelProvider.Factory = PlayerViewModel.factory(container)
    val sourceForm: ViewModelProvider.Factory = SourceViewModel.factory(container)
    val sources: ViewModelProvider.Factory = SourcesViewModel.factory(container)
    val history: ViewModelProvider.Factory = HistoryViewModel.factory(container)
    val playlists: ViewModelProvider.Factory = PlaylistsViewModel.factory(container)

    fun playlistDetail(id: Long): ViewModelProvider.Factory =
        PlaylistDetailViewModel.factory(container, id)
}
