package com.lovelev.mediadock.core.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.lovelev.mediadock.core.model.SearchText
import com.lovelev.mediadock.data.local.WorkCardRow

enum class WorksSort { TITLE, RATING, YEAR, RECENT }

/** 海报墙的一块（电影或剧）。[paths] 是该作品的全部文件，详情页仍按旧 path 接口工作。 */
data class WorkCard(
    val id: String,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?,
    val type: String,
    val episodeCount: Int,
    val completedEpisodes: Int,
    val movieProgress: Float,
    val isFavorite: Boolean,
    val genres: List<String>,
    val country: String?,
    val releaseDate: String?,
    val paths: List<String>,
) {
    /** 剧按「已看完集数 / 总集数」，单片按观看比例。 */
    val progressFraction: Float
        get() = if (episodeCount > 0) {
            completedEpisodes.toFloat() / episodeCount
        } else {
            movieProgress
        }
}

/** 海报墙/推荐/分类行共用的 works 查询。分页由 Room 的 PagingSource 追加 LIMIT/OFFSET。 */
object WorksCardSql {

    private const val SELECT = """
        SELECT
          w.id AS id,
          COALESCE(o.title, w.title) AS title,
          w.year AS year,
          w.poster_path AS posterPath,
          w.backdrop_path AS backdropPath,
          w.overview AS overview,
          w.rating AS rating,
          w.type AS type,
          w.added_at AS addedAt,
          (SELECT COUNT(*) FROM episodes e WHERE e.work_id = w.id) AS episodeCount,
          (SELECT COUNT(*) FROM episode_progress ep
             JOIN episodes e2 ON e2.id = ep.episode_id
             WHERE e2.work_id = w.id AND ep.user_id = 'default' AND ep.completed = 1) AS completedEpisodes,
          COALESCE((
            SELECT MAX(CASE WHEN mp.duration_ms > 0
                       THEN CAST(mp.position_ms AS REAL) / mp.duration_ms ELSE 0 END)
            FROM movie_progress mp WHERE mp.work_id = w.id AND mp.user_id = 'default'
          ), 0.0) AS movieProgress,
          EXISTS(SELECT 1 FROM favorites f
                 WHERE f.work_id = w.id AND f.user_id = 'default') AS isFavorite,
          wd.genres AS genres,
          wd.country AS country,
          wd.release_date AS releaseDate,
          (SELECT group_concat(path, char(31)) FROM (
             SELECT mf.legacy_path AS path
             FROM movie_versions mv JOIN media_files mf ON mf.id = mv.file_id
             WHERE mv.work_id = w.id AND mf.missing_at IS NULL
             UNION
             SELECT mf.legacy_path AS path
             FROM episode_versions ev JOIN episodes e3 ON e3.id = ev.episode_id
             JOIN media_files mf ON mf.id = ev.file_id
             WHERE e3.work_id = w.id AND mf.missing_at IS NULL
          )) AS paths
        FROM works w
        LEFT JOIN work_details wd ON wd.work_id = w.id
        LEFT JOIN title_overrides o ON o.work_id = w.id
        WHERE (
          EXISTS (SELECT 1 FROM movie_versions mv2
                  JOIN media_files mf2 ON mf2.id = mv2.file_id
                  WHERE mv2.work_id = w.id AND mf2.missing_at IS NULL)
          OR EXISTS (SELECT 1 FROM episode_versions ev2
                     JOIN episodes e4 ON e4.id = ev2.episode_id
                     JOIN media_files mf2 ON mf2.id = ev2.file_id
                     WHERE e4.work_id = w.id AND mf2.missing_at IS NULL)
        )
    """

    fun paging(filter: WorksFilter = WorksFilter()): SupportSQLiteQuery {
        val sql = "$SELECT AND ${where(filter)} ORDER BY ${orderBy(filter.sort)}"
        return SimpleSQLiteQuery(sql, filter.args())
    }

    fun paging(sort: WorksSort, search: String = ""): SupportSQLiteQuery =
        paging(WorksFilter(query = search, sort = sort))

    fun limited(
        sort: WorksSort = WorksSort.RECENT,
        limit: Int = 200,
        search: String = "",
    ): SupportSQLiteQuery {
        val filter = WorksFilter(query = search, sort = sort)
        val sql = "$SELECT AND ${where(filter)} ORDER BY ${orderBy(sort)} LIMIT $limit"
        return SimpleSQLiteQuery(sql, filter.args())
    }

    fun random(limit: Int = 5): SupportSQLiteQuery =
        SimpleSQLiteQuery("$SELECT ORDER BY RANDOM() LIMIT $limit")

    fun favorites(limit: Int = 20): SupportSQLiteQuery =
        SimpleSQLiteQuery(
            "$SELECT AND EXISTS(SELECT 1 FROM favorites f2 " +
                "WHERE f2.work_id = w.id AND f2.user_id = 'default') " +
                "ORDER BY w.added_at DESC LIMIT $limit",
        )

    private fun where(filter: WorksFilter): String {
        val clauses = mutableListOf<String>()
        if (filter.query.isNotBlank() && SearchText.query(filter.query) != null) {
            clauses += "w.id IN (SELECT work_id FROM media_fts WHERE media_fts MATCH ?)"
        }
        when (filter.type) {
            "电影" -> clauses += "w.type = 'movie'"
            "电视剧" -> clauses += "w.type = 'tv' AND COALESCE(wd.genres,'') NOT LIKE '%动画%' " +
                "AND COALESCE(wd.genres,'') NOT LIKE '%综艺%' AND COALESCE(wd.genres,'') NOT LIKE '%真人秀%'"
            "动漫" -> clauses += "COALESCE(wd.genres,'') LIKE '%动画%'"
            "综艺" -> clauses += "(COALESCE(wd.genres,'') LIKE '%综艺%' OR COALESCE(wd.genres,'') LIKE '%真人秀%')"
        }
        if (filter.genre.isNotBlank()) {
            clauses += "COALESCE(wd.genres,'') LIKE '%' || ? || '%'"
        }
        if (filter.country.isNotBlank()) {
            clauses += "wd.country = ?"
        }
        when {
            filter.year.isBlank() -> Unit
            filter.year.endsWith("年代") -> {
                val decade = filter.year.removeSuffix("年代").toIntOrNull()
                if (decade != null) {
                    clauses += "COALESCE(w.year, CAST(substr(wd.release_date,1,4) AS INTEGER)) BETWEEN ? AND ?"
                }
            }
            else -> {
                if (filter.year.toIntOrNull() != null) {
                    clauses += "COALESCE(w.year, CAST(substr(wd.release_date,1,4) AS INTEGER)) = ?"
                }
            }
        }
        return if (clauses.isEmpty()) "1 = 1" else clauses.joinToString(" AND ")
    }

    private fun orderBy(sort: WorksSort): String = when (sort) {
        WorksSort.TITLE -> "COALESCE(o.title, w.title) COLLATE NOCASE, w.id"
        WorksSort.RATING -> "COALESCE(w.rating, -1) DESC, COALESCE(o.title, w.title) COLLATE NOCASE"
        WorksSort.YEAR -> "COALESCE(w.year, 0) DESC, COALESCE(o.title, w.title) COLLATE NOCASE"
        WorksSort.RECENT -> "w.added_at DESC, COALESCE(o.title, w.title) COLLATE NOCASE"
    }
}

data class WorksFilter(
    val query: String = "",
    /** 全部 / 电视剧 / 电影 / 动漫 / 综艺 */
    val type: String = "",
    /** 类型名（work_details.genres 中的一项） */
    val genre: String = "",
    val country: String = "",
    /** 全部 / 2020年代 / 具体年份 */
    val year: String = "",
    val sort: WorksSort = WorksSort.RECENT,
) {
    internal fun args(): Array<Any?> {
        val args = mutableListOf<Any?>()
        SearchText.query(query)?.let { args += it }
        if (genre.isNotBlank()) args += genre
        if (country.isNotBlank()) args += country
        if (year.endsWith("年代")) {
            year.removeSuffix("年代").toIntOrNull()?.let { args += it; args += it + 9 }
        } else {
            year.toIntOrNull()?.let { args += it }
        }
        return args.toTypedArray()
    }
}

fun WorkCardRow.toWorkCard(): WorkCard {
    val pathList = paths?.split('\u001F')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    return WorkCard(
        id = id,
        title = title.ifBlank { pathList.firstOrNull()?.substringAfterLast('/') ?: "未命名" },
        year = year,
        posterPath = posterPath,
        backdropPath = backdropPath,
        overview = overview,
        rating = rating,
        type = type,
        episodeCount = episodeCount,
        completedEpisodes = completedEpisodes,
        movieProgress = movieProgress.toFloat(),
        isFavorite = isFavorite != 0,
        genres = genres?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
        country = country?.takeIf { it.isNotBlank() },
        releaseDate = releaseDate?.takeIf { it.isNotBlank() },
        paths = pathList,
    )
}
