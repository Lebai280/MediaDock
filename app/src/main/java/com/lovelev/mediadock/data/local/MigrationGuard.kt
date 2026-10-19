package com.lovelev.mediadock.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * v8 → v9 升级守卫（B6）：在 Room 打开库之前做一次自动备份，并写迁移状态文件。
 *
 * - 备份在 `filesDir/migration/`，含 WAL 检查点后的主库；
 * - 状态文件记录起点/终点版本、备份路径、时间与结果，便于诊断与回滚；
 * - 迁移本身仍在 Room 的事务里，失败会回滚；备份是第二道保险。
 */
class MigrationGuard(private val context: Context) {

    private val dir: File get() = File(context.filesDir, "migration").apply { mkdirs() }
    private val stateFile: File get() = File(dir, "v9_state.json")
    private val dbFile: File get() = context.getDatabasePath("library.db")

    /** 若当前库版本低于 [target]，先备份。重复调用安全。 */
    fun prepareIfNeeded(target: Int) {
        if (!dbFile.exists()) return
        val current = readUserVersion() ?: return
        if (current >= target) return
        runCatching {
            checkpointWal()
            val stamp = System.currentTimeMillis()
            val base = "library-v$current-$stamp"
            val backup = File(dir, "$base.db")
            dbFile.copyTo(backup, overwrite = false)
            listOf("-wal", "-shm").forEach { suffix ->
                val sidecar = File(dbFile.absolutePath + suffix)
                if (sidecar.exists()) sidecar.copyTo(File(dir, base + suffix), overwrite = true)
            }
            writeState(
                JSONObject()
                    .put("from", current)
                    .put("to", target)
                    .put("started_at", stamp)
                    .put("backup", backup.absolutePath)
                    .put("status", "started"),
            )
            Log.i(TAG, "已备份 v$current 数据库：${backup.absolutePath}")
        }.onFailure {
            Log.e(TAG, "升级前备份失败：${it.message}", it)
        }
    }

    fun markSuccess(target: Int) {
        val state = readState() ?: JSONObject()
        state.put("to", target)
        state.put("finished_at", System.currentTimeMillis())
        state.put("status", "success")
        writeState(state)
    }

    /** 迁移失败时可人工调用：恢复最近一次备份并删除坏库。返回是否恢复成功。 */
    fun restoreLatest(): Boolean {
        val backup = dir.listFiles { f -> f.name.startsWith("library-v") && f.name.endsWith(".db") }
            ?.maxByOrNull { it.lastModified() }
            ?: return false
        return runCatching {
            listOf("", "-wal", "-shm").forEach { suffix ->
                File(dbFile.absolutePath + suffix).delete()
            }
            backup.copyTo(dbFile, overwrite = true)
            writeState(
                (readState() ?: JSONObject())
                    .put("status", "restored")
                    .put("restored_at", System.currentTimeMillis())
                    .put("restored_from", backup.absolutePath),
            )
            Log.w(TAG, "已恢复备份：${backup.absolutePath}")
            true
        }.getOrDefault(false)
    }

    private fun readUserVersion(): Int? = runCatching {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else null
            }
        }
    }.getOrNull()

    private fun checkpointWal() {
        runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
            }
        }
    }

    private fun readState(): JSONObject? = runCatching {
        if (stateFile.exists()) JSONObject(stateFile.readText()) else null
    }.getOrNull()

    private fun writeState(state: JSONObject) {
        runCatching { stateFile.writeText(state.toString(2)) }
    }

    private companion object {
        const val TAG = "MigrationGuard"
    }
}
