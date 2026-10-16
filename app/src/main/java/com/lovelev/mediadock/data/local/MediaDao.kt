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
