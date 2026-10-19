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

    suspend fun match(
        title: String,
        year: Int?,
        isEpisode: Boolean,
        secret: TmdbSecret,
    ): TmdbMatch? = if (isEpisode) {
        tmdb.searchTv(title, year, secret)
    } else {
        tmdb.searchMovie(title, year, secret)
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
