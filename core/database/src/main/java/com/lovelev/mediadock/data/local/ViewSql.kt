package com.lovelev.mediadock.data.local

/**
 * 兼容 VIEW 的唯一 SQL 来源：Room 的 `@DatabaseView` 与 [MediaDatabase.MIGRATION_8_9]
 * 都引用这里，避免「注解一套、迁移另一套」。
 */
object ViewSql {

    const val MEDIA_ITEMS = """
        SELECT
          mf.legacy_path AS path,
          mf.name AS name,
          mf.size_bytes AS sizeBytes,
          mf.mtime AS lastModified,
          CASE WHEN mf.legacy_compat = 1 THEN mf.parsed_title
               ELSE COALESCE(mf.parsed_title, mw.title, ew.title, mf.name) END AS parsedTitle,
          CASE WHEN mf.legacy_compat = 1 THEN mf.parsed_year
               ELSE COALESCE(mf.parsed_year, mw.year, ew.year) END AS year,
          CASE WHEN mf.legacy_compat = 1 THEN mf.parsed_season
               ELSE COALESCE(mf.parsed_season, e.season) END AS season,
          CASE WHEN mf.legacy_compat = 1 THEN mf.parsed_episode
               ELSE COALESCE(mf.parsed_episode, e.episode) END AS episode,
          COALESCE(mw.scrape_state, ew.scrape_state, 'UNSCRAPED') AS scrapeState,
          COALESCE(mw.tmdb_id, ew.tmdb_id) AS tmdbId,
          COALESCE(mw.poster_path, ew.poster_path) AS posterPath,
          COALESCE(mw.backdrop_path, ew.backdrop_path) AS backdropPath,
          COALESCE(mw.overview, ew.overview) AS overview,
          COALESCE(mw.rating, ew.rating) AS rating,
          COALESCE(mw.scraped_at, ew.scraped_at) AS scrapedAt
        FROM media_files mf
        LEFT JOIN movie_versions mv ON mv.file_id = mf.id
        LEFT JOIN works mw ON mw.id = mv.work_id
        LEFT JOIN (
          SELECT ev.file_id, e.id, e.work_id, e.season, e.episode
          FROM episode_versions ev
          JOIN episodes e ON e.id = ev.episode_id
          JOIN file_segments fs ON fs.file_id = ev.file_id AND fs.episode_id = e.id
          WHERE fs.segment_index = (
            SELECT MIN(fs2.segment_index) FROM file_segments fs2 WHERE fs2.file_id = ev.file_id
          )
        ) e ON e.file_id = mf.id
        LEFT JOIN works ew ON ew.id = e.work_id
        WHERE mf.missing_at IS NULL
    """

    const val PLAYBACK_PROGRESS = """
        SELECT mf.legacy_path AS path, ep.position_ms AS positionMs,
               ep.duration_ms AS durationMs, ep.updated_at AS updatedAt
        FROM episode_progress ep
        JOIN episode_versions ev ON ev.episode_id = ep.episode_id
        JOIN media_files mf ON mf.id = ev.file_id
        WHERE mf.missing_at IS NULL
        UNION ALL
        SELECT mf.legacy_path AS path, mp.position_ms AS positionMs,
               mp.duration_ms AS durationMs, mp.updated_at AS updatedAt
        FROM movie_progress mp
        JOIN movie_versions mv ON mv.work_id = mp.work_id
        JOIN media_files mf ON mf.id = mv.file_id
        WHERE mf.missing_at IS NULL
        UNION ALL
        SELECT mf.legacy_path AS path, fp.position_ms AS positionMs,
               fp.duration_ms AS durationMs, fp.updated_at AS updatedAt
        FROM file_progress fp
        JOIN media_files mf ON mf.id = fp.file_id
        WHERE mf.missing_at IS NULL
    """

    const val FAVORITES = """
        SELECT mf.legacy_path AS path, f.added_at AS addedAt
        FROM favorites f
        JOIN (
          SELECT mv.work_id AS work_id, mv.file_id AS file_id FROM movie_versions mv
          UNION
          SELECT e.work_id AS work_id, ev.file_id AS file_id
          FROM episode_versions ev JOIN episodes e ON e.id = ev.episode_id
        ) x ON x.work_id = f.work_id
        JOIN media_files mf ON mf.id = x.file_id
        WHERE mf.missing_at IS NULL
    """
    const val PLAYLIST_ITEMS = """
        SELECT pi.playlist_id AS playlistId, mf.legacy_path AS path, pi.added_at AS addedAt
        FROM playlist_items pi
        JOIN media_files mf ON mf.id = (
          SELECT mf2.id FROM media_files mf2
          WHERE mf2.missing_at IS NULL AND mf2.id IN (
            SELECT file_id FROM movie_versions WHERE work_id = pi.work_id
            UNION
            SELECT ev.file_id FROM episode_versions ev
            JOIN episodes e ON e.id = ev.episode_id WHERE e.work_id = pi.work_id
          )
          ORDER BY mf2.legacy_path LIMIT 1
        )
    """

    const val TITLE_OVERRIDES = """
        SELECT mf.legacy_path AS path, o.title AS title, o.year AS year
        FROM title_overrides o
        JOIN (
          SELECT mv.work_id AS work_id, mv.file_id AS file_id FROM movie_versions mv
          UNION
          SELECT e.work_id AS work_id, ev.file_id AS file_id
          FROM episode_versions ev JOIN episodes e ON e.id = ev.episode_id
        ) x ON x.work_id = o.work_id
        JOIN media_files mf ON mf.id = x.file_id
        WHERE mf.missing_at IS NULL
    """

    const val TITLE_DETAILS = """
        SELECT mf.legacy_path AS path, wd.tmdb_id AS tmdbId,
               wd.release_date AS releaseDate, wd.runtime_minutes AS runtimeMinutes,
               wd.genres AS genres, wd.country AS country, wd.cast_json AS castJson,
               wd.fetched_at AS fetchedAt, wd.total_episodes AS totalEpisodes,
               wd.total_seasons AS totalSeasons
        FROM work_details wd
        JOIN (
          SELECT mv.work_id AS work_id, mv.file_id AS file_id FROM movie_versions mv
          UNION
          SELECT e.work_id AS work_id, ev.file_id AS file_id
          FROM episode_versions ev JOIN episodes e ON e.id = ev.episode_id
        ) x ON x.work_id = wd.work_id
        JOIN media_files mf ON mf.id = x.file_id
        WHERE mf.missing_at IS NULL
    """
}
