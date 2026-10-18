package com.lovelev.mediadock.data.repo

import com.lovelev.mediadock.data.local.EpisodeMeta
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.data.tmdb.CastMember
import com.lovelev.mediadock.data.tmdb.TmdbAuthException
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.data.tmdb.TmdbException
import com.lovelev.mediadock.data.tmdb.TmdbSecret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** 批量「补齐影片信息」的状态。 */
sealed interface DetailFillState {
    data object Idle : DetailFillState

    data class Running(val done: Int, val total: Int) : DetailFillState

    /**
     * [filled] 是**真拉到的**条数，[failed] 是失败的条数。
     * 不得将「处理过的总数」作为成功数上报 —— 网络不通时每条都会失败，
     * 报「已补齐 63 条」就是假信息（真机验证中出现过）。
     */
    data class Done(val filled: Int, val failed: Int) : DetailFillState

    /** [needsSecret] = 没填 / 填错 TMDB 凭据，界面该把人引到设置。 */
    data class Failed(val message: String, val needsSecret: Boolean) : DetailFillState
}

/**
 * 详情页的补充信息（类型 / 国家 / 时长 / 演职人员）。
 *
 * **默认是「打开详情页时才拉」的**（拉过就缓存）—— 整库刮削时不需要这些，92 条就是 92 次额外请求。
 * 但首页搜索要按类型/国家/演员过滤，没这些就没法用，所以额外提供一个**用户主动触发的批量补齐**
 * （[fillMissing]：4 个一批、可取消、单条失败跳过）。
 */
class DetailsRepository(
    private val db: MediaDatabase,
    private val tmdb: TmdbClient,
    private val credentials: SecureCredentialStore,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** 批量补齐用应用作用域：退出页面不打断；副作用是封锁不会自己消失，得显式取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _fill = MutableStateFlow<DetailFillState>(DetailFillState.Idle)
    val fill: StateFlow<DetailFillState> = _fill.asStateFlow()

    fun observe(path: String): Flow<TitleDetail?> = db.titleDetailDao().observe(path)

    fun cast(detail: TitleDetail): List<CastMember> = runCatching {
        json.decodeFromString<List<CastMember>>(detail.castJson)
    }.getOrDefault(emptyList())

    /**
     * 没有就拉一次并落库。已经有就跳过（**不重复请求**）。
     * 返回 null 表示成功（或本来就有）；否则是给界面看的一句话。
     */
    suspend fun ensure(path: String, tmdbId: Int, isTv: Boolean, force: Boolean = false): String? {
        val existing = db.titleDetailDao().find(path)
        // 老缓存可能没有总集数（v7 新增列）—— 剧集缺这个值就重新拉一次
        if (!force && existing != null && (!isTv || existing.totalEpisodes != null)) return null

        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: return "还没填写 TMDB 凭据"

        return try {
            fetchAndStore(path, tmdbId, isTv, secret)
            null
        } catch (e: TmdbAuthException) {
            e.message ?: "TMDB 凭据无效"
        } catch (e: TmdbException) {
            e.message ?: "拉详情失败"
        }
    }

    /** 把已刮削条目缺的详情一次补齐。正在跑就不重复起。 */
    fun fillMissing(items: List<MediaItemEntity>) {
        if (job?.isActive == true) return
        job = scope.launch {
            val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            if (secret == null) {
                _fill.value = DetailFillState.Failed("还没填写 TMDB 凭据", needsSecret = true)
                return@launch
            }

            val pending = items
                .filter { it.tmdbId != null }
                .filter { db.titleDetailDao().find(it.path) == null }
            if (pending.isEmpty()) {
                _fill.value = DetailFillState.Done(filled = 0, failed = 0)
                return@launch
            }

            _fill.value = DetailFillState.Running(done = 0, total = pending.size)
            var done = 0
            var filled = 0
            try {
                // 4 个一批：和刮削同一套节奏，避免对 TMDB 造成突发压力
                for (batch in pending.chunked(FILL_CONCURRENCY)) {
                    if (!isActive) return@launch
                    val results = coroutineScope {
                        batch.map { item ->
                            async {
                                val isTv = item.episode != null || item.season != null
                                try {
                                    fetchAndStore(item.path, item.tmdbId!!, isTv, secret)
                                    true
                                } catch (e: TmdbAuthException) {
                                    throw e
                                } catch (e: TmdbException) {
                                    // 单条失败（网络抖动等）跳过，别中断整轮
                                    false
                                }
                            }
                        }.awaitAll()
                    }
                    filled += results.count { it }
                    done += batch.size

                    // 一整轮下来一条都没成、已经试过一批了 → 多半是网络/代理不通（或 DNS 被污染）。
                    // 早点停并明确报错，不得逐条失败后才报告「已补齐」。
                    if (filled == 0 && done >= FAIL_ABORT_THRESHOLD) {
                        throw TmdbException("连不上 TMDB —— 检查手机的网络 / 代理")
                    }
                    _fill.value = DetailFillState.Running(done, pending.size)
                }
                _fill.value = DetailFillState.Done(filled = filled, failed = done - filled)
            } catch (e: TmdbAuthException) {
                _fill.value = DetailFillState.Failed(e.message ?: "TMDB 凭据无效", needsSecret = true)
            } catch (e: TmdbException) {
                _fill.value = DetailFillState.Failed(e.message ?: "补齐失败", needsSecret = false)
            }
        }
    }

    fun cancelFill() {
        job?.cancel()
        job = null
        _fill.value = DetailFillState.Idle
    }

    /** 拉一次并落库。凭据 / 网络问题**抛出去** —— 批量补齐要靠它决定中止还是跳过。 */
    private suspend fun fetchAndStore(path: String, tmdbId: Int, isTv: Boolean, secret: TmdbSecret) {
        val details = tmdb.fetchDetails(tmdbId, isTv, secret) ?: throw TmdbException("这一条没查到详情")
        db.titleDetailDao().upsert(
            TitleDetail(
                path = path,
                tmdbId = tmdbId,
                releaseDate = details.releaseDate,
                runtimeMinutes = details.runtimeMinutes,
                genres = details.genres.joinToString(","),
                country = details.country,
                castJson = json.encodeToString(details.cast),
                fetchedAt = System.currentTimeMillis(),
                totalEpisodes = details.totalEpisodes,
                totalSeasons = details.totalSeasons,
            ),
        )
    }

    /** 某一季的分集元数据：先查缓存，没有就拉一次并落库（失败返回空，不抛）。 */
    suspend fun seasonEpisodes(tmdbId: Int, season: Int): List<EpisodeMeta> {
        val cached = db.episodeMetaDao().find(tmdbId, season)
        if (cached.isNotEmpty()) return cached
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret) ?: return emptyList()
        val episodes = try {
            tmdb.fetchSeason(tmdbId, season, secret)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return emptyList()
        }
        if (episodes.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val rows = episodes.map {
            EpisodeMeta(
                tmdbId = tmdbId,
                season = season,
                episode = it.episode,
                name = it.name,
                stillPath = it.stillPath,
                runtimeMinutes = it.runtimeMinutes,
                fetchedAt = now,
            )
        }
        db.episodeMetaDao().upsertAll(rows)
        return rows
    }

    private companion object {
        const val FILL_CONCURRENCY = 4

        /** 试够这么多条还一条没成，就当网络不通、中止（避免整库逐条失败）。 */
        const val FAIL_ABORT_THRESHOLD = 8
    }
}
