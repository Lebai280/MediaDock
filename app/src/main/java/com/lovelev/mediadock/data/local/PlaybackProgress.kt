package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 播放进度。**刻意单独一张表，不挂在 [MediaItemEntity] 上** ——
 * 每次扫描都是「整表替换」，进度要是写在媒体条目里，每扫一次全丢。
 * 另外从浏览页直接播放、没进过媒体库的文件也有进度，挂在媒体条目上根本存不下。
 */
@Entity(tableName = "playback_progress")
data class PlaybackProgress(
    @PrimaryKey val path: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PlaybackProgress)

    @Query("SELECT * FROM playback_progress WHERE path = :path")
    suspend fun find(path: String): PlaybackProgress?

    @Query("DELETE FROM playback_progress WHERE path = :path")
    suspend fun remove(path: String)

    /** 海报墙用：一次拿全，界面自己拼成 map。 */
    @Query("SELECT * FROM playback_progress")
    fun observeAll(): Flow<List<PlaybackProgress>>

    /** 播放历史：最近看过的排前面。 */
    @Query("SELECT * FROM playback_progress ORDER BY updatedAt DESC")
    fun observeRecent(): Flow<List<PlaybackProgress>>
}
