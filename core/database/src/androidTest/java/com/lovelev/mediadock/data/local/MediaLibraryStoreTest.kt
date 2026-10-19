package com.lovelev.mediadock.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.model.SourceId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** v9 写路径集成测试：扫描 → 收藏/片单/进度/改名/详情/刮削合并 → 清库。 */
@RunWith(AndroidJUnit4::class)
class MediaLibraryStoreTest {

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

    @Test
    fun scanAndUserDataRoundTrip() = runBlocking {
        val movie = MediaLibraryStore.ScannedFile(
            path = "电影/测试片.2020.mp4",
            name = "测试片.2020.mp4",
            sizeBytes = 1000,
            mtime = "t1",
            title = "测试片",
            year = 2020,
            season = null,
            episode = null,
        )
        val episode = MediaLibraryStore.ScannedFile(
            path = "剧/测试剧/S01/01.mp4",
            name = "01.mp4",
            sizeBytes = 800,
            mtime = "t2",
            title = "测试剧",
            year = 2023,
            season = 1,
            episode = 1,
        )
        store.replaceScannedFiles(SourceId.WEBDAV, listOf(movie, episode))

        assertEquals(2, db.mediaDao().allItems().size)
        assertEquals(2, count("works"))
        assertEquals(1, count("episodes"))

        // 收藏按作品，兼容视图展开回 path。
        store.toggleFavorite(movie.path, movie.title, movie.year, null, null)
        assertEquals(1, count("favorites"))
        assertEquals(1, count("favorites_view"))
        assertEquals(1, db.favoriteDao().countOf(movie.path))

        // 片单：创建 → 加入 → 去重。
        val playlistId = store.createPlaylist("测试片单")
        store.addToPlaylist(playlistId, movie.path, movie.title, movie.year, null, null)
        store.addToPlaylist(playlistId, movie.path, movie.title, movie.year, null, null)
        assertEquals(1, count("playlist_items"))
        assertEquals(1, count("playlist_items_view"))
        assertEquals(listOf(movie.path), db.playlistDao().items(playlistId).map { it.path })

        // 进度按内容；兼容视图按 path 还原。
        store.saveProgress(movie.path, movie.title, movie.year, null, null, 600, 1000, 123L)
        assertEquals(1, count("movie_progress"))
        assertEquals(1, count("playback_progress"))
        assertEquals(600L, db.progressDao().find(movie.path)?.positionMs)

        // 改名与详情按作品。
        store.saveOverride(listOf(movie.path), "改过的名字", 2021)
        assertEquals(1, count("title_overrides"))
        assertEquals("改过的名字", db.titleOverrideDao().observeAll().first().first().title)
        store.saveDetails(
            path = movie.path,
            tmdbId = 100,
            releaseDate = "2020-01-01",
            runtimeMinutes = 120,
            genres = "剧情",
            country = "中国",
            castJson = "[]",
            totalEpisodes = null,
            totalSeasons = null,
            fetchedAt = 1L,
        )
        assertEquals(1, count("work_details"))
        assertEquals(1, count("title_details"))

        // 清库只删文件与版本，作品/用户数据保留。
        store.clearLibrary()
        assertEquals(0, count("media_items"))
        assertEquals(0, count("media_files"))
        assertEquals(2, count("works"))
        assertEquals(1, count("favorites"))
        assertEquals(1, count("playlist_items"))
        assertEquals(1, count("movie_progress"))
    }

    @Test
    fun movieVersionsMergeIntoOneWork() = runBlocking {
        store.replaceScannedFiles(
            SourceId.WEBDAV,
            listOf(
                MediaLibraryStore.ScannedFile("电影/同名.2020.1080p.mp4", "a.mp4", 100, "t", "同名", 2020, null, null),
                MediaLibraryStore.ScannedFile("电影/同名.2020.4K.mp4", "b.mp4", 200, "t", "同名", 2020, null, null),
            ),
        )
        assertEquals(1, count("works"))
        assertEquals(2, count("movie_versions"))
        assertEquals(2, count("media_items"))
    }

    @Test
    fun scrapeUpgradesTemporaryIdentityAndKeepsUserData() = runBlocking {
        val path = "动漫/剧/S01/01.mp4"
        store.replaceScannedFiles(
            SourceId.WEBDAV,
            listOf(
                MediaLibraryStore.ScannedFile(path, "01.mp4", 100, "t", "剧", 2023, 1, 1),
            ),
        )
        val before = db.mediaDao().allItems().first()
        assertTrue(before.scrapeState == ScrapeStatus.UNSCRAPED)
        store.toggleFavorite(path, "剧", 2023, 1, 1)
        store.saveProgress(path, "剧", 2023, 1, 1, 500, 1000, 1L)

        store.applyScrape(
            path = path,
            tmdbId = 200,
            title = "剧",
            year = 2023,
            posterPath = "/p.jpg",
            backdropPath = null,
            overview = "简介",
            rating = 8.0,
            matched = true,
            scrapedAt = 2L,
        )

        val work = db.writeDao().workByLogicalKey("tmdb:tv:200")
        assertNotNull(work)
        val episode = db.writeDao().episodeByLogicalKey("tmdb:tv:200:s1:e1")
        assertNotNull(episode)
        assertEquals(1, count("favorites"))
        assertEquals(1, count("episode_progress"))
        assertEquals(1, count("playback_progress"))
        val after = db.mediaDao().allItems().first()
        assertEquals(ScrapeStatus.MATCHED, after.scrapeState)
        assertEquals(200, after.tmdbId)
    }

    private suspend fun count(table: String): Int {
        val sql = "SELECT COUNT(*) FROM `$table`"
        return when (table) {
            "media_items" -> db.mediaDao().allItems().size
            "favorites_view" -> db.favoriteDao().observeAll().first().size
            "title_overrides" -> db.titleOverrideDao().observeAll().first().size
            else -> db.openHelper.readableDatabase
                .query(sql).use { it.moveToFirst(); it.getInt(0) }
        }
    }
}
