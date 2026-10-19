package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 媒体库读模型。v9 起 `media_items` 是兼容 VIEW，**这里只保留读查询**；
 * 写路径（扫描/刮削/删除）走 [MediaLibraryWriteDao]。
 */
@Dao
interface MediaDao {

    /** 按路径排序：同一部片子/同一季的文件会自然排在一起（路径含目录名）。 */
    @Query("SELECT * FROM media_items ORDER BY path")
    fun observeAll(): Flow<List<MediaItemEntity>>

    /** 扫描时用来把上一次的刮削结果按路径接回去。 */
    @Query("SELECT * FROM media_items")
    suspend fun allItems(): List<MediaItemEntity>

    /** 待刮削：没刮过的 + 上次没匹配上的（用户再点一次就当重试）。 */
    @Query("SELECT * FROM media_items WHERE scrapeState != 'MATCHED' ORDER BY path")
    suspend fun pendingScrape(): List<MediaItemEntity>

    /** 扫描后自动刮削只处理新条目，不自动重试历史失败项。 */
    @Query("SELECT COUNT(*) FROM media_items WHERE scrapeState = 'UNSCRAPED'")
    suspend fun unscrapedCount(): Int

    /** 详情页：只取这一个合集/这部剧的条目。空列表要由调用方挡掉 —— SQLite 的 `IN ()` 是语法错。 */
    @Query("SELECT * FROM media_items WHERE path IN (:paths)")
    fun observeByPaths(paths: List<String>): Flow<List<MediaItemEntity>>

    @Query("SELECT COUNT(*) FROM media_items WHERE scrapeState = 'MATCHED'")
    fun observeMatchedCount(): Flow<Int>
}
