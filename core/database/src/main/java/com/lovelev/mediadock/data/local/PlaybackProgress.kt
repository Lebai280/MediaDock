package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 播放进度 —— **v9 起是只读兼容 VIEW**：按内容（电影/分集/多集文件）存的进度
 * 投影回文件 path，现有海报墙、播放历史按 path 读的逻辑不变。
 * 写入走 [MediaLibraryWriteDao]。
 */
@DatabaseView(viewName = "playback_progress", value = ViewSql.PLAYBACK_PROGRESS)
data class PlaybackProgress(
    val path: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
)

@Dao
interface ProgressDao {

    @Query("SELECT * FROM playback_progress WHERE path = :path")
    suspend fun find(path: String): PlaybackProgress?

    /** 海报墙用：一次拿全，界面自己拼成 map。 */
    @Query("SELECT * FROM playback_progress")
    fun observeAll(): Flow<List<PlaybackProgress>>

    /** 播放历史：最近看过的排前面。 */
    @Query("SELECT * FROM playback_progress ORDER BY updatedAt DESC")
    fun observeRecent(): Flow<List<PlaybackProgress>>
}
