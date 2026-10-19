package com.lovelev.mediadock.scan

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.data.scan.DirectoryLister
import com.lovelev.mediadock.core.data.scan.DirectoryListing
import com.lovelev.mediadock.core.data.scan.RemoteEntry
import com.lovelev.mediadock.core.data.scan.ScanEngine
import com.lovelev.mediadock.core.data.scan.ScanLimits
import com.lovelev.mediadock.core.data.scan.ScanOutcome
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.ScanMode
import com.lovelev.mediadock.data.local.ScanStatus
import com.lovelev.mediadock.data.webdav.WebDavException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanEngineTest {

    private lateinit var db: MediaDatabase
    private lateinit var engine: ScanEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = ScanEngine(
            db,
            MediaLibraryStore(db),
            ScanLimits(maxDirectories = 100, maxFiles = 100, requestIntervalMs = 0),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fullScanWritesFilesAndCache() = runBlocking {
        val lister = MapLister(
            mapOf(
                "" to DirectoryListing(
                    entries = listOf(
                        dir("电影", etag = "e-movie"),
                        file("根.mp4", 10),
                        file("空.mkv", 0),
                        dir(".hidden", etag = "e-hidden"),
                    ),
                    etag = "e-root",
                ),
                "电影" to DirectoryListing(
                    entries = listOf(file("电影/片.mp4", 20), dir("电影/子", etag = "e-sub")),
                    etag = "e-movie",
                ),
                "电影/子" to DirectoryListing(
                    entries = listOf(file("电影/子/子片.mp4", 30)),
                    etag = "e-sub",
                ),
            ),
        )

        val outcome = engine.run(SourceId.WEBDAV, ScanMode.FULL, lister)
        assertTrue(outcome is ScanOutcome.Finished)
        assertEquals(3, (outcome as ScanOutcome.Finished).files)
        assertEquals(3, db.mediaDao().allItems().size)
        assertEquals(3, count("directory_cache"))
        assertEquals(0, count("scan_queue"))
        val run = db.openHelper.readableDatabase
            .query("SELECT status, dirs, files FROM scan_runs").use { c ->
                c.moveToFirst()
                Triple(c.getString(0), c.getInt(1), c.getInt(2))
            }
        assertEquals(ScanStatus.DONE, run.first)
        assertEquals(3, run.second)
        assertEquals(3, run.third)
    }

    @Test
    fun incrementalSkipsUnchangedSubtree() = runBlocking {
        val tree = mapOf(
            "" to DirectoryListing(
                entries = listOf(dir("电影", etag = "e-movie")),
                etag = "e-root",
            ),
            "电影" to DirectoryListing(
                entries = listOf(file("电影/片.mp4", 10)),
                etag = "e-movie",
            ),
        )
        engine.run(SourceId.WEBDAV, ScanMode.FULL, MapLister(tree))

        val calls = mutableListOf<String>()
        val cachingLister = DirectoryLister { key, etag, _ ->
            calls += key
            if (key == "" && etag == "e-root") {
                DirectoryListing(notModified = true)
            } else {
                tree.getValue(key)
            }
        }
        val outcome = engine.run(SourceId.WEBDAV, ScanMode.INCREMENTAL, cachingLister)
        assertTrue(outcome is ScanOutcome.Finished)
        assertEquals(0, (outcome as ScanOutcome.Finished).files)
        assertEquals(listOf(""), calls)
        assertEquals(1, db.mediaDao().allItems().size)
    }

    @Test
    fun fullScanMarksDeletedFileMissing() = runBlocking {
        engine.run(
            SourceId.WEBDAV,
            ScanMode.FULL,
            MapLister(
                mapOf(
                    "" to DirectoryListing(
                        entries = listOf(file("a.mp4", 10), file("b.mp4", 20)),
                        etag = "e1",
                    ),
                ),
            ),
        )
        assertEquals(2, db.mediaDao().allItems().size)

        engine.run(
            SourceId.WEBDAV,
            ScanMode.FULL,
            MapLister(
                mapOf(
                    "" to DirectoryListing(entries = listOf(file("a.mp4", 10)), etag = "e2"),
                ),
            ),
        )
        assertEquals(1, db.mediaDao().allItems().size)
        val missing = db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM media_files WHERE missing_at IS NOT NULL")
            .use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, missing)
    }

    @Test
    fun unauthorizedBlocksWithoutRetry() = runBlocking {
        val lister = DirectoryLister { _, _, _ -> throw WebDavException.Unauthorized() }
        val outcome = engine.run(SourceId.WEBDAV, ScanMode.FULL, lister)
        assertTrue(outcome is ScanOutcome.Blocked)
        val status = db.openHelper.readableDatabase
            .query("SELECT status FROM scan_runs").use { it.moveToFirst(); it.getString(0) }
        assertEquals(ScanStatus.BLOCKED, status)
    }

    @Test
    fun resumesFromPersistedQueueAfterCancellation() = runBlocking {
        val first = DirectoryLister { key, _, _ ->
            when (key) {
                "" -> DirectoryListing(
                    entries = listOf(dir("A", "ea"), dir("B", "eb")),
                    etag = "er",
                )
                else -> throw CancellationException("stopped")
            }
        }
        runCatching { engine.run(SourceId.WEBDAV, ScanMode.FULL, first) }
            .onSuccess { throw AssertionError("应被取消") }
            .onFailure { assertTrue(it is CancellationException) }

        val running = db.openHelper.readableDatabase
            .query("SELECT status FROM scan_runs").use { it.moveToFirst(); it.getString(0) }
        assertEquals(ScanStatus.RUNNING, running)
        assertEquals(2, count("scan_queue"))

        val second = MapLister(
            mapOf(
                "A" to DirectoryListing(entries = listOf(file("A/1.mp4", 10)), etag = "ea"),
                "B" to DirectoryListing(entries = listOf(file("B/2.mp4", 20)), etag = "eb"),
            ),
        )
        val outcome = engine.run(SourceId.WEBDAV, ScanMode.INCREMENTAL, second)
        assertTrue(outcome is ScanOutcome.Finished)
        assertEquals(2, (outcome as ScanOutcome.Finished).files)
        assertEquals(2, db.mediaDao().allItems().size)
        assertEquals(0, count("scan_queue"))
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM `$table`").use {
            it.moveToFirst(); it.getInt(0)
        }

    private fun file(path: String, size: Long) = RemoteEntry(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = false,
        sizeBytes = size,
        lastModified = "t",
        etag = "e-$path",
    )

    private fun dir(path: String, etag: String) = RemoteEntry(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = true,
        sizeBytes = null,
        lastModified = "t",
        etag = etag,
    )

    private class MapLister(private val tree: Map<String, DirectoryListing>) : DirectoryLister {
        override suspend fun list(
            remoteKey: String,
            etag: String?,
            lastModified: String?,
        ): DirectoryListing = tree[remoteKey] ?: error("缺少目录：$remoteKey")
    }
}
