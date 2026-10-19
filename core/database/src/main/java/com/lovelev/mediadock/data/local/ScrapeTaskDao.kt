package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/** 持久刮削队列。Worker 只领取到期的 PENDING 任务；失败按 next_attempt_at 退避。 */
@Dao
interface ScrapeTaskDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(task: ScrapeTaskEntity)

    @Update
    suspend fun update(task: ScrapeTaskEntity)

    @Query("SELECT * FROM scrape_tasks WHERE id = :id")
    suspend fun task(id: String): ScrapeTaskEntity?

    @Query(
        "SELECT * FROM scrape_tasks WHERE state = 'PENDING' " +
            "AND (next_attempt_at IS NULL OR next_attempt_at <= :now) " +
            "ORDER BY priority DESC, next_attempt_at ASC, updated_at ASC LIMIT :limit",
    )
    suspend fun dueTasks(now: Long, limit: Int): List<ScrapeTaskEntity>

    @Query(
        "SELECT COUNT(*) FROM scrape_tasks WHERE state = 'PENDING' " +
            "AND (next_attempt_at IS NULL OR next_attempt_at <= :now)",
    )
    suspend fun countDue(now: Long): Int

    @Query("SELECT COUNT(*) FROM scrape_tasks WHERE state = 'PENDING'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM scrape_tasks WHERE state = :state")
    suspend fun countInState(state: String): Int

    @Query("UPDATE scrape_tasks SET state = 'PENDING', attempts = 0, next_attempt_at = 0 WHERE state = 'RUNNING'")
    suspend fun resetRunning()

    @Query("DELETE FROM scrape_tasks WHERE content_id = :workId")
    suspend fun deleteForWork(workId: String)

    @Query("SELECT * FROM scrape_tasks")
    suspend fun all(): List<ScrapeTaskEntity>
}

object ScrapeTaskState {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}
