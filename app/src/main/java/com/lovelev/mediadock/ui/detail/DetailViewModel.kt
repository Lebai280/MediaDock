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
import com.lovelev.mediadock.data.local.EpisodeMeta
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.Playlist
import com.lovelev.mediadock.data.local.PlaybackProgress
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.tmdb.CastMember
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.ui.library.orderedEpisodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    /**
     * 播放队列。
     *
     * 剧集（≥2 集）就是这几集；**单片则取同目录的视频** —— 和从「浏览」页点进来时的行为一致。
     *
     * 为什么单片也要给队列：用户的库里全是独立的短片/电影，没有剧集，
     * 只传 `paths`（1 条）的话播放页的「上一集 / 下一集」会**永远是灰的**，
     * 那两个按钮就等于没做。
     */
    val playQueue: StateFlow<List<String>> = container.library.items
        .map { items ->
            if (paths.size >= 2) return@map paths
            val primary = paths.firstOrNull().orEmpty()
            val folder = primary.substringBeforeLast('/', "")
            val siblings = items
                .filter { it.path.substringBeforeLast('/', "") == folder }
                .map { it.path }
            if (siblings.size >= 2) siblings else paths
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, paths)

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

    /**
     * 同一部电影的其它版本（按 tmdbId 关联，排除剧集文件）。
     * 海报墙已把多版本合并成一块，这里提供详情页的版本切换。
     */
    val versions: StateFlow<List<MediaItemEntity>> =
        combine(container.library.items, entries) { items, eps ->
            val first = eps.firstOrNull() ?: return@combine emptyList()
            if (eps.size >= 2) return@combine emptyList()
            val tmdbId = first.tmdbId ?: return@combine emptyList()
            items.filter { it.tmdbId == tmdbId && it.path != first.path && it.episode == null }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 右上角菜单里那条操作的结果（成功为空）。 */
    var notice by mutableStateOf<String?>(null)
        private set

    /**
     * 海报底部的主色调 —— 详情页下半部分的背景用它接出去（见 PosterTint）。
     * null = 取不到色（没图 / 没网 / 解码失败），界面走回退渐变，不影响功能。
     */
    var heroTint by mutableStateOf<Color?>(null)
        private set

    // init 必须放在它引用的属性**之后** —— Kotlin 按声明顺序初始化，
    // 之前把 init 写在了 entries 前面，运行起来就是 NullPointerException。
    init {
        viewModelScope.launch {
            // 等条目到位（StateFlow 的初值是空列表，不得直接调用 first()）
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
            notice = container.library.rescrapeOne(path)
        }
    }

    /** 整部剧重新匹配（右上角菜单）。 */
    fun rescrapeSeries(paths: List<String>) {
        viewModelScope.launch {
            notice = container.library.rescrapeSeries(paths)
        }
    }

    /** 手动匹配的候选列表（空 = 还没搜或没结果）。 */
    var matchCandidates by mutableStateOf<List<TmdbMatch>>(emptyList())
        private set
    var matchBusy by mutableStateOf(false)
        private set

    fun searchMatches(query: String, year: Int?, isTv: Boolean) {
        viewModelScope.launch {
            matchBusy = true
            matchCandidates = emptyList()
            notice = null
            try {
                matchCandidates = container.library.searchMatchCandidates(query, year, isTv)
                if (matchCandidates.isEmpty()) notice = "无匹配"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: "搜索失败"
            } finally {
                matchBusy = false
            }
        }
    }

    /** 选中一条候选：应用到整部剧/整部片，并清掉旧的详情缓存。 */
    fun applyMatch(paths: List<String>, match: TmdbMatch) {
        viewModelScope.launch {
            matchBusy = true
            notice = null
            try {
                container.library.applyMatch(paths, match)
                matchCandidates = emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: "应用失败"
            } finally {
                matchBusy = false
            }
        }
    }

    /** 保存手改的片名/年份，并用它重刮整部剧。 */
    fun saveOverrideAndScrape(paths: List<String>, title: String, year: Int?) {
        viewModelScope.launch {
            container.library.saveOverride(paths, title, year)
            notice = container.library.rescrapeSeries(paths)
        }
    }

    fun clearOverride(paths: List<String>) {
        viewModelScope.launch {
            container.library.saveOverride(paths, "", null)
            notice = container.library.rescrapeSeries(paths)
        }
    }

    /** 当前季的分集元数据（剧照/分集名/时长），按集号索引。 */
    var episodeMeta by mutableStateOf<Map<Int, EpisodeMeta>>(emptyMap())
        private set
    private val seasonCache = mutableMapOf<Int, Map<Int, EpisodeMeta>>()
    private var seasonToken = 0

    /** 用户片单（详情页「加入片单」用）。 */
    val playlists: StateFlow<List<Playlist>> = container.playlists.playlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addToPlaylist(playlistId: Long, path: String) {
        viewModelScope.launch { container.playlists.add(playlistId, path) }
    }

    fun createPlaylistAndAdd(name: String, path: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = container.playlists.create(name)
            container.playlists.add(id, path)
        }
    }

    fun loadSeason(tmdbId: Int, season: Int) {
        seasonCache[season]?.let {
            episodeMeta = it
            return
        }
        val token = ++seasonToken
        viewModelScope.launch {
            val map = container.details.seasonEpisodes(tmdbId, season)
                .associateBy { it.episode }
            seasonCache[season] = map
            // 用户可能已经切到别的季，只有最后一次请求能更新界面
            if (token == seasonToken) episodeMeta = map
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
