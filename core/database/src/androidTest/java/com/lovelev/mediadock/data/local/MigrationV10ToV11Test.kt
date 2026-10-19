package com.lovelev.mediadock.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v10 → v11：刮削任务唯一索引。 */
@RunWith(AndroidJUnit4::class)
class MigrationV10ToV11Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun addsScrapeTaskUniqueIndex() {
        helper.createDatabase(DB, 10).use { db ->
            db.execSQL(
                "INSERT INTO scrape_tasks(id, content_id, content_kind, provider, task_kind, " +
                    "state, attempts, next_attempt_at, priority, last_error, updated_at) " +
                    "VALUES('a','w1','movie','tmdb','search','PENDING',0,0,0,NULL,1)",
            )
        }
        helper.runMigrationsAndValidate(DB, 11, false, MediaDatabase.MIGRATION_10_11).use { db ->
            db.execSQL(
                "INSERT OR IGNORE INTO scrape_tasks(id, content_id, content_kind, provider, task_kind, " +
                    "state, attempts, next_attempt_at, priority, last_error, updated_at) " +
                    "VALUES('b','w1','movie','tmdb','search','PENDING',0,0,0,NULL,2)",
            )
            val count = db.query("SELECT COUNT(*) FROM scrape_tasks").use {
                it.moveToFirst(); it.getInt(0)
            }
            assertTrue("唯一索引应合并重复任务", count == 1)
        }
    }

    private companion object {
        const val DB = "mediadock-v10-v11-test"
    }
}
