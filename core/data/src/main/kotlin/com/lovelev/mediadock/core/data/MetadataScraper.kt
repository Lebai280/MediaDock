package com.lovelev.mediadock.core.data

import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import com.lovelev.mediadock.data.tmdb.TmdbSecret

/**
 * TMDB 匹配的纯网络逻辑：剧集走 search/tv，电影走 search/movie。
 *
 * 状态机（批量刮削、配额、取消）仍留在应用层；这里只负责「拿检索词换一个匹配」，
 * 便于单测与后续被 Worker 复用。
 */
class MetadataScraper(private val tmdb: TmdbClient) {

    /**
     * 匹配一个作品：按类型搜索；没有可接受结果时**跨类型兜底一次**
     * （文件名解析常把电影/剧集判错）。凭据/网络错误原样抛出。
     */
    suspend fun match(
        title: String,
        year: Int?,
        isEpisode: Boolean,
        secret: TmdbSecret,
    ): TmdbMatch? {
        val primary = if (isEpisode) {
            tmdb.searchTv(title, year, secret)
        } else {
            tmdb.searchMovie(title, year, secret)
        }
        if (primary != null) return primary
        return if (isEpisode) {
            tmdb.searchMovie(title, year, secret)
        } else {
            tmdb.searchTv(title, year, secret)
        }
    }

    /** 手动匹配的候选：剧集走 tv，电影走 movie。 */
    suspend fun searchCandidates(
        query: String,
        year: Int?,
        isTv: Boolean,
        secret: TmdbSecret,
    ): List<TmdbMatch> = if (isTv) {
        tmdb.searchTvCandidates(query, year, secret)
    } else {
        tmdb.searchMovieCandidates(query, year, secret)
    }
}
