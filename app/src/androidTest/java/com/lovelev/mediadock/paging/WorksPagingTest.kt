package com.lovelev.mediadock.paging

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.data.WorksCardSql
import com.lovelev.mediadock.core.data.WorksSort
import com.lovelev.mediadock.core.data.toWorkCard
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorksPagingTest {

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
    fun pagingReturnsWorksWithPathsAndAggregates() = runBlocking {
        store.replaceScannedFiles(
            SourceId.WEBDAV,
            listOf(
                MediaLibraryStore.ScannedFile(
                    "电影/单片 (2020).mp4", "单片 (2020).mp4", 100, "t", "单片", 2020, null, null,
                ),
                MediaLibraryStore.ScannedFile(
                    "剧/S01/01.mp4", "01.mp4", 10, "t", "测试剧", 2023, 1, 1,
                ),
                MediaLibraryStore.ScannedFile(
                    "剧/S01/02.mp4", "02.mp4", 20, "t", "测试剧", 2023, 1, 2,
                ),
                MediaLibraryStore.ScannedFile(
                    "剧/S01/03.mp4", "03.mp4", 30, "t", "测试剧", 2023, 1, 3,
                ),
            ),
        )
        store.toggleFavorite("电影/单片 (2020).mp4", "单片", 2020, null, null)
        store.saveProgress(
            "电影/单片 (2020).mp4", "单片", 2020, null, null,
            positionMs = 500, durationMs = 1000, updatedAt = 1L,
        )
        store.saveProgress(
            "剧/S01/01.mp4", "测试剧", 2023, 1, 1,
            positionMs = 1000, durationMs = 1000, updatedAt = 2L,
        )

        val source = db.worksCardDao().pagingWorks(WorksCardSql.paging(WorksSort.TITLE))
        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 30, placeholdersEnabled = false),
        )
        val page = (result as PagingSource.LoadResult.Page).data.map { it.toWorkCard() }
        assertEquals(2, page.size)

        val movie = page.first { it.title == "单片" }
        assertEquals(1, movie.paths.size)
        assertTrue(movie.isFavorite)
        assertEquals(0.5f, movie.progressFraction, 0.01f)

        val series = page.first { it.title == "测试剧" }
        assertEquals(3, series.episodeCount)
        assertEquals(3, series.paths.size)
        assertEquals(1, series.completedEpisodes)
        assertEquals(1f / 3f, series.progressFraction, 0.01f)

        val random = db.worksCardDao().cards(WorksCardSql.random(5)).map { it.toWorkCard() }
        assertEquals(2, random.size)
        val recent = db.worksCardDao().cards(WorksCardSql.limited(WorksSort.RECENT, 1))
        assertEquals(1, recent.size)
    }
}
