package com.lovelev.mediadock.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lovelev.mediadock.core.model.ContentIds
import com.lovelev.mediadock.core.model.DEFAULT_USER_ID
import com.lovelev.mediadock.core.model.LogicalKeyCodec
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.core.model.normalizeRemoteKey

/**
 * v8 → v9：一次性切到 UUID 身份 + 多集/版本模型，旧表保留为 `*_legacy`。
 *
 * 映射规则以 ARCHITECTURE 15.2 / 15.4 与 S1 原型为准：
 * - 已匹配（scrapeState=MATCHED 且有 tmdbId）→ `tmdb:movie:<id>` / `tmdb:tv:<id>`
 * - 未匹配 → 临时 `file:<sourceId>:<key>`
 * - 收藏/片单/改名按作品去重
 * - 进度按内容（电影/分集）落表，`completed = position >= 90%`
 *
 * 事务由 Room 包住；任一校验失败抛异常 → 整次迁移回滚，旧库保持可用。
 */
object MigrationV8ToV9 {

    fun build(): Migration = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            renameLegacy(db)
            createTables(db)
            seedSources(db, now)
            backfill(db, now)
            createViews(db)
            createFts(db)
            verifyMediaItemsView(db)
            verify(db)
        }
    }

    private fun renameLegacy(db: SupportSQLiteDatabase) {
        listOf(
            "media_items", "playback_progress", "favorites", "playlists",
            "playlist_items", "title_overrides", "title_details",
        ).forEach { table ->
            db.execSQL("ALTER TABLE `$table` RENAME TO `${table}_legacy`")
        }
    }

    private fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sources` (" +
                "`id` TEXT NOT NULL, `type` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`base_url` TEXT, `root_path` TEXT, `credential_ref` TEXT, " +
                "`enabled` INTEGER NOT NULL, `priority` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, `last_scan_at` INTEGER, `health` TEXT, " +
                "`root_generation` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `media_files` (" +
                "`id` TEXT NOT NULL, `source_id` TEXT NOT NULL, `remote_key` TEXT NOT NULL, " +
                "`legacy_path` TEXT, `provider_item_id` TEXT, `media_kind` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `size_bytes` INTEGER, `mtime` TEXT, `etag` TEXT, " +
                "`scan_run_id` INTEGER, `missing_at` INTEGER, `parsed_title` TEXT, " +
                "`parsed_year` INTEGER, `parsed_season` INTEGER, `parsed_episode` INTEGER, " +
                "`legacy_compat` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_media_files_source_id_remote_key` " +
                "ON `media_files` (`source_id`, `remote_key`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `works` (" +
                "`id` TEXT NOT NULL, `logical_key` TEXT NOT NULL, `tmdb_id` INTEGER, " +
                "`type` TEXT NOT NULL, `title` TEXT NOT NULL, `sort_title` TEXT, `year` INTEGER, " +
                "`poster_path` TEXT, `backdrop_path` TEXT, `overview` TEXT, `rating` REAL, " +
                "`total_seasons` INTEGER, `total_episodes` INTEGER, `collection_id` TEXT, " +
                "`metadata_source` TEXT, `external_ids_json` TEXT, " +
                "`added_at` INTEGER NOT NULL, `latest_file_mtime` INTEGER, `updated_at` INTEGER, " +
                "`scrape_state` TEXT NOT NULL, `scraped_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_works_logical_key` ON `works` (`logical_key`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `episodes` (" +
                "`id` TEXT NOT NULL, `logical_key` TEXT NOT NULL, `work_id` TEXT NOT NULL, " +
                "`season` INTEGER NOT NULL, `episode` INTEGER NOT NULL, `title` TEXT, " +
                "`still_path` TEXT, `runtime` INTEGER, `air_date` TEXT, PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_logical_key` ON `episodes` (`logical_key`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_work_id_season_episode` " +
                "ON `episodes` (`work_id`, `season`, `episode`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `movie_versions` (" +
                "`work_id` TEXT NOT NULL, `file_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, " +
                "`quality_label` TEXT, `size_bytes` INTEGER, `pinned` INTEGER NOT NULL, " +
                "PRIMARY KEY(`work_id`, `file_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `episode_versions` (" +
                "`episode_id` TEXT NOT NULL, `file_id` TEXT NOT NULL, `source_id` TEXT NOT NULL, " +
                "`quality_label` TEXT, `size_bytes` INTEGER, `pinned` INTEGER NOT NULL, " +
                "PRIMARY KEY(`episode_id`, `file_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `file_segments` (" +
                "`file_id` TEXT NOT NULL, `episode_id` TEXT NOT NULL, " +
                "`segment_index` INTEGER NOT NULL, `start_ms` INTEGER, `end_ms` INTEGER, " +
                "PRIMARY KEY(`file_id`, `segment_index`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `movie_progress` (" +
                "`work_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `position_ms` INTEGER NOT NULL, " +
                "`duration_ms` INTEGER NOT NULL, `completed` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`work_id`, `user_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `episode_progress` (" +
                "`episode_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `position_ms` INTEGER NOT NULL, " +
                "`duration_ms` INTEGER NOT NULL, `completed` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`episode_id`, `user_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `file_progress` (" +
                "`file_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `position_ms` INTEGER NOT NULL, " +
                "`duration_ms` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`file_id`, `user_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `favorites` (" +
                "`work_id` TEXT NOT NULL, `user_id` TEXT NOT NULL, `added_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`work_id`, `user_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `created_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_items` (" +
                "`playlist_id` INTEGER NOT NULL, `work_id` TEXT NOT NULL, " +
                "`added_at` INTEGER NOT NULL, PRIMARY KEY(`playlist_id`, `work_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `title_overrides` (" +
                "`work_id` TEXT NOT NULL, `title` TEXT NOT NULL, `year` INTEGER, PRIMARY KEY(`work_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `work_details` (" +
                "`work_id` TEXT NOT NULL, `tmdb_id` INTEGER NOT NULL, `release_date` TEXT, " +
                "`runtime_minutes` INTEGER, `genres` TEXT NOT NULL, `country` TEXT, " +
                "`cast_json` TEXT NOT NULL, `fetched_at` INTEGER NOT NULL, " +
                "`total_episodes` INTEGER, `total_seasons` INTEGER, PRIMARY KEY(`work_id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `content_tombstones` (" +
                "`id` TEXT NOT NULL, `merged_into` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `subtitles` (" +
                "`id` TEXT NOT NULL, `logical_id` TEXT, `file_id` TEXT, `language` TEXT, " +
                "`source` TEXT, `path` TEXT, `format` TEXT, `encoding` TEXT, " +
                "`downloaded_at` INTEGER, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `provider_health` (" +
                "`provider` TEXT NOT NULL, `state` TEXT NOT NULL, `last_error` TEXT, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`provider`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scrape_tasks` (" +
                "`id` TEXT NOT NULL, `content_id` TEXT NOT NULL, `content_kind` TEXT NOT NULL, " +
                "`provider` TEXT NOT NULL, `task_kind` TEXT NOT NULL, `state` TEXT NOT NULL, " +
                "`attempts` INTEGER NOT NULL, `next_attempt_at` INTEGER, `priority` INTEGER NOT NULL, " +
                "`last_error` TEXT, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
    }

    private fun seedSources(db: SupportSQLiteDatabase, now: Long) {
        listOf(
            arrayOf<Any?>("webdav", SourceType.WEBDAV.name, "WebDAV", "primary"),
            arrayOf<Any?>("local", SourceType.LOCAL.name, "本地", null),
        ).forEach { row ->
            db.execSQL(
                "INSERT OR IGNORE INTO sources(id, type, name, base_url, root_path, credential_ref, " +
                    "enabled, priority, created_at, last_scan_at, health, root_generation) " +
                    "VALUES(?, ?, ?, NULL, NULL, ?, 1, 0, ?, NULL, NULL, 0)",
                arrayOf(row[0], row[1], row[2], row[3], now),
            )
        }
    }

    private data class WorkSeed(
        val id: String,
        val logicalKey: String,
        val type: String,
        var tmdbId: Int?,
        var title: String,
        var year: Int?,
        var poster: String?,
        var backdrop: String?,
        var overview: String?,
        var rating: Double?,
        var scrapeState: String,
        var scrapedAt: Long?,
    )

    private data class EpisodeSeed(
        val id: String,
        val logicalKey: String,
        val workId: String,
        val season: Int,
        val episode: Int,
        var title: String?,
    )

    private data class ContentRef(val workId: String, val episodeId: String?, val fileId: String)

    private fun backfill(db: SupportSQLiteDatabase, now: Long) {
        val works = LinkedHashMap<String, WorkSeed>()
        val episodes = LinkedHashMap<String, EpisodeSeed>()
        val pathMap = HashMap<String, ContentRef>()
        val expectedProgress = HashSet<String>()
        val expectedFavorites = HashSet<String>()
        val expectedPlaylistItems = HashSet<String>()
        val expectedOverrides = HashSet<String>()
        val expectedEpisodeKeys = HashSet<String>()

        db.query("SELECT * FROM media_items_legacy").use { c ->
            while (c.moveToNext()) {
                val path = c.getString(c.getColumnIndexOrThrow("path"))
                val name = c.getString(c.getColumnIndexOrThrow("name"))
                val size = c.getNullableLong("sizeBytes")
                val lastModified = c.getNullableString("lastModified")
                val parsedTitle = c.getString(c.getColumnIndexOrThrow("parsedTitle"))
                val year = c.getNullableInt("year")
                val season = c.getNullableInt("season")
                val episode = c.getNullableInt("episode")
                val scrapeState = c.getString(c.getColumnIndexOrThrow("scrapeState"))
                val tmdbId = c.getNullableInt("tmdbId")
                val poster = c.getNullableString("posterPath")
                val backdrop = c.getNullableString("backdropPath")
                val overview = c.getNullableString("overview")
                val rating = c.getNullableDouble("rating")
                val scrapedAt = c.getNullableLong("scrapedAt")

                val sourceId = if (path.startsWith("local/") || path.startsWith("content://")) {
                    SourceId.LOCAL
                } else {
                    SourceId.WEBDAV
                }
                val remoteKey = normalizeRemoteKey(path)
                val fileId = ContentIds.file(sourceId, remoteKey)
                val isEpisode = season != null || episode != null
                val matched = tmdbId != null && scrapeState == ScrapeStatus.MATCHED

                val workKey: String
                val episodeKey: String?
                if (isEpisode) {
                    val s = season ?: 0
                    val e = episode ?: 0
                    workKey = if (matched) {
                        LogicalKeyCodec.show(tmdbId!!)
                    } else {
                        LogicalKeyCodec.file(sourceId, "$remoteKey#series")
                    }
                    episodeKey = if (matched) {
                        LogicalKeyCodec.episode(tmdbId!!, s, e)
                    } else {
                        LogicalKeyCodec.file(sourceId, "$remoteKey#episode")
                    }
                } else {
                    workKey = if (matched) {
                        LogicalKeyCodec.movie(tmdbId!!)
                    } else {
                        LogicalKeyCodec.file(sourceId, remoteKey)
                    }
                    episodeKey = null
                }

                val workId = ContentIds.work(workKey)
                works.getOrPut(workId) {
                    WorkSeed(
                        id = workId,
                        logicalKey = workKey,
                        type = if (isEpisode) "tv" else "movie",
                        tmdbId = tmdbId,
                        title = parsedTitle,
                        year = year,
                        poster = poster,
                        backdrop = backdrop,
                        overview = overview,
                        rating = rating,
                        scrapeState = scrapeState,
                        scrapedAt = scrapedAt,
                    )
                }.also { seed ->
                    seed.tmdbId = seed.tmdbId ?: tmdbId
                    seed.year = seed.year ?: year
                    seed.poster = seed.poster ?: poster
                    seed.backdrop = seed.backdrop ?: backdrop
                    seed.overview = seed.overview ?: overview
                    seed.rating = seed.rating ?: rating
                    if (scrapeState == ScrapeStatus.MATCHED) seed.scrapeState = ScrapeStatus.MATCHED
                    if (seed.scrapeState == ScrapeStatus.UNSCRAPED) seed.scrapeState = scrapeState
                    if (scrapedAt != null) seed.scrapedAt = maxOf(seed.scrapedAt ?: 0L, scrapedAt)
                }

                val episodeId = episodeKey?.let { key ->
                    val id = ContentIds.episode(key)
                    episodes.getOrPut(id) {
                        EpisodeSeed(
                            id = id,
                            logicalKey = key,
                            workId = workId,
                            season = season ?: 0,
                            episode = episode ?: 0,
                            title = parsedTitle,
                        )
                    }
                    expectedEpisodeKeys += id
                    id
                }

                val fileValues = ContentValues().apply {
                    put("id", fileId)
                    put("source_id", sourceId.value)
                    put("remote_key", remoteKey)
                    put("legacy_path", path)
                    putNull("provider_item_id")
                    put("media_kind", "main")
                    put("name", name)
                    if (size == null) putNull("size_bytes") else put("size_bytes", size)
                    if (lastModified == null) putNull("mtime") else put("mtime", lastModified)
                    putNull("etag")
                    putNull("scan_run_id")
                    putNull("missing_at")
                    put("parsed_title", parsedTitle)
                    putNullable("parsed_year", year)
                    putNullable("parsed_season", season)
                    putNullable("parsed_episode", episode)
                    put("legacy_compat", 1)
                }
                db.insert("media_files", SQLiteDatabase.CONFLICT_REPLACE, fileValues)

                if (episodeId != null) {
                    db.insert(
                        "episode_versions",
                        SQLiteDatabase.CONFLICT_REPLACE,
                        ContentValues().apply {
                            put("episode_id", episodeId)
                            put("file_id", fileId)
                            put("source_id", sourceId.value)
                            putNull("quality_label")
                            if (size == null) putNull("size_bytes") else put("size_bytes", size)
                            put("pinned", 0)
                        },
                    )
                    db.insert(
                        "file_segments",
                        SQLiteDatabase.CONFLICT_REPLACE,
                        ContentValues().apply {
                            put("file_id", fileId)
                            put("episode_id", episodeId)
                            put("segment_index", 0)
                            put("start_ms", 0L)
                            putNull("end_ms")
                        },
                    )
                } else {
                    db.insert(
                        "movie_versions",
                        SQLiteDatabase.CONFLICT_REPLACE,
                        ContentValues().apply {
                            put("work_id", workId)
                            put("file_id", fileId)
                            put("source_id", sourceId.value)
                            putNull("quality_label")
                            if (size == null) putNull("size_bytes") else put("size_bytes", size)
                            put("pinned", 0)
                        },
                    )
                }
                pathMap[path] = ContentRef(workId, episodeId, fileId)
            }
        }

        works.values.forEach { seed ->
            db.insert(
                "works",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", seed.id)
                    put("logical_key", seed.logicalKey)
                    if (seed.tmdbId == null) putNull("tmdb_id") else put("tmdb_id", seed.tmdbId)
                    put("type", seed.type)
                    put("title", seed.title)
                    putNull("sort_title")
                    if (seed.year == null) putNull("year") else put("year", seed.year)
                    if (seed.poster == null) putNull("poster_path") else put("poster_path", seed.poster)
                    if (seed.backdrop == null) putNull("backdrop_path") else put("backdrop_path", seed.backdrop)
                    if (seed.overview == null) putNull("overview") else put("overview", seed.overview)
                    if (seed.rating == null) putNull("rating") else put("rating", seed.rating)
                    put("added_at", now)
                    putNull("latest_file_mtime")
                    put("updated_at", now)
                    put("scrape_state", seed.scrapeState)
                    if (seed.scrapedAt == null) putNull("scraped_at") else put("scraped_at", seed.scrapedAt)
                },
            )
        }
        episodes.values.forEach { seed ->
            db.insert(
                "episodes",
                SQLiteDatabase.CONFLICT_IGNORE,
                ContentValues().apply {
                    put("id", seed.id)
                    put("logical_key", seed.logicalKey)
                    put("work_id", seed.workId)
                    put("season", seed.season)
                    put("episode", seed.episode)
                    if (seed.title == null) putNull("title") else put("title", seed.title)
                    putNull("still_path")
                    putNull("runtime")
                    putNull("air_date")
                },
            )
        }

        // title_details → work_details（按作品去重，后写覆盖）
        db.query("SELECT * FROM title_details_legacy").use { c ->
            while (c.moveToNext()) {
                val path = c.getString(c.getColumnIndexOrThrow("path"))
                val ref = pathMap[path] ?: continue
                db.insert(
                    "work_details",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("work_id", ref.workId)
                        put("tmdb_id", c.getInt(c.getColumnIndexOrThrow("tmdbId")))
                        putNullable("release_date", c.getNullableString("releaseDate"))
                        putNullable("runtime_minutes", c.getNullableInt("runtimeMinutes"))
                        put("genres", c.getString(c.getColumnIndexOrThrow("genres")))
                        putNullable("country", c.getNullableString("country"))
                        put("cast_json", c.getString(c.getColumnIndexOrThrow("castJson")))
                        put("fetched_at", c.getLong(c.getColumnIndexOrThrow("fetchedAt")))
                        putNullable("total_episodes", c.getNullableInt("totalEpisodes"))
                        putNullable("total_seasons", c.getNullableInt("totalSeasons"))
                    },
                )
            }
        }

        // episode_meta → episodes（保留 episode_meta 表作为详情页缓存）
        db.query(
            "SELECT tmdbId, season, episode, name, stillPath, runtimeMinutes FROM episode_meta",
        ).use { c ->
            while (c.moveToNext()) {
                val workId = ContentIds.work(LogicalKeyCodec.show(c.getInt(0)))
                db.execSQL(
                    "UPDATE episodes SET title = ?, still_path = ?, runtime = ? " +
                        "WHERE work_id = ? AND season = ? AND episode = ?",
                    arrayOf<Any?>(
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getNullableString("stillPath"),
                        c.getNullableInt("runtimeMinutes"),
                        workId,
                        c.getInt(c.getColumnIndexOrThrow("season")),
                        c.getInt(c.getColumnIndexOrThrow("episode")),
                    ),
                )
            }
        }

        // playback_progress → movie/episode progress
        db.query("SELECT path, positionMs, durationMs, updatedAt FROM playback_progress_legacy").use { c ->
            while (c.moveToNext()) {
                val path = c.getString(0)
                val ref = pathMap[path] ?: continue
                val position = c.getLong(1)
                val duration = c.getLong(2)
                val updatedAt = c.getLong(3)
                val completed = if (duration > 0 && position >= duration * 9 / 10) 1 else 0
                if (ref.episodeId != null) {
                    expectedProgress += "ep:${ref.episodeId}"
                    db.insert(
                        "episode_progress",
                        SQLiteDatabase.CONFLICT_REPLACE,
                        ContentValues().apply {
                            put("episode_id", ref.episodeId)
                            put("user_id", DEFAULT_USER_ID)
                            put("position_ms", position)
                            put("duration_ms", duration)
                            put("completed", completed)
                            put("updated_at", updatedAt)
                        },
                    )
                } else {
                    expectedProgress += "mv:${ref.workId}"
                    db.insert(
                        "movie_progress",
                        SQLiteDatabase.CONFLICT_REPLACE,
                        ContentValues().apply {
                            put("work_id", ref.workId)
                            put("user_id", DEFAULT_USER_ID)
                            put("position_ms", position)
                            put("duration_ms", duration)
                            put("completed", completed)
                            put("updated_at", updatedAt)
                        },
                    )
                }
            }
        }

        // playlists（保留 id）
        db.query("SELECT id, name, createdAt FROM playlists_legacy").use { c ->
            while (c.moveToNext()) {
                db.insert(
                    "playlists",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("id", c.getLong(0))
                        put("user_id", DEFAULT_USER_ID)
                        put("name", c.getString(1))
                        put("created_at", c.getLong(2))
                    },
                )
            }
        }

        db.query("SELECT path, addedAt FROM favorites_legacy").use { c ->
            while (c.moveToNext()) {
                val ref = pathMap[c.getString(0)] ?: continue
                expectedFavorites += ref.workId
                db.insert(
                    "favorites",
                    SQLiteDatabase.CONFLICT_IGNORE,
                    ContentValues().apply {
                        put("work_id", ref.workId)
                        put("user_id", DEFAULT_USER_ID)
                        put("added_at", c.getLong(1))
                    },
                )
            }
        }

        db.query("SELECT playlistId, path, addedAt FROM playlist_items_legacy").use { c ->
            while (c.moveToNext()) {
                val ref = pathMap[c.getString(1)] ?: continue
                val key = "${c.getLong(0)}:${ref.workId}"
                expectedPlaylistItems += key
                db.insert(
                    "playlist_items",
                    SQLiteDatabase.CONFLICT_IGNORE,
                    ContentValues().apply {
                        put("playlist_id", c.getLong(0))
                        put("work_id", ref.workId)
                        put("added_at", c.getLong(2))
                    },
                )
            }
        }

        db.query("SELECT path, title, year FROM title_overrides_legacy").use { c ->
            while (c.moveToNext()) {
                val ref = pathMap[c.getString(0)] ?: continue
                expectedOverrides += ref.workId
                db.insert(
                    "title_overrides",
                    SQLiteDatabase.CONFLICT_REPLACE,
                    ContentValues().apply {
                        put("work_id", ref.workId)
                        put("title", c.getString(1))
                        putNullable("year", c.getNullableInt("year"))
                    },
                )
            }
        }

        checkCounts(
            db = db,
            expectedWorks = works.size,
            expectedEpisodes = expectedEpisodeKeys.size,
            expectedProgress = expectedProgress.size,
            expectedFavorites = expectedFavorites.size,
            expectedPlaylistItems = expectedPlaylistItems.size,
            expectedOverrides = expectedOverrides.size,
        )
    }

    private fun createViews(db: SupportSQLiteDatabase) {
        // Room 校验的是视图 SQL 文本本身：注解值会被 trim，这里必须一致。
        db.execSQL("CREATE VIEW `media_items` AS ${ViewSql.MEDIA_ITEMS.trim()}")
        db.execSQL("CREATE VIEW `playback_progress` AS ${ViewSql.PLAYBACK_PROGRESS.trim()}")
        db.execSQL("CREATE VIEW `favorites_view` AS ${ViewSql.FAVORITES.trim()}")
        db.execSQL("CREATE VIEW `playlist_items_view` AS ${ViewSql.PLAYLIST_ITEMS.trim()}")
        db.execSQL("CREATE VIEW `title_overrides_view` AS ${ViewSql.TITLE_OVERRIDES.trim()}")
        db.execSQL("CREATE VIEW `title_details` AS ${ViewSql.TITLE_DETAILS.trim()}")
    }

    private fun createFts(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `media_fts` USING FTS4(" +
                "`work_id`, `search_text`, tokenize=unicode61)",
        )
        db.query("SELECT id, title FROM works").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO media_fts(work_id, search_text) VALUES(?, ?)",
                    arrayOf(c.getString(0), cjkSpace(c.getString(1))),
                )
            }
        }
    }

    /** CJK 字符之间补空格，FTS4 unicode61 才能逐字命中（S2 / ADR-024）。 */
    private fun cjkSpace(text: String): String {
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            val isCjk = ch.code in 0x3400..0x4DBF || ch.code in 0x4E00..0x9FFF
            if (isCjk) sb.append(' ').append(ch).append(' ') else sb.append(ch)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun checkCounts(
        db: SupportSQLiteDatabase,
        expectedWorks: Int,
        expectedEpisodes: Int,
        expectedProgress: Int,
        expectedFavorites: Int,
        expectedPlaylistItems: Int,
        expectedOverrides: Int,
    ) {
        val actualWorks = count(db, "works")
        val actualEpisodes = count(db, "episodes")
        val actualProgress = count(db, "movie_progress") + count(db, "episode_progress") + count(db, "file_progress")
        val actualFavorites = count(db, "favorites")
        val actualPlaylistItems = count(db, "playlist_items")
        val actualOverrides = count(db, "title_overrides")

        val failures = buildList {
            if (actualWorks != expectedWorks) add("works=$actualWorks expected=$expectedWorks")
            if (actualEpisodes != expectedEpisodes) add("episodes=$actualEpisodes expected=$expectedEpisodes")
            if (actualProgress != expectedProgress) add("progress=$actualProgress expected=$expectedProgress")
            if (actualFavorites != expectedFavorites) add("favorites=$actualFavorites expected=$expectedFavorites")
            if (actualPlaylistItems != expectedPlaylistItems) {
                add("playlist_items=$actualPlaylistItems expected=$expectedPlaylistItems")
            }
            if (actualOverrides != expectedOverrides) add("overrides=$actualOverrides expected=$expectedOverrides")
        }
        check(failures.isEmpty()) { "v9 迁移计数校验失败：${failures.joinToString("；")}" }
    }

    /** 逐行比对兼容 VIEW 与 legacy 表的 15 个列。 */
    private fun verifyMediaItemsView(db: SupportSQLiteDatabase) {
        val failures = mutableListOf<String>()
        db.query("SELECT * FROM media_items_legacy").use { legacy ->
            while (legacy.moveToNext()) {
                val path = legacy.getString(legacy.getColumnIndexOrThrow("path"))
                db.query("SELECT * FROM media_items WHERE path = ?", arrayOf(path)).use { view ->
                    if (!view.moveToFirst()) {
                        failures += "$path: VIEW 无此行"
                        return@use
                    }
                    fun compare(col: String) {
                        val expected = legacy.getNullableString(col)
                        val actual = view.getNullableString(col)
                        if (expected != actual) {
                            failures += "$path.$col: legacy=$expected view=$actual"
                        }
                    }
                    listOf(
                        "name", "sizeBytes", "lastModified", "parsedTitle", "year",
                        "season", "episode", "scrapeState", "tmdbId", "posterPath",
                        "backdropPath", "overview", "rating",
                    ).forEach(::compare)
                }
            }
        }
        check(failures.isEmpty()) {
            "v9 迁移 VIEW 校验失败（前 10 条）：${failures.take(10).joinToString("；")}"
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun verify(db: SupportSQLiteDatabase) {
        val legacyCount = count(db, "media_items_legacy")
        val fileCount = count(db, "media_files")
        check(legacyCount == fileCount) {
            "v9 迁移文件数不一致：legacy=$legacyCount media_files=$fileCount"
        }
        val viewCount = count(db, "media_items")
        check(viewCount == fileCount) {
            "v9 迁移 VIEW 行数不一致：media_files=$fileCount view=$viewCount"
        }
    }
}

private fun android.database.Cursor.getNullableString(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

private fun android.database.Cursor.getNullableInt(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
}

private fun android.database.Cursor.getNullableLong(column: String): Long? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getLong(index)
}

private fun android.database.Cursor.getNullableDouble(column: String): Double? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getDouble(index)
}

private fun ContentValues.putNullable(key: String, value: Any?) {
    when (value) {
        null -> putNull(key)
        is String -> put(key, value)
        is Int -> put(key, value)
        is Long -> put(key, value)
        is Double -> put(key, value)
        is Boolean -> put(key, value)
        is ByteArray -> put(key, value)
        else -> put(key, value.toString())
    }
}
