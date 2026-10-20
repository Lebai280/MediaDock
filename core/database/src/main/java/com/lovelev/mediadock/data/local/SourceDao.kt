package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** `sources` 表的最小 DAO：注册表的持久镜像 + 删除影响统计。 */
@Dao
interface SourceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(source: SourceEntity)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM sources ORDER BY priority, created_at")
    suspend fun all(): List<SourceEntity>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: String): SourceEntity?

    @Query("DELETE FROM directory_cache WHERE source_id = :id")
    suspend fun clearDirectoryCache(id: String)

    @Query("DELETE FROM scan_queue WHERE source_id = :id")
    suspend fun clearScanQueue(id: String)

    /** 该源当前可见（未标记缺失）的文件数，删除前给用户看影响。 */
    @Query(
        "SELECT COUNT(*) FROM media_files " +
            "WHERE source_id = :id AND missing_at IS NULL",
    )
    suspend fun visibleFileCount(id: String): Int
}
