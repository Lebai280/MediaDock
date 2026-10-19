package com.lovelev.mediadock.data.local

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v9 shadow compare（仅 debug）：旧 `media_items_legacy` 与新兼容 VIEW 逐行比对。
 *
 * 只比用户可见列；`scrapedAt` 是作品级派生值，per-file 不一致属预期，不比。
 * 差异写日志，不阻断启动；用于 0a/v10 期间观察读路径是否漂移。
 */
object ShadowCompare {

    private const val TAG = "ShadowCompare"

    fun run(db: MediaDatabase) {
        runCatching {
            val sqlite = db.openHelper.readableDatabase
            val legacyCount = count(sqlite, "media_items_legacy")
            val viewCount = count(sqlite, "media_items")
            if (legacyCount != viewCount) {
                Log.w(TAG, "行数不一致：legacy=$legacyCount view=$viewCount")
            }
            var compared = 0
            var mismatched = 0
            sqlite.query("SELECT * FROM media_items_legacy").use { legacy ->
                while (legacy.moveToNext()) {
                    val path = legacy.getString(legacy.getColumnIndexOrThrow("path"))
                    sqlite.query("SELECT * FROM media_items WHERE path = ?", arrayOf(path)).use { view ->
                        if (!view.moveToFirst()) {
                            mismatched++
                            Log.w(TAG, "$path 在 VIEW 中缺失")
                            return@use
                        }
                        compared++
                        COLUMNS.forEach { col ->
                            val a = legacy.getStringOrNull(col)
                            val b = view.getStringOrNull(col)
                            if (a != b) {
                                mismatched++
                                Log.w(TAG, "$path.$col legacy=$a view=$b")
                            }
                        }
                    }
                }
            }
            Log.i(TAG, "shadow compare 完成：比对 $compared 行，差异 $mismatched 处")
        }.onFailure {
            Log.w(TAG, "shadow compare 失败：${it.message}")
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private val COLUMNS = listOf(
        "name", "sizeBytes", "lastModified", "parsedTitle", "year",
        "season", "episode", "scrapeState", "tmdbId", "posterPath",
        "backdropPath", "overview", "rating",
    )
}
