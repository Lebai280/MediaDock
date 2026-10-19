package com.lovelev.mediadock.scrape

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.data.scrape.ScrapeBudget
import com.lovelev.mediadock.core.data.scrape.ScrapeEngine
import com.lovelev.mediadock.core.data.scrape.ScrapeLimits
import com.lovelev.mediadock.core.data.scrape.ScrapeMatcher
import com.lovelev.mediadock.core.data.scrape.ScrapeOutcome
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.ScrapeTaskState
import com.lovelev.mediadock.data.tmdb.TmdbAuthException
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import com.lovelev.mediadock.data.tmdb.TmdbSecret
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrapeEngineTest {

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
    fun matchesSuccessAndBacksOffFailure() = runBlocking {
        seedMovies("成功", "失败")
        val engine = engine(
            matcher = ScrapeMatcher { title, _, _, _ ->
                if (title.startsWith("成功")) match(100, "成功") else null
            },
            budget = FakeBudget(100),
        )
        val outcome = engine.run(TmdbSecret("api-key"))
        assertTrue(outcome is ScrapeOutcome.Finished)
        assertEquals(1, (outcome as ScrapeOutcome.Finished).matched)
        assertEquals(1, outcome.failed)

        val success = db.writeDao().pendingWorks().firstOrNull { it.title.startsWith("成功") }
        val failed = db.writeDao().pendingWorks().firstOrNull { it.title.startsWith("失败") }
        assertEquals(null, success) // 已匹配，不再待刮削
        assertNotNull(failed) // 未匹配仍待处理

        val tasks = db.scrapeTaskDao().all()
        val done = tasks.first { it.contentKind == "movie" && it.state == ScrapeTaskState.DONE }
        val retry = tasks.first { it.state == ScrapeTaskState.PENDING && it.attempts > 0 }
        assertEquals(0, done.attempts)
        assertTrue(retry.nextAttemptAt != null && retry.nextAttemptAt!! > System.currentTimeMillis())

        // 退避期内不会立刻重试。
        val second = engine.run(TmdbSecret("api-key"))
        assertEquals(0, (second as ScrapeOutcome.Finished).processed)
    }

    @Test
    fun authErrorStopsQueue() = runBlocking {
        seedMovies("认证失败")
        val engine = engine(
            matcher = ScrapeMatcher { _, _, _, _ -> throw TmdbAuthException("凭据无效") },
            budget = FakeBudget(100),
        )
        val outcome = engine.run(TmdbSecret("bad"))
        assertTrue(outcome is ScrapeOutcome.NeedsSecret)
        val task = db.scrapeTaskDao().all().first()
        assertEquals(ScrapeTaskState.PENDING, task.state)
        assertEquals(0, task.attempts)
    }

    @Test
    fun budgetStopsWithoutLosingTasks() = runBlocking {
        seedMovies("A", "B", "C")
        val engine = engine(
            matcher = ScrapeMatcher { _, _, _, _ -> null },
            budget = FakeBudget(1),
        )
        val outcome = engine.run(TmdbSecret("api-key"))
        assertTrue(outcome is ScrapeOutcome.BudgetExhausted)
        assertEquals(1, (outcome as ScrapeOutcome.BudgetExhausted).processed)
        assertEquals(3, db.scrapeTaskDao().countPending())
    }

    @Test
    fun enqueueIsIdempotent() = runBlocking {
        seedMovies("X", "Y")
        assertEquals(2, store.enqueuePendingScrapeTasks())
        assertEquals(2, store.enqueuePendingScrapeTasks())
        assertEquals(2, db.scrapeTaskDao().all().size)
    }

    private suspend fun seedMovies(vararg titles: String) {
        store.replaceScannedFiles(
            SourceId.WEBDAV,
            titles.mapIndexed { i, title ->
                MediaLibraryStore.ScannedFile(
                    path = "$title (2020).mp4",
                    name = "$title (2020).mp4",
                    sizeBytes = 100L + i,
                    mtime = "t",
                    title = title,
                    year = 2020,
                    season = null,
                    episode = null,
                )
            },
        )
        store.enqueuePendingScrapeTasks()
    }

    private fun engine(matcher: ScrapeMatcher, budget: ScrapeBudget) = ScrapeEngine(
        db = db,
        store = store,
        matcher = matcher,
        budget = budget,
        limits = ScrapeLimits(batchSize = 4, requestIntervalMs = 0, maxTasksPerRun = 100),
    )

    private fun match(id: Int, title: String) = TmdbMatch(
        tmdbId = id,
        title = title,
        year = 2020,
        posterPath = "/p.jpg",
        backdropPath = null,
        overview = "简介",
        rating = 8.0,
    )

    private class FakeBudget(private var remaining: Int) : ScrapeBudget {
        override suspend fun tryConsume(): Boolean {
            if (remaining <= 0) return false
            remaining--
            return true
        }
    }
}
