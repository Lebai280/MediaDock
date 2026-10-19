package com.lovelev.mediadock.data.local

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.lovelev.mediadock.core.model.SearchText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `media_fts` 的维护：标题、手工改名、类型、演员姓名（S2 / ADR-024）。
 *
 * 索引文本由 [SearchText.index] 生成（CJK 逐字空格），查询由 [SearchText.query] 生成。
 * 索引进度记录在 `provider_health(provider='fts_index')`；内容版本变化时启动后重建一次。
 */
class SearchIndexer(private val db: MediaDatabase) {

    suspend fun refresh(workId: String) = db.withTransaction { refreshLocked(workId) }

    suspend fun refreshLocked(workId: String) {
        val sqlite = db.openHelper.writableDatabase
        ensureTable(sqlite)
        val row = sqlite.query(
            SimpleSQLiteQuery(
                """
                SELECT w.title AS title, o.title AS overrideTitle,
                       wd.genres AS genres, wd.cast_json AS castJson
                FROM works w
                LEFT JOIN title_overrides o ON o.work_id = w.id
                LEFT JOIN work_details wd ON wd.work_id = w.id
                WHERE w.id = ?
                """.trimIndent(),
                arrayOf(workId),
            ),
        ).use { c ->
            if (!c.moveToFirst()) null else IndexRow(
                title = c.getString(0),
                overrideTitle = if (c.isNull(1)) null else c.getString(1),
                genres = if (c.isNull(2)) null else c.getString(2),
                castJson = if (c.isNull(3)) null else c.getString(3),
            )
        } ?: return

        val text = SearchText.index(
            row.title,
            row.overrideTitle,
            row.genres,
            castNames(row.castJson),
        )
        sqlite.execSQL("DELETE FROM media_fts WHERE work_id = ?", arrayOf(workId))
        if (text.isNotBlank()) {
            sqlite.execSQL(
                "INSERT INTO media_fts(work_id, search_text) VALUES(?, ?)",
                arrayOf(workId, text),
            )
        }
    }

    suspend fun delete(workId: String) {
        val sqlite = db.openHelper.writableDatabase
        ensureTable(sqlite)
        sqlite.execSQL(
            "DELETE FROM media_fts WHERE work_id = ?",
            arrayOf(workId),
        )
    }

    /** 全量重建（标题/类型/演员/改名）。一次性操作，按作品逐条写入同一事务。 */
    suspend fun rebuildAll() = db.withTransaction {
        val sqlite = db.openHelper.writableDatabase
        ensureTable(sqlite)
        sqlite.execSQL("DELETE FROM media_fts")
        sqlite.query(
            """
            SELECT w.id AS id, w.title AS title, o.title AS overrideTitle,
                   wd.genres AS genres, wd.cast_json AS castJson
            FROM works w
            LEFT JOIN title_overrides o ON o.work_id = w.id
            LEFT JOIN work_details wd ON wd.work_id = w.id
            """.trimIndent(),
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val text = SearchText.index(
                    c.getString(1),
                    if (c.isNull(2)) null else c.getString(2),
                    if (c.isNull(3)) null else c.getString(3),
                    castNames(if (c.isNull(4)) null else c.getString(4)),
                )
                if (text.isNotBlank()) {
                    sqlite.execSQL(
                        "INSERT INTO media_fts(work_id, search_text) VALUES(?, ?)",
                        arrayOf(id, text),
                    )
                }
            }
        }
        sqlite.execSQL(
            "INSERT OR REPLACE INTO provider_health(provider, state, last_error, updated_at) " +
                "VALUES('fts_index', ?, NULL, ?)",
            arrayOf<Any?>(INDEX_VERSION.toString(), System.currentTimeMillis()),
        )
    }

    /** 启动时调用：索引版本不一致（或不存在）则重建一次。 */
    suspend fun ensureFresh() {
        ensureTable(db.openHelper.writableDatabase)
        val state = db.openHelper.readableDatabase.query(
            SimpleSQLiteQuery("SELECT state FROM provider_health WHERE provider = 'fts_index'"),
        ).use { c -> if (c.moveToFirst()) c.getString(0) else null }
        if (state != INDEX_VERSION.toString()) rebuildAll()
    }

    /** 全新安装不会走 v8→v9 迁移；首次写入前确保 FTS 表存在。 */
    private fun ensureTable(sqlite: androidx.sqlite.db.SupportSQLiteDatabase) {
        if (tableReady) return
        sqlite.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `media_fts` USING FTS4(" +
                "`work_id`, `search_text`, tokenize=unicode61)",
        )
        tableReady = true
    }

    @Volatile
    private var tableReady = false

    private fun castNames(castJson: String?): String? {
        if (castJson.isNullOrBlank()) return null
        return runCatching {
            Json.parseToJsonElement(castJson).jsonArray
                .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .joinToString(" ")
        }.getOrNull()
    }

    private data class IndexRow(
        val title: String,
        val overrideTitle: String?,
        val genres: String?,
        val castJson: String?,
    )

    private companion object {
        /** 内容结构变化时 +1，触发启动后重建。 */
        const val INDEX_VERSION = 2
    }
}
