package com.lovelev.mediadock.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.model.SourceId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 多数据源写层：同一相对路径来自两个源时必须共存且身份不同。 */
@RunWith(AndroidJUnit4::class)
class MultiSourceStoreTest {

    private lateinit var db: MediaDatabase
    private lateinit var store: MediaLibraryStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = MediaLibraryStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun file(path: String, legacyPath: String, title: String) =
        MediaLibraryStore.ScannedFile(
            path = path,
            name = path.substringAfterLast('/'),
            sizeBytes = 100,
            mtime = "t",
            title = title,
            year = 2020,
            season = null,
            episode = null,
            legacyPath = legacyPath,
        )

    @Test
    fun sameWorkFromTwoSourcesMergesIntoSingleWorkWithVersions() = runBlocking {
        val legacy = SourceId.WEBDAV
        val second = SourceId("webdav-b")
        store.applyDirectoryListing(
            legacy,
            "电影",
            listOf(file("电影/同名.mkv", "电影/同名.mkv", "同名")),
        )
        store.applyDirectoryListing(
            second,
            "电影",
            listOf(file("电影/同名.mkv", "source:webdav-b/电影/同名.mkv", "同名")),
        )

        // 两个源的文件行必须共存（同一相对路径不能互相覆盖）
        val rows = query("SELECT id, source_id, legacy_path FROM media_files ORDER BY source_id")
        assertEquals(2, rows.size)
        assertEquals(listOf("webdav", "webdav-b"), rows.map { it[1] })
        assertEquals("电影/同名.mkv", rows[0][2])
        assertEquals("source:webdav-b/电影/同名.mkv", rows[1][2])
        assertNotEquals(rows[0][0], rows[1][0])

        // E2.5：同标题/年份/类型跨源合并为同一作品，两个文件成为该作品的版本
        val legacyResolved = store.resolve("电影/同名.mkv")
        val secondResolved = store.resolve("source:webdav-b/电影/同名.mkv")
        assertNotNull(legacyResolved)
        assertNotNull(secondResolved)
        assertEquals(legacyResolved!!.workId, secondResolved!!.workId)

        val versions = db.mediaDao().movieVersionRows(legacyResolved.workId)
        assertEquals(2, versions.size)
        assertEquals(setOf("webdav", "webdav-b"), versions.map { it.sourceId }.toSet())
    }

    private fun query(sql: String): List<List<String>> =
        db.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { cursor.getString(it) })
                }
            }
        }
}
