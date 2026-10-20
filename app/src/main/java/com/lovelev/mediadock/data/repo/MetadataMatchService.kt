package com.lovelev.mediadock.data.repo

import com.lovelev.mediadock.core.data.MetadataScraper
import com.lovelev.mediadock.core.data.withTitleOverride
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.data.tmdb.TmdbAuthException
import com.lovelev.mediadock.data.tmdb.TmdbException
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import com.lovelev.mediadock.data.tmdb.TmdbSecret
import kotlinx.coroutines.flow.first

/** 手工匹配、改片名与单条/整剧重刮。返回 null 表示成功。 */
class MetadataMatchService(
    private val db: MediaDatabase,
    private val credentials: SecureCredentialStore,
    private val scraper: MetadataScraper,
    private val store: MediaLibraryStore,
) {

    /** 整部剧一起改：所有集的覆盖都写同一份，重新刮削时用同一检索词。 */
    suspend fun saveOverride(paths: List<String>, title: String, year: Int?) {
        if (paths.isEmpty()) return
        store.saveOverride(paths, title, year)
    }

    /** 手动匹配的候选：剧集走 tv，电影走 movie。 */
    suspend fun searchMatchCandidates(query: String, year: Int?, isTv: Boolean): List<TmdbMatch> {
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: throw TmdbAuthException("还没填写 TMDB 凭据")
        return scraper.searchCandidates(query, year, isTv, secret)
    }

    /**
     * 把一个手选的匹配应用到**整部剧/整部片**（所有 episode paths），
     * 并把选中的片名/年份写进覆盖表 —— 以后重新扫描也沿用。
     */
    suspend fun applyMatch(paths: List<String>, match: TmdbMatch) {
        if (paths.isEmpty()) return
        val now = System.currentTimeMillis()
        paths.forEach { path ->
            store.applyScrape(
                path = path,
                tmdbId = match.tmdbId,
                title = match.title,
                year = match.year,
                posterPath = match.posterPath,
                backdropPath = match.backdropPath,
                overview = match.overview,
                rating = match.rating,
                matched = true,
                scrapedAt = now,
            )
        }
        paths.forEach { store.completeScrapeTask(it) }
        match.title?.takeIf { it.isNotBlank() }?.let { title ->
            store.saveOverride(paths, title, match.year)
        }
        store.deleteDetails(paths)
    }

    /** 用覆盖后的片名重刮整部剧；成功后把同一匹配应用到所有集。 */
    suspend fun rescrapeSeries(paths: List<String>): String? {
        if (paths.isEmpty()) return "找不到条目"
        val dao = db.mediaDao()
        val overrides = db.titleOverrideDao().observeAll().first().associateBy { it.path }
        val items = dao.observeByPaths(paths).first()
            .map { it.withTitleOverride(overrides[it.path]) }
        if (items.isEmpty()) return "找不到条目"
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: return "还没填写 TMDB 凭据"
        return try {
            val match = scrapeItem(items.first(), secret) ?: return "没匹配到"
            applyMatch(paths, match)
            null
        } catch (e: TmdbAuthException) {
            e.message ?: "TMDB 凭据无效"
        } catch (e: TmdbException) {
            e.message ?: "刮削失败"
        }
    }

    /** 只重刮一条（详情页右上角菜单用）。返回 null 表示成功。 */
    suspend fun rescrapeOne(path: String): String? {
        val dao = db.mediaDao()
        val override = db.titleOverrideDao().observeAll().first().firstOrNull { it.path == path }
        val item = dao.observeByPaths(listOf(path)).first().firstOrNull()
            ?.withTitleOverride(override)
            ?: return "找不到这一条"
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: return "还没填写 TMDB 凭据"
        return try {
            val match = scrapeItem(item, secret)
            store.applyScrape(
                path = path,
                tmdbId = match?.tmdbId,
                title = match?.title,
                year = match?.year,
                posterPath = match?.posterPath,
                backdropPath = match?.backdropPath,
                overview = match?.overview,
                rating = match?.rating,
                matched = match != null,
                scrapedAt = System.currentTimeMillis(),
            )
            if (match == null) "没匹配到，可以试试「修改信息」换个片名" else null
        } catch (e: TmdbAuthException) {
            e.message ?: "TMDB 凭据无效"
        } catch (e: TmdbException) {
            e.message ?: "刮削失败"
        }
    }

    /** 单条：剧集走 search/tv，电影走 search/movie；年份先带上，搜不到会自动去掉年份重试。 */
    private suspend fun scrapeItem(item: MediaItemEntity, secret: TmdbSecret): TmdbMatch? {
        val isEpisode = item.episode != null || item.season != null
        return scraper.match(item.parsedTitle, item.year, isEpisode, secret)
    }
}
