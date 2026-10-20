package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * v9 写路径。旧 UI 只读兼容 VIEW；扫描、刮削、清理与用户数据写入都落新表。
 */
@Dao
interface MediaLibraryWriteDao {

    // ---------------------------------------------------------------- 读取/解析

    @Query("SELECT * FROM media_files WHERE legacy_path = :path LIMIT 1")
    suspend fun fileByPath(path: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE source_id = :sourceId AND remote_key = :remoteKey")
    suspend fun fileByKey(sourceId: String, remoteKey: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE source_id = :sourceId")
    suspend fun filesBySource(sourceId: String): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE legacy_path LIKE :prefix || '%'")
    suspend fun filesByPrefix(prefix: String): List<MediaFileEntity>

    /**
     * 按 remote_key 前缀取文件（走唯一索引）。[upper] 由调用方给 `prefix + '￿'`，
     * 用于目录级差异写入时只扫描该目录子树。
     */
    @Query(
        "SELECT * FROM media_files WHERE source_id = :sourceId " +
            "AND remote_key >= :prefix AND remote_key < :upper",
    )
    suspend fun filesInKeyRange(
        sourceId: String,
        prefix: String,
        upper: String,
    ): List<MediaFileEntity>

    @Query("UPDATE media_files SET missing_at = :at WHERE id IN (:ids)")
    suspend fun markMissingByIds(ids: List<String>, at: Long)

    @Query("SELECT COUNT(*) FROM file_segments WHERE file_id = :fileId")
    suspend fun segmentCount(fileId: String): Int

    @Query("DELETE FROM movie_versions WHERE source_id = :sourceId")
    suspend fun deleteMovieVersionsBySource(sourceId: String)

    @Query("DELETE FROM episode_versions WHERE source_id = :sourceId")
    suspend fun deleteEpisodeVersionsBySource(sourceId: String)

    @Query(
        "DELETE FROM file_segments WHERE file_id IN " +
            "(SELECT id FROM media_files WHERE source_id = :sourceId)",
    )
    suspend fun deleteSegmentsBySource(sourceId: String)

    @Query("DELETE FROM media_files WHERE source_id = :sourceId")
    suspend fun deleteFilesBySource(sourceId: String)

    /** 作品当前所属的数据源（多源刮削时用于决定逻辑键是否带源后缀）。 */
    @Query(
        "SELECT source_id FROM movie_versions WHERE work_id = :workId " +
            "UNION SELECT ev.source_id FROM episode_versions ev " +
            "JOIN episodes e ON e.id = ev.episode_id WHERE e.work_id = :workId LIMIT 1",
    )
    suspend fun sourceIdOfWork(workId: String): String?

    @Query("SELECT episode_id FROM episode_versions WHERE file_id = :fileId LIMIT 1")
    suspend fun episodeIdForFile(fileId: String): String?

    @Query("SELECT work_id FROM movie_versions WHERE file_id = :fileId LIMIT 1")
    suspend fun movieWorkIdForFile(fileId: String): String?

    @Query("SELECT work_id FROM episodes WHERE id = :episodeId")
    suspend fun workIdForEpisode(episodeId: String): String?

    @Query("SELECT * FROM works WHERE logical_key = :logicalKey LIMIT 1")
    suspend fun workByLogicalKey(logicalKey: String): WorkEntity?

    @Query(
        "SELECT * FROM works w WHERE w.type = :type AND w.title = :title " +
            "AND w.year IS :year AND w.tmdb_id IS NOT NULL " +
            "AND EXISTS (" +
            "SELECT 1 FROM movie_versions mv WHERE mv.work_id = w.id AND mv.source_id = :sourceId " +
            "UNION SELECT 1 FROM episode_versions ev JOIN episodes e ON e.id = ev.episode_id " +
            "WHERE e.work_id = w.id AND ev.source_id = :sourceId) " +
            "ORDER BY w.added_at LIMIT 1",
    )
    suspend fun matchedWorkByTitle(
        title: String,
        year: Int?,
        type: String,
        sourceId: String,
    ): WorkEntity?

    @Query(
        "SELECT * FROM works w WHERE w.type = :type AND w.title = :title " +
            "AND w.year IS :year " +
            "AND EXISTS (" +
            "SELECT 1 FROM movie_versions mv WHERE mv.work_id = w.id AND mv.source_id = :sourceId " +
            "UNION SELECT 1 FROM episode_versions ev JOIN episodes e ON e.id = ev.episode_id " +
            "WHERE e.work_id = w.id AND ev.source_id = :sourceId) " +
            "ORDER BY w.tmdb_id IS NULL, w.added_at LIMIT 1",
    )
    suspend fun anyWorkByTitle(
        title: String,
        year: Int?,
        type: String,
        sourceId: String,
    ): WorkEntity?

    @Query("SELECT * FROM works WHERE id = :id")
    suspend fun workById(id: String): WorkEntity?

    @Query(
        "SELECT * FROM works WHERE scrape_state != 'MATCHED' ORDER BY added_at DESC",
    )
    suspend fun pendingWorks(): List<WorkEntity>

    @Query("SELECT * FROM episodes WHERE logical_key = :logicalKey LIMIT 1")
    suspend fun episodeByLogicalKey(logicalKey: String): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE work_id = :workId AND season = :season AND episode = :episode")
    suspend fun episodeByNumber(workId: String, season: Int, episode: Int): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE work_id = :workId")
    suspend fun episodesOfWork(workId: String): List<EpisodeEntity>

    // ---------------------------------------------------------------- 扫描写入

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: MediaFileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWorkIgnore(work: WorkEntity)

    @Update
    suspend fun updateWork(work: WorkEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodeIgnore(episode: EpisodeEntity)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovieVersion(version: MovieVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeVersion(version: EpisodeVersionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSegment(segment: FileSegmentEntity)

    @Query("UPDATE media_files SET missing_at = :at WHERE source_id = :sourceId")
    suspend fun markMissingBySource(sourceId: String, at: Long)

    /** FULL 扫描收尾：本轮没见到的文件标缺失（含被删目录）。 */
    @Query(
        "UPDATE media_files SET missing_at = :at WHERE source_id = :sourceId " +
            "AND (scan_run_id IS NULL OR scan_run_id != :runId)",
    )
    suspend fun markMissingNotInRun(sourceId: String, runId: Long, at: Long)

    @Query(
        "UPDATE media_files SET missing_at = :at " +
            "WHERE source_id = :sourceId AND legacy_path LIKE :prefix || '%'",
    )
    suspend fun markMissingByPrefix(sourceId: String, prefix: String, at: Long)

    @Query("UPDATE media_files SET missing_at = NULL WHERE id = :id")
    suspend fun clearMissing(id: String)

    @Query("DELETE FROM movie_versions WHERE file_id = :fileId")
    suspend fun deleteMovieVersionsForFile(fileId: String)

    @Query("DELETE FROM episode_versions WHERE file_id = :fileId")
    suspend fun deleteEpisodeVersionsForFile(fileId: String)

    @Query("DELETE FROM file_segments WHERE file_id = :fileId")
    suspend fun deleteSegmentsForFile(fileId: String)

    /** 清空媒体库：删文件与版本/分段，保留作品、分集与用户数据。 */
    @Query("DELETE FROM movie_versions")
    suspend fun deleteAllMovieVersions()

    @Query("DELETE FROM episode_versions")
    suspend fun deleteAllEpisodeVersions()

    @Query("DELETE FROM file_segments")
    suspend fun deleteAllSegments()

    @Query("DELETE FROM media_files")
    suspend fun deleteAllFiles()

    // ---------------------------------------------------------------- 进度

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMovieProgress(progress: MovieProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeProgress(progress: EpisodeProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFileProgress(progress: FileProgressEntity)

    @Query("DELETE FROM movie_progress WHERE work_id = :workId AND user_id = :userId")
    suspend fun deleteMovieProgress(workId: String, userId: String)

    @Query("DELETE FROM episode_progress WHERE episode_id = :episodeId AND user_id = :userId")
    suspend fun deleteEpisodeProgress(episodeId: String, userId: String)

    @Query("DELETE FROM file_progress WHERE file_id = :fileId AND user_id = :userId")
    suspend fun deleteFileProgress(fileId: String, userId: String)

    @Query("SELECT * FROM movie_progress WHERE work_id = :workId AND user_id = :userId")
    suspend fun movieProgress(workId: String, userId: String): MovieProgressEntity?

    @Query("SELECT * FROM episode_progress WHERE episode_id = :episodeId AND user_id = :userId")
    suspend fun episodeProgress(episodeId: String, userId: String): EpisodeProgressEntity?

    @Query("SELECT * FROM file_progress WHERE file_id = :fileId AND user_id = :userId")
    suspend fun fileProgress(fileId: String, userId: String): FileProgressEntity?

    // ---------------------------------------------------------------- 收藏 / 片单 / 改名 / 详情

    @Query("SELECT COUNT(*) FROM favorites WHERE work_id = :workId AND user_id = :userId")
    suspend fun favoriteCount(workId: String, userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE work_id = :workId AND user_id = :userId")
    suspend fun deleteFavorite(workId: String, userId: String)

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylistItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId AND work_id = :workId")
    suspend fun removePlaylistItem(playlistId: Long, workId: String)

    @Query("DELETE FROM playlist_items WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistItems(playlistId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: TitleOverrideEntity)

    @Query(
        "INSERT OR IGNORE INTO title_overrides(work_id, title, year) " +
            "SELECT :toWorkId, title, year FROM title_overrides WHERE work_id = :fromWorkId",
    )
    suspend fun moveOverride(fromWorkId: String, toWorkId: String)

    @Query("DELETE FROM title_overrides WHERE work_id = :workId")
    suspend fun deleteOverride(workId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkDetail(detail: WorkDetailEntity)

    @Query("SELECT * FROM work_details WHERE work_id = :workId")
    suspend fun workDetail(workId: String): WorkDetailEntity?

    @Query("DELETE FROM work_details WHERE work_id IN (:workIds)")
    suspend fun deleteWorkDetails(workIds: List<String>)

    // ---------------------------------------------------------------- 合并

    @Query(
        """
        INSERT OR IGNORE INTO movie_versions(work_id, file_id, source_id, quality_label, size_bytes, pinned)
        SELECT :toWorkId, file_id, source_id, quality_label, size_bytes, pinned
        FROM movie_versions WHERE work_id = :fromWorkId
        """,
    )
    suspend fun moveMovieVersions(fromWorkId: String, toWorkId: String)

    @Query("DELETE FROM movie_versions WHERE work_id = :workId")
    suspend fun deleteMovieVersionsOfWork(workId: String)

    @Query(
        """
        INSERT OR IGNORE INTO favorites(work_id, user_id, added_at)
        SELECT :toWorkId, user_id, added_at FROM favorites WHERE work_id = :fromWorkId
        """,
    )
    suspend fun moveFavorites(fromWorkId: String, toWorkId: String)

    @Query("DELETE FROM favorites WHERE work_id = :workId")
    suspend fun deleteFavorites(workId: String)

    @Query(
        """
        INSERT OR IGNORE INTO playlist_items(playlist_id, work_id, added_at)
        SELECT playlist_id, :toWorkId, added_at FROM playlist_items WHERE work_id = :fromWorkId
        """,
    )
    suspend fun movePlaylistItems(fromWorkId: String, toWorkId: String)

    @Query("DELETE FROM playlist_items WHERE work_id = :workId")
    suspend fun deletePlaylistItemsOfWork(workId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTombstone(tombstone: ContentTombstoneEntity)

    @Query("UPDATE episodes SET work_id = :toWorkId WHERE id = :episodeId")
    suspend fun moveEpisodeToWork(episodeId: String, toWorkId: String)

    @Query("UPDATE episodes SET logical_key = :logicalKey WHERE id = :episodeId")
    suspend fun updateEpisodeLogicalKey(episodeId: String, logicalKey: String)

    @Query(
        "INSERT OR IGNORE INTO episode_versions(episode_id, file_id, source_id, quality_label, size_bytes, pinned) " +
            "SELECT :toEpisodeId, file_id, source_id, quality_label, size_bytes, pinned " +
            "FROM episode_versions WHERE episode_id = :fromEpisodeId",
    )
    suspend fun moveEpisodeVersions(fromEpisodeId: String, toEpisodeId: String)

    @Query("UPDATE OR IGNORE file_segments SET episode_id = :toEpisodeId WHERE episode_id = :fromEpisodeId")
    suspend fun moveSegments(fromEpisodeId: String, toEpisodeId: String)

    @Query("DELETE FROM episode_versions WHERE episode_id = :episodeId")
    suspend fun deleteEpisodeVersionsOfEpisode(episodeId: String)

    @Query("DELETE FROM file_segments WHERE episode_id = :episodeId")
    suspend fun deleteSegmentsOfEpisode(episodeId: String)

    @Query(
        "INSERT OR IGNORE INTO movie_progress(work_id, user_id, position_ms, duration_ms, completed, updated_at) " +
            "SELECT :toWorkId, user_id, position_ms, duration_ms, completed, updated_at " +
            "FROM movie_progress WHERE work_id = :fromWorkId",
    )
    suspend fun moveMovieProgress(fromWorkId: String, toWorkId: String)

    @Query("DELETE FROM movie_progress WHERE work_id = :workId")
    suspend fun deleteMovieProgressOfWork(workId: String)

    @Query(
        "INSERT OR IGNORE INTO episode_progress(episode_id, user_id, position_ms, duration_ms, completed, updated_at) " +
            "SELECT :toEpisodeId, user_id, position_ms, duration_ms, completed, updated_at " +
            "FROM episode_progress WHERE episode_id = :fromEpisodeId",
    )
    suspend fun moveEpisodeProgress(fromEpisodeId: String, toEpisodeId: String)

    @Query("DELETE FROM episode_progress WHERE episode_id = :episodeId")
    suspend fun deleteEpisodeProgressOf(episodeId: String)
}
