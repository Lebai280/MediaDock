package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    /** 按路径排序：同一部片子/同一季的文件会自然排在一起（路径含目录名）。 */
    @Query("SELECT * FROM media_items ORDER BY path")
    fun observeAll(): Flow<List<MediaItemEntity>>

    /** 扫描时用来把上一次的刮削结果按路径接回去（见 LibraryRepository.keepScraped）。 */
    @Query("SELECT * FROM media_items")
    suspend fun allItems(): List<MediaItemEntity>

    /** 待刮削：没刮过的 + 上次没匹配上的（用户再点一次就当重试）。 */
    @Query("SELECT * FROM media_items WHERE scrapeState != 'MATCHED' ORDER BY path")
    suspend fun pendingScrape(): List<MediaItemEntity>

    /** 详情页：只取这一个合集/这部剧的条目。空列表要由调用方挡掉 —— SQLite 的 `IN ()` 是语法错。 */
    @Query("SELECT * FROM media_items WHERE path IN (:paths)")
    fun observeByPaths(paths: List<String>): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM media_items WHERE scrapeState = 'MATCHED'")
    fun observeMatchedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Query("DELETE FROM media_items")
    suspend fun deleteAll()

    /**
     * 只删本地那一半（SAF 条目 + 系统媒体库条目）。
     *
     * 库里同时装三种来源（见 [LocalMedia]），扫描是「整批替换」，所以**不能再用 [deleteAll]** ——
     * 那会把别的来源也扫掉。两个 LIKE 模式由 [LocalMedia.SAF_LIKE] / [LocalMedia.CONTENT_LIKE] 给，
     * 都不含 SQL 通配符，直接拼安全。
     */
    @Query("DELETE FROM media_items WHERE path LIKE :safLike OR path LIKE :contentLike")
    suspend fun deleteLocalItems(safLike: String, contentLike: String)

    /** WebDAV 那一半：既不是 SAF 也不是媒体库的。 */
    @Query("DELETE FROM media_items WHERE path NOT LIKE :safLike AND path NOT LIKE :contentLike")
    suspend fun deleteWebDavItems(safLike: String, contentLike: String)

    @Query(
        """
        UPDATE media_items SET
            scrapeState = :state, tmdbId = :tmdbId, posterPath = :posterPath,
            backdropPath = :backdropPath, overview = :overview, rating = :rating,
            scrapedAt = :scrapedAt
        WHERE path = :path
        """,
    )
    suspend fun markScraped(
        path: String,
        state: String,
        tmdbId: Int?,
        posterPath: String?,
        backdropPath: String?,
        overview: String?,
        rating: Double?,
        scrapedAt: Long,
    )
}
