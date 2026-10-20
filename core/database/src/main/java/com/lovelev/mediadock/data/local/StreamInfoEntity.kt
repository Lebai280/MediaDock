package com.lovelev.mediadock.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** StreamInfo 的持久缓存：键为 path，payload 是 app 层的序列化 JSON。 */
@Entity(tableName = "stream_info_cache")
data class StreamInfoEntity(
    @PrimaryKey @ColumnInfo(name = "path") val path: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "probed_at") val probedAt: Long,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
)

@Dao
interface StreamInfoDao {
    @Query("SELECT * FROM stream_info_cache WHERE path = :path")
    suspend fun byPath(path: String): StreamInfoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StreamInfoEntity)

    @Query("DELETE FROM stream_info_cache WHERE path = :path")
    suspend fun delete(path: String)

    @Query("DELETE FROM stream_info_cache")
    suspend fun clear()
}
