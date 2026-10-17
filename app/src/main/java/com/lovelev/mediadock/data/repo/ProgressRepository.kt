package com.lovelev.mediadock.data.repo

import androidx.room.withTransaction
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.PlaybackProgress
import kotlinx.coroutines.flow.Flow

/**
 * 播放进度：记到哪、从哪续、海报墙上的那条进度条。
 *
 * 写入是**节流**的（见 [PlaybackService][com.lovelev.mediadock.player.PlaybackService]
 * 里的 5 秒计时器 + 暂停时补一次），不是每一帧都写库。
 */
class ProgressRepository(private val db: MediaDatabase) {

    fun observeAll(): Flow<List<PlaybackProgress>> = db.progressDao().observeAll()

    /** 播放历史用：按最近更新倒序。 */
    fun observeRecent(): Flow<List<PlaybackProgress>> = db.progressDao().observeRecent()

    suspend fun find(path: String): PlaybackProgress? = db.progressDao().find(path)

    suspend fun save(path: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        db.withTransaction {
            db.progressDao().upsert(
                PlaybackProgress(
                    path = path,
                    positionMs = positionMs.coerceIn(0L, durationMs),
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /**
     * 续播点。**快看完的不算** —— 停在片尾 30 秒内，下次从头放，否则每次都从片尾开始。
     */
    suspend fun resumePositionMs(path: String): Long? {
        val p = find(path) ?: return null
        if (p.durationMs <= 0L) return null
        if (p.positionMs <= 0L) return null
        if (p.durationMs - p.positionMs <= END_TOLERANCE_MS) return null
        return p.positionMs
    }

    /** 标记为已看：直接把进度写到片尾（海报墙的进度条会显示满格，续播也不再从中间开始）。 */
    suspend fun markWatched(path: String, durationMs: Long) {
        if (durationMs <= 0L) return
        save(path, positionMs = durationMs, durationMs = durationMs)
    }

    /** 标记为未看：删掉这一条进度。 */
    suspend fun clear(path: String) {
        db.withTransaction { db.progressDao().remove(path) }
    }

    private companion object {
        const val END_TOLERANCE_MS = 30_000L
    }
}
