package com.lovelev.mediadock.search

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.data.WorksCardSql
import com.lovelev.mediadock.core.data.WorksFilter
import com.lovelev.mediadock.core.data.WorksSort
import com.lovelev.mediadock.core.data.toWorkCard
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.SearchIndexer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchPagingTest {

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
    fun chineseLatinAndCastSearch() = runBlocking {
        seed()

        assertEquals(listOf("葬送的芙莉莲"), search("芙莉莲"))
        assertEquals(listOf("葬送的芙莉莲"), search("芙 莉 莲"))
        assertEquals(listOf("Avatar"), search("avatar"))
        assertEquals(listOf("Avatar"), search("ava"))
        // 演员名也进索引
        assertEquals(listOf("葬送的芙莉莲"), search("演员甲"))
        // 手工改名也进索引
        store.saveOverride(listOf("剧/S01/01.mp4"), "改名后的剧", 2023)
        assertEquals(listOf("改名后的剧"), search("改名"))
    }

    @Test
    fun filtersRunInSql() = runBlocking {
        seed()
        assertEquals(listOf("葬送的芙莉莲"), search(type = "动漫"))
        assertEquals(listOf("葬送的芙莉莲"), search(genre = "奇幻", country = "日本"))
        assertEquals(listOf("Avatar"), search(type = "电影"))
        assertEquals(listOf("Avatar"), search(year = "2000年代"))
        assertEquals(listOf("葬送的芙莉莲"), search(year = "2020年代"))
        assertEquals(emptyList<String>(), search(year = "1990年代"))
    }

    private suspend fun search(
        query: String = "",
        type: String = "",
        genre: String = "",
        country: String = "",
        year: String = "",
    ): List<String> {
        val filter = WorksFilter(
            query = query,
            type = type,
            genre = genre,
            country = country,
            year = year,
            sort = WorksSort.TITLE,
        )
        val source = db.worksCardDao().pagingWorks(WorksCardSql.paging(filter))
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )
        return (result as PagingSource.LoadResult.Page).data.map { it.toWorkCard().title }
    }

    private suspend fun seed() {
        store.replaceScannedFiles(
            SourceId.WEBDAV,
            listOf(
                MediaLibraryStore.ScannedFile(
                    "剧/S01/01.mp4", "01.mp4", 10, "t", "葬送的芙莉莲", 2023, 1, 1,
                ),
                MediaLibraryStore.ScannedFile(
                    "剧/S01/02.mp4", "02.mp4", 20, "t", "葬送的芙莉莲", 2023, 1, 2,
                ),
                MediaLibraryStore.ScannedFile(
                    "电影/Avatar (2009).mp4", "Avatar (2009).mp4", 100, "t", "Avatar", 2009, null, null,
                ),
            ),
        )
        store.saveDetails(
            path = "剧/S01/01.mp4",
            tmdbId = 200,
            releaseDate = "2023-09-29",
            runtimeMinutes = 24,
            genres = "动画,奇幻",
            country = "日本",
            castJson = """[{"name":"演员甲","role":null,"profilePath":null}]""",
            totalEpisodes = 2,
            totalSeasons = 1,
            fetchedAt = 1L,
        )
        store.saveDetails(
            path = "电影/Avatar (2009).mp4",
            tmdbId = 100,
            releaseDate = "2009-12-18",
            runtimeMinutes = 162,
            genres = "科幻,动作",
            country = "美国",
            castJson = "[]",
            totalEpisodes = null,
            totalSeasons = null,
            fetchedAt = 1L,
        )
        SearchIndexer(db).ensureFresh()
    }
}
