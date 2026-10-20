package com.lovelev.mediadock.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v11 → v12：首页「最近」分页索引。 */
@RunWith(AndroidJUnit4::class)
class MigrationV11ToV12Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun addsRecentPagingIndex() {
        helper.createDatabase(DB, 11).use { }
        helper.runMigrationsAndValidate(DB, 12, false, MediaDatabase.MIGRATION_11_12).use { db ->
            val found = db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' " +
                    "AND name = 'index_works_added_at_id'",
            ).use { it.moveToFirst() }
            assertTrue("应创建 works(added_at, id) 索引", found)
        }
    }

    private companion object {
        const val DB = "mediadock-v11-v12-test"
    }
}
