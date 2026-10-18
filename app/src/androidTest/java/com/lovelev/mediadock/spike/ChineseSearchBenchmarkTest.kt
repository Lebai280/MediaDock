package com.lovelev.mediadock.spike

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * S2 spike: Chinese search at 20k rows on the real device.
 *
 * Compares:
 *  - FTS4 unicode61 over CJK-spaced text, queried as a phrase ("芙 莉 莲")
 *  - FTS5 trigram (if the platform SQLite supports it)
 *  - LIKE '%query%' baseline
 *
 * Test-only; writes a temporary database in cacheDir and deletes it.
 */
@RunWith(AndroidJUnit4::class)
class ChineseSearchBenchmarkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = File(context.cacheDir, "s2_search_benchmark.db")

    private val bases = listOf(
        "葬送的芙莉莲", "四月是你的谎言", "秒速五厘米", "你的名字", "天气之子",
        "铃芽之旅", "辉夜大小姐想让我告白", "进击的巨人", "鬼灭之刃", "咒术回战",
        "间谍过家家", "紫罗兰永恒花园", "未闻花名", "千与千寻", "龙猫",
        "哈尔的移动城堡", "天空之城", "风之谷", "幽灵公主", "红猪",
        "起风了", "声之形", "言叶之庭", "追逐繁星的孩子", "云之彼端约定的地方",
        "某科学的超电磁炮", "命运石之门", "钢之炼金术师", "死亡笔记", "海贼王",
        "火影忍者", "名侦探柯南", "灌篮高手", "排球少年", "黑子的篮球",
        "东京喰种", "一拳超人", "灵能百分百", "心理测量者", "攻壳机动队",
        "星际牛仔", "新世纪福音战士", "高达", "超时空要塞", "fate",
        "原神", "崩坏", "明日方舟", "碧蓝航线", "战双帕弥什",
    )

    @Test
    fun benchmarkChineseSearch() {
        dbFile.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL(
                "CREATE TABLE works(id INTEGER PRIMARY KEY, title TEXT NOT NULL, " +
                    "search_text TEXT NOT NULL)",
            )
            db.execSQL("CREATE INDEX idx_works_title ON works(title)")

            val insertWorks = db.compileStatement(
                "INSERT INTO works(id,title,search_text) VALUES(?,?,?)",
            )

            var fts5Available = false
            try {
                db.execSQL("CREATE VIRTUAL TABLE fts5 USING fts5(search_text, tokenize='trigram')")
                fts5Available = true
            } catch (e: SQLiteException) {
                Log.w(TAG, "FTS5 trigram unavailable: ${e.message}")
            }
            db.execSQL("CREATE VIRTUAL TABLE fts4 USING fts4(search_text, tokenize=unicode61)")
            val insertFts4 = db.compileStatement(
                "INSERT INTO fts4(docid,search_text) VALUES(?,?)",
            )
            val insertFts5 = if (fts5Available) {
                db.compileStatement("INSERT INTO fts5(rowid,search_text) VALUES(?,?)")
            } else {
                null
            }

            db.beginTransaction()
            try {
                for (i in 0 until ROWS) {
                    val base = bases[i % bases.size]
                    val edition = if (i < bases.size) "" else " 第${i / bases.size}季"
                    val title = "$base$edition"
                    val search = spaced("$title 演员${i % 500} 导演${i % 200} 剧情 动画")
                    insertWorks.bindLong(1, i.toLong())
                    insertWorks.bindString(2, title)
                    insertWorks.bindString(3, search)
                    insertWorks.executeInsert()
                    insertFts4.bindLong(1, i.toLong())
                    insertFts4.bindString(2, search)
                    insertFts4.executeInsert()
                    insertFts5?.let {
                        it.bindLong(1, i.toLong())
                        it.bindString(2, search)
                        it.executeInsert()
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            val cases = listOf(
                "葬送的芙莉莲" to 0L,
                "芙莉莲" to 0L,
                "四月" to 1L,
                "秒速五" to 2L,
                "演员42" to null,
                "原神" to 45L,
            )

            for ((query, expected) in cases) {
                val fts4Count = runQuery(db, "SELECT docid FROM fts4 WHERE fts4 MATCH ?",
                    arrayOf(fts4Phrase(query)))
                val likeCount = runQuery(db, "SELECT id FROM works WHERE title LIKE ?",
                    arrayOf("%$query%"))
                Log.i(TAG, "correctness FTS4 '$query': $fts4Count rows")
                Log.i(TAG, "correctness LIKE '$query': $likeCount rows")
                assertTrue("FTS4 should match $query", fts4Count > 0)
                // LIKE baseline only covers title substrings; actor queries use search_text.
                if (query != "演员42") {
                    assertTrue("LIKE should match $query", likeCount > 0)
                }
                if (expected != null) {
                    assertTrue(
                        "FTS4 missing id=$expected for $query",
                        containsId(db, "SELECT docid FROM fts4 WHERE fts4 MATCH ?",
                            arrayOf(fts4Phrase(query)), expected),
                    )
                }
                if (fts5Available && query.length >= 3) {
                    val fts5Count = runQuery(db,
                        "SELECT rowid FROM fts5 WHERE fts5 MATCH ?", arrayOf(query))
                    Log.i(TAG, "correctness FTS5 '$query': $fts5Count rows")
                    assertTrue("FTS5 should match $query", fts5Count > 0)
                }
            }

            val queries = listOf("葬送的芙莉莲", "芙莉莲", "四月", "秒速五", "演员42", "原神")
            val fts4 = measureAll(queries) { q ->
                count(db, "SELECT docid FROM fts4 WHERE fts4 MATCH ?", arrayOf(fts4Phrase(q)))
            }
            val like = measureAll(queries) { q ->
                count(db, "SELECT id FROM works WHERE title LIKE ?", arrayOf("%$q%"))
            }
            val fts5 = if (fts5Available) {
                measureAll(queries.filter { it.length >= 3 }) { q ->
                    count(db, "SELECT rowid FROM fts5 WHERE fts5 MATCH ?", arrayOf(q))
                }
            } else {
                null
            }

            Log.i(TAG, "rows=$ROWS fts5=$fts5Available")
            Log.i(TAG, "FTS4 ${percentiles(fts4)}")
            Log.i(TAG, "LIKE ${percentiles(like)}")
            fts5?.let { Log.i(TAG, "FTS5 ${percentiles(it)}") }

            assertTrue("FTS4 p95 must be <= 300 ms", p95(fts4) <= 300.0)
        } finally {
            db.close()
            Log.i(TAG, "db size=${dbFile.length()} bytes")
            dbFile.delete()
        }
    }

    private fun spaced(text: String): String = buildString {
        var lastSpace = true
        for (c in text) {
            if (isCjk(c)) {
                if (!lastSpace) append(' ')
                append(c)
                append(' ')
                lastSpace = true
            } else {
                append(c)
                lastSpace = c == ' '
            }
        }
    }.replace(Regex("\\s+"), " ").trim()

    private fun isCjk(c: Char): Boolean =
        c.code in 0x3400..0x4DBF || c.code in 0x4E00..0x9FFF

    private fun fts4Phrase(query: String): String = "\"${spaced(query)}\""

    private fun count(db: SQLiteDatabase, sql: String, args: Array<String>): Int =
        db.rawQuery(sql, args).use { cursor ->
            var n = 0
            while (cursor.moveToNext()) n++
            n
        }

    private fun runQuery(db: SQLiteDatabase, sql: String, args: Array<String>): Int =
        count(db, sql, args)

    private fun containsId(
        db: SQLiteDatabase,
        sql: String,
        args: Array<String>,
        id: Long,
    ): Boolean = db.rawQuery(sql, args).use { cursor ->
        while (cursor.moveToNext()) {
            if (cursor.getLong(0) == id) return true
        }
        false
    }

    private fun measureAll(
        queries: List<String>,
        body: (String) -> Int,
    ): LongArray {
        // Warm up once so SQLite page cache / statement preparation is not measured.
        queries.forEach { body(it) }
        val samples = ArrayList<Long>(queries.size * REPEATS)
        repeat(REPEATS) {
            for (q in queries) {
                val start = System.nanoTime()
                body(q)
                samples += System.nanoTime() - start
            }
        }
        return samples.toLongArray()
    }

    private fun p95(samples: LongArray): Double {
        val sorted = samples.sorted()
        val index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index] / 1_000_000.0
    }

    private fun percentiles(samples: LongArray): String {
        val sorted = samples.sorted()
        val p50 = sorted[(sorted.size - 1) / 2] / 1_000_000.0
        val index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
        val p95 = sorted[index] / 1_000_000.0
        return String.format(Locale.US, "p50=%.2f ms p95=%.2f ms", p50, p95)
    }

    private companion object {
        const val TAG = "S2Search"
        const val ROWS = 20_000
        const val REPEATS = 30
    }
}
