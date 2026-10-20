package com.lovelev.mediadock.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * v9 数据层实体。列名按 ARCHITECTURE 15.2 使用 snake_case；
 * 旧 UI 读的 `media_items` 等由 [Views.kt] 的兼容 VIEW 投影，列名仍是旧的 camelCase。
 */

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "base_url") val baseUrl: String?,
    @ColumnInfo(name = "root_path") val rootPath: String?,
    @ColumnInfo(name = "credential_ref") val credentialRef: String?,
    @ColumnInfo(name = "enabled") val enabled: Int = 1,
    @ColumnInfo(name = "priority") val priority: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long? = null,
    @ColumnInfo(name = "health") val health: String? = null,
    @ColumnInfo(name = "root_generation") val rootGeneration: Long = 0,
)

@Entity(
    tableName = "media_files",
    indices = [Index(value = ["source_id", "remote_key"], unique = true)],
)
data class MediaFileEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_key") val remoteKey: String,
    @ColumnInfo(name = "legacy_path") val legacyPath: String?,
    @ColumnInfo(name = "provider_item_id") val providerItemId: String? = null,
    @ColumnInfo(name = "media_kind") val mediaKind: String = "main",
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,
    @ColumnInfo(name = "mtime") val mtime: String? = null,
    @ColumnInfo(name = "etag") val etag: String? = null,
    @ColumnInfo(name = "scan_run_id") val scanRunId: Long? = null,
    @ColumnInfo(name = "missing_at") val missingAt: Long? = null,
    // 扫描时对文件名解析出的展示值。旧 UI 按文件显示，不能拿作品级字段替代。
    @ColumnInfo(name = "parsed_title") val parsedTitle: String? = null,
    @ColumnInfo(name = "parsed_year") val parsedYear: Int? = null,
    @ColumnInfo(name = "parsed_season") val parsedSeason: Int? = null,
    @ColumnInfo(name = "parsed_episode") val parsedEpisode: Int? = null,
    /** 1 = 从 v8 迁移而来，兼容 VIEW 必须原样还原（包括 null）。 */
    @ColumnInfo(name = "legacy_compat") val legacyCompat: Int = 0,
)

@Entity(
    tableName = "works",
    indices = [
        Index(value = ["logical_key"], unique = true),
        // 首页「最近」分页按 (added_at, id) 直接走索引，避免对 5k 作品整表排序
        Index(value = ["added_at", "id"]),
    ],
)
data class WorkEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "logical_key") val logicalKey: String,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int? = null,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "sort_title") val sortTitle: String? = null,
    @ColumnInfo(name = "year") val year: Int? = null,
    @ColumnInfo(name = "poster_path") val posterPath: String? = null,
    @ColumnInfo(name = "backdrop_path") val backdropPath: String? = null,
    @ColumnInfo(name = "overview") val overview: String? = null,
    @ColumnInfo(name = "rating") val rating: Double? = null,
    @ColumnInfo(name = "total_seasons") val totalSeasons: Int? = null,
    @ColumnInfo(name = "total_episodes") val totalEpisodes: Int? = null,
    @ColumnInfo(name = "collection_id") val collectionId: String? = null,
    @ColumnInfo(name = "metadata_source") val metadataSource: String? = null,
    @ColumnInfo(name = "external_ids_json") val externalIdsJson: String? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "latest_file_mtime") val latestFileMtime: Long? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long? = null,
    @ColumnInfo(name = "scrape_state") val scrapeState: String = ScrapeStatus.UNSCRAPED,
    @ColumnInfo(name = "scraped_at") val scrapedAt: Long? = null,
)

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["logical_key"], unique = true),
        Index(value = ["work_id", "season", "episode"], unique = true),
    ],
)
data class EpisodeEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "logical_key") val logicalKey: String,
    @ColumnInfo(name = "work_id") val workId: String,
    @ColumnInfo(name = "season") val season: Int,
    @ColumnInfo(name = "episode") val episode: Int,
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "still_path") val stillPath: String? = null,
    @ColumnInfo(name = "runtime") val runtime: Int? = null,
    @ColumnInfo(name = "air_date") val airDate: String? = null,
)

@Entity(tableName = "movie_versions", primaryKeys = ["work_id", "file_id"])
data class MovieVersionEntity(
    @ColumnInfo(name = "work_id") val workId: String,
    @ColumnInfo(name = "file_id") val fileId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "quality_label") val qualityLabel: String? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,
    @ColumnInfo(name = "pinned") val pinned: Int = 0,
)

@Entity(tableName = "episode_versions", primaryKeys = ["episode_id", "file_id"])
data class EpisodeVersionEntity(
    @ColumnInfo(name = "episode_id") val episodeId: String,
    @ColumnInfo(name = "file_id") val fileId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "quality_label") val qualityLabel: String? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,
    @ColumnInfo(name = "pinned") val pinned: Int = 0,
)

@Entity(tableName = "file_segments", primaryKeys = ["file_id", "segment_index"])
data class FileSegmentEntity(
    @ColumnInfo(name = "file_id") val fileId: String,
    @ColumnInfo(name = "episode_id") val episodeId: String,
    @ColumnInfo(name = "segment_index") val segmentIndex: Int,
    @ColumnInfo(name = "start_ms") val startMs: Long? = null,
    @ColumnInfo(name = "end_ms") val endMs: Long? = null,
)

@Entity(tableName = "movie_progress", primaryKeys = ["work_id", "user_id"])
data class MovieProgressEntity(
    @ColumnInfo(name = "work_id") val workId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "completed") val completed: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "episode_progress", primaryKeys = ["episode_id", "user_id"])
data class EpisodeProgressEntity(
    @ColumnInfo(name = "episode_id") val episodeId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "completed") val completed: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "file_progress", primaryKeys = ["file_id", "user_id"])
data class FileProgressEntity(
    @ColumnInfo(name = "file_id") val fileId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(tableName = "favorites", primaryKeys = ["work_id", "user_id"])
data class FavoriteEntity(
    @ColumnInfo(name = "work_id") val workId: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "playlist_items", primaryKeys = ["playlist_id", "work_id"])
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    @ColumnInfo(name = "work_id") val workId: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "title_overrides")
data class TitleOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "work_id") val workId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "year") val year: Int? = null,
)

@Entity(tableName = "work_details")
data class WorkDetailEntity(
    @PrimaryKey @ColumnInfo(name = "work_id") val workId: String,
    @ColumnInfo(name = "tmdb_id") val tmdbId: Int,
    @ColumnInfo(name = "release_date") val releaseDate: String? = null,
    @ColumnInfo(name = "runtime_minutes") val runtimeMinutes: Int? = null,
    @ColumnInfo(name = "genres") val genres: String,
    @ColumnInfo(name = "country") val country: String? = null,
    @ColumnInfo(name = "cast_json") val castJson: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "total_episodes") val totalEpisodes: Int? = null,
    @ColumnInfo(name = "total_seasons") val totalSeasons: Int? = null,
)

@Entity(tableName = "content_tombstones")
data class ContentTombstoneEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "merged_into") val mergedInto: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "subtitles")
data class SubtitleEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "logical_id") val logicalId: String?,
    @ColumnInfo(name = "file_id") val fileId: String?,
    @ColumnInfo(name = "language") val language: String?,
    @ColumnInfo(name = "source") val source: String?,
    @ColumnInfo(name = "path") val path: String?,
    @ColumnInfo(name = "format") val format: String?,
    @ColumnInfo(name = "encoding") val encoding: String?,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long?,
)

@Entity(tableName = "provider_health")
data class ProviderHealthEntity(
    @PrimaryKey @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "scrape_tasks",
    indices = [
        Index(
            value = ["content_id", "content_kind", "provider", "task_kind"],
            unique = true,
        ),
    ],
)
data class ScrapeTaskEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "content_id") val contentId: String,
    @ColumnInfo(name = "content_kind") val contentKind: String,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "task_kind") val taskKind: String,
    @ColumnInfo(name = "state") val state: String,
    @ColumnInfo(name = "attempts") val attempts: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    @ColumnInfo(name = "priority") val priority: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
