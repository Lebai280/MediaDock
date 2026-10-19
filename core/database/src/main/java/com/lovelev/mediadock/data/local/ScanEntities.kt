package com.lovelev.mediadock.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一次扫描代际。进程死亡后按 status=RUNNING + scan_queue 续跑。 */
@Entity(tableName = "scan_runs")
data class ScanRunEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long? = null,
    @ColumnInfo(name = "dirs") val dirs: Int = 0,
    @ColumnInfo(name = "files") val files: Int = 0,
    @ColumnInfo(name = "error") val error: String? = null,
)

object ScanMode {
    const val INCREMENTAL = "INCREMENTAL"
    const val FULL = "FULL"
}

object ScanStatus {
    const val RUNNING = "RUNNING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
    const val CANCELLED = "CANCELLED"
    const val TRUNCATED = "TRUNCATED"
    const val BLOCKED = "BLOCKED"
}

/** 目录缓存：ETag / Last-Modified / 子项数，用于增量扫描跳过未变化子树。 */
@Entity(
    tableName = "directory_cache",
    primaryKeys = ["source_id", "remote_key"],
    indices = [Index(value = ["source_id", "last_scan_run_id"])],
)
data class DirectoryCacheEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "remote_key") val remoteKey: String,
    @ColumnInfo(name = "etag") val etag: String? = null,
    @ColumnInfo(name = "last_modified") val lastModified: String? = null,
    @ColumnInfo(name = "last_scan_run_id") val lastScanRunId: Long? = null,
    @ColumnInfo(name = "child_count") val childCount: Int = 0,
)

/** 持久 BFS 队列；按 scan_run_id 区分代际，旧代际不消费。 */
@Entity(tableName = "scan_queue", primaryKeys = ["source_id", "remote_key"])
data class ScanQueueEntity(
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "scan_run_id") val scanRunId: Long,
    @ColumnInfo(name = "remote_key") val remoteKey: String,
    @ColumnInfo(name = "depth") val depth: Int,
)
