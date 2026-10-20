package com.lovelev.mediadock.data.repo

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 片单增强：剧集播放展开整季，电影只播选中版本。 */
@RunWith(AndroidJUnit4::class)
class PlaylistQueueTest {

    private lateinit var db: MediaDatabase
    private lateinit var store: MediaLibraryStore
    private lateinit var repo: PlaylistRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MediaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = MediaLibraryStore(db)
        repo = PlaylistRepository(db, store)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun episode(n: Int) = MediaLibraryStore.ScannedFile(
        path = "剧/S01/0$n.mp4",
        name = "0$n.mp4",
        sizeBytes = 100L + n,
        mtime = "t",
        title = "测试剧",
        year = 2023,
        season = 1,
        episode = n,
    )

    @Test
    fun seriesExpandsToWholeSeasonInOrder() = runBlocking {
        store.applyDirectoryListing(SourceId.WEBDAV, "剧/S01", (1..3).map(::episode))
        val playlistId = repo.create("列表")
        repo.add(playlistId, "剧/S01/02.mp4")

        assertEquals(
            listOf("剧/S01/01.mp4", "剧/S01/02.mp4", "剧/S01/03.mp4"),
            repo.playQueue("剧/S01/02.mp4"),
        )
    }

    @Test
    fun movieQueueKeepsSelectedVersionOnly() = runBlocking {
        val movie = MediaLibraryStore.ScannedFile(
            path = "电影/同一部.mp4",
            name = "同一部.mp4",
            sizeBytes = 100,
            mtime = "t",
            title = "同一部",
            year = 2020,
            season = null,
            episode = null,
        )
        store.applyDirectoryListing(SourceId.WEBDAV, "电影", listOf(movie))
        val playlistId = repo.create("列表")
        repo.add(playlistId, movie.path)

        assertEquals(listOf(movie.path), repo.playQueue(movie.path))
    }
}
