package com.lovelev.mediadock.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaybackProgress
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.tmdb.CastMember
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.ui.library.orderedEpisodes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DetailViewModel(private val container: AppContainer) : ViewModel() {

    private val paths = container.detailSelection.paths
    private val primaryPath = paths.firstOrNull().orEmpty()

    val entries: StateFlow<List<MediaItemEntity>> = container.library.observeByPaths(paths)
        .map { it.orderedEpisodes() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 补充信息（类型/国家/时长/上映日期）。**打开详情页时才拉，拉过就缓存。** */
    val detail: StateFlow<TitleDetail?> = container.details.observe(primaryPath)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val cast: StateFlow<List<CastMember>> = detail
        .map { d -> d?.let { container.details.cast(it) } ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** path → 进度。既用来画每集的进度条，也用来决定「继续播放」播哪一集，还提供时长。 */
    val progress: StateFlow<Map<String, PlaybackProgress>> = container.progress.observeAll()
        .map { rows -> rows.associateBy { it.path } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val favorites: StateFlow<Set<String>> = container.favorites.observePaths()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 右上角菜单里那条操作的结果（成功为空）。 */
    var notice by mutableStateOf<String?>(null)
        private set

    /**
     * 海报底部的主色调 —— 详情页下半部分的背景用它接出去（见 PosterTint）。
     * null = 取不到色（没图 / 没网 / 解码失败），界面走兜底渐变，不影响功能。
     */
    var heroTint by mutableStateOf<Color?>(null)
        private set

    // ⚠️ init 必须放在它引用的属性**之后** —— Kotlin 按声明顺序初始化，
    // 之前把 init 写在了 entries 前面，运行起来就是 NullPointerException。
    init {
        viewModelScope.launch {
            // 等条目到位（StateFlow 的初值是空列表，别直接 first()）
            val item = entries.first { it.isNotEmpty() }.first()

            // 背景主色：取海报底部一条的均值（w92 只有几 KB），拿不到就算了
            item.heroArt()?.let { art ->
                heroTint = PosterTint.bottomAverage(
                    url = TmdbClient.posterUrl(art, width = 92),
                    client = container.okHttpClient,
                )
            }

            val tmdbId = item.tmdbId ?: return@launch
            val isTv = item.episode != null || item.season != null
            container.details.ensure(item.path, tmdbId, isTv)?.let { notice = it }
        }
    }

    /** 标记已看 / 未看。已看的判据和海报墙一致：进度 ≥ 92%。 */
    fun setWatched(path: String, durationMs: Long, watched: Boolean) {
        viewModelScope.launch {
            if (watched) {
                if (durationMs <= 0L) {
                    notice = "还不知道时长，先播一下再标记"
                } else {
                    container.progress.markWatched(path, durationMs)
                    notice = null
                }
            } else {
                container.progress.clear(path)
                notice = null
            }
        }
    }

    fun rescrape(path: String) {
        viewModelScope.launch {
            notice = "正在重新匹配…"
            notice = container.library.rescrapeOne(path)
        }
    }

    fun saveOverride(path: String, title: String, year: Int?) {
        viewModelScope.launch {
            container.library.saveOverride(path, title, year)
            notice = null
        }
    }

    fun toggleFavorite(path: String) {
        viewModelScope.launch { container.favorites.toggle(path) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DetailViewModel(container) }
        }
    }
}
