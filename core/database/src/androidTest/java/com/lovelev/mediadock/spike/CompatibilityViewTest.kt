package com.lovelev.mediadock.spike

import androidx.room.Dao
import androidx.room.Database
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S1 spike: prove that Room can read a compatibility VIEW with the same
 * column names the current MediaDao expects.
 *
 * This is test-only; it intentionally does not touch the production database.
 */
@Entity(tableName = "seed")
data class Seed(
    @PrimaryKey val id: Int,
    val path: String,
    val name: String,
    val sizeBytes: Long,
)

@DatabaseView(
    value = "SELECT path AS path, name AS name, sizeBytes AS sizeBytes FROM seed",
    viewName = "media_items",
)
data class MediaItemView(
    val path: String,
    val name: String,
    val sizeBytes: Long,
)

@Dao
interface SeedDao {
    @Insert
    suspend fun insert(seed: Seed)

    @Query("SELECT * FROM media_items ORDER BY path")
    suspend fun all(): List<MediaItemView>

    @Query("SELECT COUNT(*) FROM media_items WHERE path IN (:paths)")
    suspend fun countIn(paths: List<String>): Int
}

@Database(
    entities = [Seed::class],
    views = [MediaItemView::class],
    version = 1,
    exportSchema = false,
)
abstract class SpikeViewDatabase : RoomDatabase() {
    abstract fun dao(): SeedDao
}

@RunWith(AndroidJUnit4::class)
class CompatibilityViewTest {

    private lateinit var db: SpikeViewDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, SpikeViewDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun roomReadsCompatibilityView() = runBlocking {
        db.dao().insert(Seed(1, "电影/a.mp4", "a.mp4", 100))
        db.dao().insert(Seed(2, "动画/b.mp4", "b.mp4", 200))

        val rows = db.dao().all()
        assertEquals(2, rows.size)
        assertEquals(
            setOf("电影/a.mp4", "动画/b.mp4"),
            rows.map { it.path }.toSet(),
        )
        assertEquals("b.mp4", rows.first { it.path == "动画/b.mp4" }.name)
        assertEquals(1, db.dao().countIn(listOf("电影/a.mp4", "不存在.mp4")))
    }
}
