package com.lovelev.mediadock.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v9 → v10：扫描代际 / 目录缓存 / 持久队列。 */
@RunWith(AndroidJUnit4::class)
class MigrationV9ToV10Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MediaDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun addsScanTables() {
        helper.createDatabase(DB, 9).use { }
        helper.runMigrationsAndValidate(DB, 10, false, MediaDatabase.MIGRATION_9_10).use { db ->
            assertEquals(0, count(db, "scan_runs"))
            assertEquals(0, count(db, "directory_cache"))
            assertEquals(0, count(db, "scan_queue"))
        }
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private companion object {
        const val DB = "mediadock-v9-v10-test"
    }
}
