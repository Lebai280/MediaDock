package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** 扫描代际、目录缓存与持久队列。所有方法都应在扫描事务/串行流程内调用。 */
@Dao
interface ScanDao {

    @Insert
    suspend fun insertRun(run: ScanRunEntity): Long

    @Query("SELECT * FROM scan_runs WHERE id = :id")
    suspend fun run(id: Long): ScanRunEntity?

    @Query(
        "SELECT * FROM scan_runs WHERE source_id = :sourceId AND status = 'RUNNING' " +
            "ORDER BY id DESC LIMIT 1",
    )
    suspend fun activeRun(sourceId: String): ScanRunEntity?

    @Query(
        "UPDATE scan_runs SET status = :status, finished_at = :finishedAt, " +
            "dirs = :dirs, files = :files, error = :error WHERE id = :id",
    )
    suspend fun finishRun(
        id: Long,
        status: String,
        finishedAt: Long,
        dirs: Int,
        files: Int,
        error: String?,
    )

    @Query("UPDATE scan_runs SET dirs = :dirs, files = :files WHERE id = :id")
    suspend fun updateProgress(id: Long, dirs: Int, files: Int)

    @Query("SELECT * FROM scan_runs WHERE source_id = :sourceId ORDER BY id DESC LIMIT :limit")
    suspend fun recentRuns(sourceId: String, limit: Int = 10): List<ScanRunEntity>

    @Query("SELECT * FROM directory_cache WHERE source_id = :sourceId AND remote_key = :remoteKey")
    suspend fun directory(sourceId: String, remoteKey: String): DirectoryCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDirectory(entry: DirectoryCacheEntity)

    @Query("DELETE FROM directory_cache WHERE source_id = :sourceId")
    suspend fun clearDirectories(sourceId: String)

    @Query("DELETE FROM scan_queue WHERE source_id = :sourceId")
    suspend fun clearQueue(sourceId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entry: ScanQueueEntity)

    @Query(
        "SELECT * FROM scan_queue WHERE source_id = :sourceId AND scan_run_id = :runId " +
            "ORDER BY depth ASC, remote_key ASC LIMIT 1",
    )
    suspend fun nextQueued(sourceId: String, runId: Long): ScanQueueEntity?

    @Query("DELETE FROM scan_queue WHERE source_id = :sourceId AND remote_key = :remoteKey")
    suspend fun dequeue(sourceId: String, remoteKey: String)

    @Query("SELECT COUNT(*) FROM scan_queue WHERE source_id = :sourceId AND scan_run_id = :runId")
    suspend fun queuedCount(sourceId: String, runId: Long): Int

    @Query("UPDATE sources SET health = :health, last_scan_at = :at WHERE id = :sourceId")
    suspend fun updateSourceHealth(sourceId: String, health: String, at: Long)

    @Query("UPDATE sources SET health = :health WHERE id = :sourceId")
    suspend fun setSourceHealth(sourceId: String, health: String)
}
