package com.lovelev.mediadock.data.tmdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import com.lovelev.mediadock.data.Countries
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * TMDB 凭据。TMDB 认两种，两种都收 —— 用户从申请页拿到的是「API Read Access Token」
 * （JWT）和「API Key」（32 位十六进制）两个值，两种形式均支持，避免用户猜测。
 */
data class TmdbSecret(val value: String) {
    /** JWT 长这样：`xxx.yyy.zzz`；api_key 是没有点的 32 位十六进制。 */
    val isBearer: Boolean = value.count { it == '.' } == 2
}

/** 演职人员的一条。存成 JSON，所以在数据库层也是这个形状。 */
@Serializable
data class CastMember(
    val name: String,
    val role: String?,
    val profilePath: String?,
)

/**
 * 详情页的补充信息。**一次请求就能拿全**（`append_to_response=credits`）。
 * 实测：`/movie/{id}?language=zh-CN&append_to_response=credits` 会带出
 * `genres[].name`（类型名，不用再去查类型表）、`production_countries`、`runtime`、
 * `release_date`、`credits.cast[]` / `credits.crew[]`。
 */
data class TmdbDetails(
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    val country: String?,
    val cast: List<CastMember>,
    val totalEpisodes: Int? = null,
    val totalSeasons: Int? = null,
)

/** 分集元数据（按季拉取）。 */
data class TmdbEpisode(
    val episode: Int,
    val name: String,
    val stillPath: String?,
    val runtimeMinutes: Int?,
)

/** 一条刮削结果。图片只存 path，完整地址由 [posterUrl] 现拼。 */
data class TmdbMatch(
    val tmdbId: Int,
    val title: String?,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?,
)

class TmdbException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 凭据不对：整轮刮削不得继续重试。 */
class TmdbAuthException(message: String) : Exception(message)

class TmdbClient(private val client: OkHttpClient) {

    suspend fun searchMovie(query: String, year: Int?, secret: TmdbSecret): TmdbMatch? =
        searchMovieCandidates(query, year, secret)
            .let { results -> results.firstOrNull { it.posterPath != null } ?: results.firstOrNull() }

    /** 手动匹配用：把所有候选都返回，让用户挑正确的那一条。 */
    suspend fun searchMovieCandidates(query: String, year: Int?, secret: TmdbSecret): List<TmdbMatch> {
        val params = mutableMapOf("query" to query, "language" to LANGUAGE, "include_adult" to "false")
        year?.let { params["year"] = it.toString() }
        var results = search("/search/movie", params, secret)
        // 年份猜错是常事（片名本身带年份、或发行年与检索年不一致）→ 去掉年份再试一次
        if (results.isEmpty() && year != null) {
            params.remove("year")
            results = search("/search/movie", params, secret)
        }
        return results.map { it.toMatch() }
    }

    suspend fun searchTv(query: String, year: Int?, secret: TmdbSecret): TmdbMatch? =
        searchTvCandidates(query, year, secret)
            .let { results -> results.firstOrNull { it.posterPath != null } ?: results.firstOrNull() }

    /** 手动匹配用：把所有候选都返回，让用户挑正确的那一条。 */
    suspend fun searchTvCandidates(query: String, year: Int?, secret: TmdbSecret): List<TmdbMatch> {
        val params = mutableMapOf("query" to query, "language" to LANGUAGE)
        year?.let { params["first_air_date_year"] = it.toString() }
        var results = search("/search/tv", params, secret)
        if (results.isEmpty() && year != null) {
            params.remove("first_air_date_year")
            results = search("/search/tv", params, secret)
        }
        return results.map { it.toMatch() }
    }

    /** 拉一季的分集元数据（剧照/分集名/时长）。详情页按需调用，调用方负责缓存。 */
    suspend fun fetchSeason(tmdbId: Int, season: Int, secret: TmdbSecret): List<TmdbEpisode> =
        withContext(Dispatchers.IO) {
            val body = readBody(
                requestFor("/tv/$tmdbId/season/$season", mapOf("language" to LANGUAGE), secret),
            )
            val dto = try {
                JSON.decodeFromString<SeasonResponse>(body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TmdbException("TMDB 分集数据无法解析：${e.message}", e)
            }
            dto.episodes.orEmpty().mapNotNull { e ->
                val number = e.episode_number ?: return@mapNotNull null
                TmdbEpisode(
                    episode = number,
                    name = e.name.orEmpty(),
                    stillPath = e.still_path,
                    runtimeMinutes = e.runtime?.takeIf { it > 0 },
                )
            }
        }

    /** 拉一条的详情 + 演职员。剧集走 `/tv`，电影走 `/movie`。 */
    suspend fun fetchDetails(tmdbId: Int, isTv: Boolean, secret: TmdbSecret): TmdbDetails? =
        withContext(Dispatchers.IO) {
            val path = if (isTv) "/tv/$tmdbId" else "/movie/$tmdbId"
            val body = readBody(
                requestFor(path, mapOf("language" to LANGUAGE, "append_to_response" to "credits"), secret),
            )
            val dto = try {
                JSON.decodeFromString<DetailResponse>(body)
            } catch (e: CancellationException) {
                throw e   // 取消要原样抛出，不得作为业务失败吞掉
            } catch (e: Exception) {
                throw TmdbException("TMDB 详情无法解析：${e.message}", e)
            }

            val cast = (dto.credits?.cast ?: emptyList())
                .sortedBy { it.order ?: Int.MAX_VALUE }
                .take(CAST_LIMIT)
                .map { CastMember(name = it.name.orEmpty(), role = it.character, profilePath = it.profile_path) }

            // 导演单独拎出来放最前面（VidHub 也是这么排的）
            val director = (dto.credits?.crew ?: emptyList())
                .firstOrNull { it.job == "Director" }
                ?.let { CastMember(name = it.name.orEmpty(), role = "导演", profilePath = it.profile_path) }

            TmdbDetails(
                releaseDate = (dto.release_date ?: dto.first_air_date)?.takeIf { it.isNotBlank() },
                runtimeMinutes = dto.runtime?.takeIf { it > 0 }
                    ?: dto.episode_run_time?.firstOrNull()?.takeIf { it > 0 },
                genres = dto.genres?.mapNotNull { it.name } ?: emptyList(),
                country = Countries.label(
                    isoCodes = (dto.production_countries ?: emptyList()).mapNotNull { it.iso_3166_1 }
                        .ifEmpty { dto.origin_country ?: emptyList() },
                    fallback = dto.production_countries?.firstOrNull()?.name,
                ),
                cast = listOfNotNull(director) + cast,
                totalEpisodes = dto.number_of_episodes?.takeIf { it > 0 },
                totalSeasons = dto.number_of_seasons?.takeIf { it > 0 },
            )
        }

    private suspend fun search(
        path: String,
        params: Map<String, String>,
        secret: TmdbSecret,
    ): List<SearchResult> = withContext(Dispatchers.IO) {
        val body = readBody(requestFor(path, params, secret))
        try {
            JSON.decodeFromString<SearchResponse>(body).results
        } catch (e: CancellationException) {
            throw e   // 取消要原样抛出，不得作为业务失败吞掉
        } catch (e: Exception) {
            throw TmdbException("TMDB 响应无法解析：${e.message}", e)
        }
    }

    /**
     * **此处的 429 允许重试**：它是 TMDB 限流，不是账号锁定。
     * 与 WebDAV 的「401/429 不得重试」约束语义不同，不得混淆：
     * WebDAV 重试会影响其他客户端；TMDB 按 `Retry-After` 退避符合服务端限流要求。
     */
    /** 组一个 GET 请求（带凭据）。 */
    private fun requestFor(path: String, params: Map<String, String>, secret: TmdbSecret): Request {
        val builder = BASE.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/'))
        for ((k, v) in params) builder.addQueryParameter(k, v)
        if (!secret.isBearer) builder.addQueryParameter("api_key", secret.value)
        return Request.Builder()
            .url(builder.build())
            .apply { if (secret.isBearer) header("Authorization", "Bearer ${secret.value}") }
            .header("Accept", "application/json")
            .build()
    }

    private suspend fun readBody(request: Request): String {
        var attempt = 0
        while (true) {
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw TmdbException("连不上 TMDB：${e.message}", e)
            }
            response.use { resp ->
                when (resp.code) {
                    200 -> return resp.body.string()
                    401 -> throw TmdbAuthException("TMDB 凭据无效，请到设置里重新填写")
                    429 -> {
                        val wait = resp.header("Retry-After")?.trim()?.toLongOrNull() ?: DEFAULT_BACKOFF
                        if (attempt >= MAX_RATE_RETRIES) {
                            throw TmdbException("TMDB 一直限流，稍后再试")
                        }
                        attempt++
                        // 按服务器说的等，但避免因异常值长时间阻塞
                        delay(wait.coerceIn(1L, MAX_BACKOFF) * 1000)
                    }
                    else -> throw TmdbException("TMDB 返回 HTTP ${resp.code}")
                }
            }
        }
    }

    private fun SearchResult.toMatch(): TmdbMatch {
        val date = release_date ?: first_air_date
        return TmdbMatch(
            tmdbId = id,
            title = title ?: name,
            year = date?.take(4)?.toIntOrNull(),
            posterPath = poster_path,
            backdropPath = backdrop_path,
            overview = overview?.takeIf { it.isNotBlank() },
            rating = vote_average?.takeIf { it > 0.0 },
        )
    }

    companion object {
        private const val BASE = "https://api.themoviedb.org/3"
        private const val LANGUAGE = "zh-CN"
        private const val DEFAULT_BACKOFF = 5L
        private const val MAX_BACKOFF = 30L
        private const val MAX_RATE_RETRIES = 2

        /** 演员表最多带这么多条，够铺一条横滑了，避免详情页数据量过大。 */
        private const val CAST_LIMIT = 20

        /** 图片地址：只存 path，宽度按用处拼。 */
        fun posterUrl(posterPath: String, width: Int = 342): String =
            "https://image.tmdb.org/t/p/w$width$posterPath"

        /** 完整 URL（Emby/Jellyfin 海报）原样返回，其余按 TMDB path 拼接。 */
        fun posterModel(posterPath: String, width: Int = 342): String =
            if (posterPath.startsWith("http://") || posterPath.startsWith("https://")) {
                posterPath
            } else {
                posterUrl(posterPath, width)
            }

        /** 分集剧照。 */
        fun stillUrl(stillPath: String, width: Int = 300): String =
            "https://image.tmdb.org/t/p/w$width$stillPath"

        private val JSON = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class SeasonResponse(
    val episodes: List<EpisodeDto>? = null,
)

@Serializable
private data class EpisodeDto(
    val episode_number: Int? = null,
    val name: String? = null,
    val still_path: String? = null,
    val runtime: Int? = null,
)

@Serializable
private data class DetailResponse(
    val release_date: String? = null,
    val first_air_date: String? = null,
    val runtime: Int? = null,
    val episode_run_time: List<Int>? = null,
    val number_of_episodes: Int? = null,
    val number_of_seasons: Int? = null,
    val genres: List<GenreDto>? = null,
    val production_countries: List<CountryDto>? = null,
    val origin_country: List<String>? = null,
    val credits: CreditsDto? = null,
)

@Serializable
private data class GenreDto(val name: String? = null)

@Serializable
private data class CountryDto(val iso_3166_1: String? = null, val name: String? = null)

@Serializable
private data class CreditsDto(
    val cast: List<CastDto>? = null,
    val crew: List<CrewDto>? = null,
)

@Serializable
private data class CastDto(
    val name: String? = null,
    val character: String? = null,
    val profile_path: String? = null,
    val order: Int? = null,
)

@Serializable
private data class CrewDto(
    val name: String? = null,
    val job: String? = null,
    val profile_path: String? = null,
)

@Serializable
private data class SearchResponse(val results: List<SearchResult> = emptyList())

@Serializable
private data class SearchResult(
    val id: Int = 0,
    val title: String? = null,
    val name: String? = null,
    val release_date: String? = null,
    val first_air_date: String? = null,
    val vote_average: Double? = null,
    val poster_path: String? = null,
    val backdrop_path: String? = null,
    val overview: String? = null,
)
