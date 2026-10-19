package com.lovelev.mediadock.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v8 → v9 真迁移测试：脱敏夹具 → 迁移 → 逐项校验。
 *
 * 夹具覆盖：已匹配电影 / 已匹配剧集（两季）/ 未匹配本地文件 / 未匹配媒体库 URI；
 * 进度、收藏（同一剧集两条，应按作品去重）、片单、改名、详情、分集元数据。
 */
@RunWith(AndroidJUnit4::class)
class MigrationV8ToV9Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migratesV8FixtureToV9() {
        helper.createDatabase(DB, 8).use { db -> insertV8Fixture(db) }

        helper.runMigrationsAndValidate(DB, 9, false, MediaDatabase.MIGRATION_8_9).use { db ->
            assertEquals(5, count(db, "media_files"))
            assertEquals(4, count(db, "works"))
            assertEquals(2, count(db, "episodes"))
            assertEquals(3, count(db, "movie_versions"))
            assertEquals(2, count(db, "episode_versions"))
            assertEquals(2, count(db, "file_segments"))
            assertEquals(3, count(db, "movie_progress") + count(db, "episode_progress"))
            // 两条同剧集收藏去重为一个作品收藏 + 一条电影收藏。
            assertEquals(2, count(db, "favorites"))
            assertEquals(1, count(db, "playlists"))
            assertEquals(2, count(db, "playlist_items"))
            assertEquals(1, count(db, "title_overrides"))
            assertEquals(2, count(db, "work_details"))

            // 兼容 VIEW：行数、关键列。
            assertEquals(5, count(db, "media_items"))
            db.query(
                "SELECT parsedTitle, season, episode, tmdbId, scrapeState FROM media_items " +
                    "WHERE path = ?",
                arrayOf("动漫/测试剧/S02/29.简日双语.mp4"),
            ).use { c ->
                assertTrue("视图缺少该行", c.moveToFirst())
                assertEquals("测试剧", c.getString(0))
                assertEquals(2, c.getInt(1))
                assertEquals(29, c.getInt(2))
                assertEquals(200, c.getInt(3))
                assertEquals(ScrapeStatus.MATCHED, c.getString(4))
            }
            // 播放进度视图按 path 还原到两条剧集与一条电影。
            assertEquals(3, count(db, "playback_progress"))
            // 收藏视图把作品展开为全部文件 path：电影 1 + 剧集 2。
            assertEquals(3, count(db, "favorites_view"))
            // 改名与详情按作品展开到剧集的两条 path。
            assertEquals(1, count(db, "title_overrides_view"))
            assertEquals(3, count(db, "title_details"))
            // 片单每个作品一行，代表文件存在。
            assertEquals(2, count(db, "playlist_items_view"))
            // 分集元数据已合并进 episodes。
            db.query("SELECT title FROM episodes WHERE season = 2 AND episode = 29").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("第29集", c.getString(0))
            }
            // FTS 索引随作品建立。
            assertEquals(4, count(db, "media_fts"))
        }
    }

    @Test
    fun failedMigrationLeavesLegacyDataIntact() {
        helper.createDatabase(DB, 8).use { db ->
            insertV8Fixture(db)
            // 人为制造失败：迁移需要的 legacy 表先删掉一张。
            db.execSQL("DROP TABLE playback_progress")
        }

        val threw = runCatching {
            helper.runMigrationsAndValidate(DB, 9, false, MediaDatabase.MIGRATION_8_9)
        }.isFailure
        assertTrue("迁移应失败", threw)

        // 重新以 v8 只读打开：业务表仍在（SQLite 事务已回滚）。
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        android.database.sqlite.SQLiteDatabase.openDatabase(
            context.getDatabasePath(DB).path,
            null,
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            assertEquals(5, count(db, "media_items"))
            assertEquals(3, count(db, "favorites"))
        }
    }

    private fun insertV8Fixture(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val media = listOf(
            arrayOf<Any?>(
                "电影/测试电影.2020.mp4", "测试电影.2020.mp4", 2000L,
                "Tue, 02 Jan 2024 00:00:00 GMT", "测试电影", 2020, null, null,
                "MATCHED", 100, "/p100.jpg", "/b100.jpg", "电影简介", 7.5, 1700000000000L,
            ),
            arrayOf<Any?>(
                "动漫/测试剧/S01/测试剧01.mp4", "测试剧01.mp4", 3000L,
                "Wed, 03 Jan 2024 00:00:00 GMT", "测试剧", 2023, 1, 1,
                "MATCHED", 200, "/p200.jpg", "/b200.jpg", "剧简介", 8.0, 1700000000000L,
            ),
            arrayOf<Any?>(
                "动漫/测试剧/S02/29.简日双语.mp4", "29.简日双语.mp4", 4000L,
                "Thu, 04 Jan 2024 00:00:00 GMT", "测试剧", 2023, 2, 29,
                "MATCHED", 200, "/p200.jpg", "/b200.jpg", "剧简介", 8.0, 1700000000000L,
            ),
            arrayOf<Any?>(
                "local/电影/本地电影.mp4", "本地电影.mp4", 5000L, null,
                "本地电影", 2019, null, null, "UNSCRAPED", null, null, null, null, null, null,
            ),
            arrayOf<Any?>(
                "content://media/external/video/media/123", "手机视频.mp4", 6000L, null,
                "手机视频", null, null, null, "UNSCRAPED", null, null, null, null, null, null,
            ),
        )
        media.forEach {
            db.execSQL(
                "INSERT INTO media_items(path,name,sizeBytes,lastModified,parsedTitle,year,season,episode," +
                    "scrapeState,tmdbId,posterPath,backdropPath,overview,rating,scrapedAt) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                it,
            )
        }
        listOf(
            arrayOf<Any?>("电影/测试电影.2020.mp4", 120000L, 600000L, 1700000010000L),
            arrayOf<Any?>("动漫/测试剧/S01/测试剧01.mp4", 500000L, 600000L, 1700000020000L),
            arrayOf<Any?>("local/电影/本地电影.mp4", 550000L, 600000L, 1700000030000L),
        ).forEach {
            db.execSQL(
                "INSERT INTO playback_progress(path,positionMs,durationMs,updatedAt) VALUES(?,?,?,?)",
                it,
            )
        }
        listOf(
            arrayOf<Any?>("电影/测试电影.2020.mp4", 1700000010000L),
            arrayOf<Any?>("动漫/测试剧/S01/测试剧01.mp4", 1700000020000L),
            arrayOf<Any?>("动漫/测试剧/S02/29.简日双语.mp4", 1700000030000L),
        ).forEach {
            db.execSQL("INSERT INTO favorites(path,addedAt) VALUES(?,?)", it)
        }
        db.execSQL("INSERT INTO playlists(id,name,createdAt) VALUES(1,'我的片单',1700000000000)")
        listOf(
            arrayOf<Any?>(1L, "电影/测试电影.2020.mp4", 1700000010000L),
            arrayOf<Any?>(1L, "动漫/测试剧/S01/测试剧01.mp4", 1700000020000L),
        ).forEach {
            db.execSQL("INSERT INTO playlist_items(playlistId,path,addedAt) VALUES(?,?,?)", it)
        }
        db.execSQL(
            "INSERT INTO title_overrides(path,title,year) VALUES(?,?,?)",
            arrayOf<Any?>("电影/测试电影.2020.mp4", "测试电影改名", 2021),
        )
        listOf(
            arrayOf<Any?>(
                "电影/测试电影.2020.mp4", 100, "2020-01-01", 120, "剧情,喜剧", "中国", "[]",
                1700000000000L, null, null,
            ),
            arrayOf<Any?>(
                "动漫/测试剧/S01/测试剧01.mp4", 200, "2023-09-29", 24, "动画,剧情", "日本", "[]",
                1700000000000L, 38, 2,
            ),
        ).forEach {
            db.execSQL(
                "INSERT INTO title_details(path,tmdbId,releaseDate,runtimeMinutes,genres,country," +
                    "castJson,fetchedAt,totalEpisodes,totalSeasons) VALUES(?,?,?,?,?,?,?,?,?,?)",
                it,
            )
        }
        listOf(
            arrayOf<Any?>(200, 1, 1, "第1集", "/s1.jpg", 24, 1700000000000L),
            arrayOf<Any?>(200, 2, 29, "第29集", "/s29.jpg", 25, 1700000000000L),
        ).forEach {
            db.execSQL(
                "INSERT INTO episode_meta(tmdbId,season,episode,name,stillPath,runtimeMinutes,fetchedAt) " +
                    "VALUES(?,?,?,?,?,?,?)",
                it,
            )
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private fun count(db: android.database.sqlite.SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM `$table`", null).use { it.moveToFirst(); it.getInt(0) }

    private companion object {
        const val DB = "mediadock-v8-v9-test"
    }
}
