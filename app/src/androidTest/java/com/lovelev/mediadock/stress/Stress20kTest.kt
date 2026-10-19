package com.lovelev.mediadock.stress

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.data.WorkCard
import com.lovelev.mediadock.core.data.WorksCardSql
import com.lovelev.mediadock.core.data.WorksFilter
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
import java.io.File
import java.util.Locale

/**
 * E1.5：20k 合成媒体库压测。
 *
 * 经生产写层（[MediaLibraryStore.applyDirectoryListing]）播种 4,000 部电影 + 1,000 部剧
 * （每部 16 集），共 20,000 文件 / 5,000 作品，随后测量：
 * - 首页首屏（works 分页第一页）与整库滚动分页
 * - FTS 搜索、筛选、随机推荐与分类行查询
 * - 进程 PSS 峰值与数据库体积
 *
 * 验收目标（ARCHITECTURE §15.8）：首屏 p95 ≤ 1 s、搜索 p95 ≤ 300 ms、
 * 内存 < 300 MB、DB < 200 MB。
 *
 * 仅针对 `.debug` 包运行（applicationIdSuffix 不同），库文件在 debug 包私有目录，
 * 不影响真机上的 release 安装与用户数据。
 */
@RunWith(AndroidJUnit4::class)
class Stress20kTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "stress20k.db"

    private lateinit var db: MediaDatabase
    private lateinit var store: MediaLibraryStore

    @Before
    fun setUp() {
        deleteDbFiles()
        db = Room.databaseBuilder(context, MediaDatabase::class.java, dbName).build()
        store = MediaLibraryStore(db)
    }

    @After
    fun tearDown() {
        db.close()
        deleteDbFiles()
    }

    @Test
    fun stress20kLibrary() = runBlocking {
        val baselinePss = pssKb()
        Log.i(TAG, "PSS baseline=${mb(baselinePss)} MB")

        val seedStart = System.nanoTime()
        seed()
        val seedMs = (System.nanoTime() - seedStart) / 1_000_000
        Log.i(TAG, "seed done in $seedMs ms, PSS peak so far=${mb(peakPssKb)} MB")

        val files = count("SELECT COUNT(*) FROM media_files")
        val works = count("SELECT COUNT(*) FROM works")
        val episodes = count("SELECT COUNT(*) FROM episodes")
        Log.i(TAG, "counts files=$files works=$works episodes=$episodes")
        assertEquals(FILE_COUNT, files)
        assertEquals(WORK_COUNT, works)
        assertEquals(EPISODE_COUNT, episodes)

        // 首页首屏：默认排序（RECENT）第一页。
        val firstPage = measure("works.firstPage", REPEATS) {
            loadFirstPage(WorksCardSql.paging(WorksSort.RECENT))
        }
        assertTrue(
            "首页首屏 p95 ${fmt(firstPage.p95)} ms 超过 1000 ms",
            firstPage.p95 <= 1000.0,
        )

        // 首屏标题排序（搜索页/排序视图使用）。
        val titlePage = measure("works.firstPage.title", REPEATS) {
            loadFirstPage(WorksCardSql.paging(WorksSort.TITLE))
        }

        // 整库滚动：顺序加载全部页，模拟海报墙下滑到底。
        val scroll = scrollAll(WorksCardSql.paging(WorksSort.RECENT), PAGE_SIZE)
        Log.i(
            TAG,
            "scroll pages=${scroll.pages} rows=${scroll.rows} totalMs=${scroll.totalMs} " +
                "pageP50=${fmt(scroll.p50)} ms pageP95=${fmt(scroll.p95)} ms",
        )
        assertEquals(WORK_COUNT, scroll.rows)
        // 架构 SLO 只约束首屏与搜索；整库逐页加载是预取路径，单页上限取 500 ms，
        // 总耗时上限 90 s（5,000 作品 / 167 页）。
        assertTrue("整库滚动分页 p95 ${fmt(scroll.p95)} ms 超过 500 ms", scroll.p95 <= 500.0)
        assertTrue("整库滚动总耗时 ${scroll.totalMs} ms 超过 90 s", scroll.totalMs <= 90_000)

        // 首页其余行。
        val random = measure("home.random5", 10) {
            db.worksCardDao().cards(WorksCardSql.random(5)).map { it.toWorkCard() }
        }
        val recentRow = measure("home.recent200", 10) {
            db.worksCardDao().cards(WorksCardSql.limited(WorksSort.RECENT, 200))
        }
        val favoritesRow = measure("home.favorites20", 10) {
            db.worksCardDao().cards(WorksCardSql.favorites(20))
        }

        // 搜索：中文短语、逐字、拉丁前缀、演员、数字。
        val searchQueries = listOf("芙莉莲", "芙 莉 莲", "星际", "Avatar", "Ava", "剧1", "演员甲")
        val searchSamples = ArrayList<Long>(searchQueries.size * REPEATS)
        for (q in searchQueries) {
            val result = measure("search.$q", REPEATS) {
                loadFirstPage(WorksCardSql.paging(WorksFilter(query = q, sort = WorksSort.RECENT)))
            }
            searchSamples.addAll(result.samples.toList())
            assertTrue("搜索 '$q' 无结果", result.rowCount > 0)
        }
        val searchP95 = p95(searchSamples)
        Log.i(TAG, "search overall p95=${fmt(searchP95)} ms")
        assertTrue("搜索 p95 ${fmt(searchP95)} ms 超过 300 ms", searchP95 <= 300.0)

        // 筛选走 SQL，同样应在 300 ms 内。
        val filterSamples = measure("filter.type.movie", REPEATS) {
            loadFirstPage(WorksCardSql.paging(WorksFilter(type = "电影", sort = WorksSort.RECENT)))
        }.samples + measure("filter.genre", REPEATS) {
            loadFirstPage(WorksCardSql.paging(WorksFilter(genre = "奇幻", sort = WorksSort.RECENT)))
        }.samples + measure("filter.year", REPEATS) {
            loadFirstPage(WorksCardSql.paging(WorksFilter(year = "2020年代", sort = WorksSort.RECENT)))
        }.samples
        Log.i(TAG, "filter p95=${fmt(p95(filterSamples))} ms")

        // 体积与内存。
        val dbBytes = dbSizeBytes()
        Log.i(TAG, "db size=${mb(dbBytes)} MB")
        samplePss()
        Log.i(TAG, "PSS peak=${mb(peakPssKb)} MB (baseline ${mb(baselinePss)} MB)")
        Log.i(
            TAG,
            "SUMMARY seedMs=$seedMs firstPageP95=${fmt(firstPage.p95)} " +
                "titleP95=${fmt(titlePage.p95)} scrollP95=${fmt(scroll.p95)} " +
                "randomP95=${fmt(random.p95)} recent200P95=${fmt(recentRow.p95)} " +
                "favoritesP95=${fmt(favoritesRow.p95)} searchP95=${fmt(searchP95)} " +
                "dbMB=${fmt(mb(dbBytes))} pssMB=${fmt(mb(peakPssKb))}",
        )
        assertTrue("数据库 ${mb(dbBytes)} MB 超过 200 MB", dbBytes < 200L * 1024 * 1024)
        assertTrue("PSS 峰值 ${mb(peakPssKb)} MB 超过 300 MB", peakPssKb < 300 * 1024)
    }

    // ------------------------------------------------------------------ 播种

    private suspend fun seed() {
        // 电影：一个扁平目录 4,000 部。
        val movies = ArrayList<MediaLibraryStore.ScannedFile>(MOVIE_COUNT)
        repeat(MOVIE_COUNT) { i ->
            val title = movieTitle(i)
            val year = 1990 + i % 35
            movies += MediaLibraryStore.ScannedFile(
                path = "电影/$title ($year)/$title.mp4",
                name = "$title.mp4",
                sizeBytes = 1_000_000_000L + i,
                mtime = "seed",
                title = title,
                year = year,
                season = null,
                episode = null,
            )
        }
        val t0 = System.nanoTime()
        store.applyDirectoryListing(SourceId.WEBDAV, "电影", movies)
        Log.i(TAG, "seed movies=${movies.size} in ${ms(t0)} ms")
        samplePss()

        // 剧集：每部 2 季 × 8 集，按季目录写入。
        repeat(SERIES_COUNT) { i ->
            val title = seriesTitle(i)
            val year = 2000 + i % 24
            for (season in 1..2) {
                val dir = "剧集/$title/S%02d".format(season)
                val files = (1..EPISODES_PER_SEASON).map { ep ->
                    val name = "S%02dE%02d.mp4".format(season, ep)
                    MediaLibraryStore.ScannedFile(
                        path = "$dir/$name",
                        name = name,
                        sizeBytes = 400_000_000L + i,
                        mtime = "seed",
                        title = title,
                        year = year,
                        season = season,
                        episode = ep,
                    )
                }
                store.applyDirectoryListing(SourceId.WEBDAV, dir, files)
            }
            if (i % 100 == 0 && i > 0) {
                samplePss()
                Log.i(TAG, "seed series=$i PSS=${mb(peakPssKb)} MB")
            }
        }
        Log.i(TAG, "seed series=${SERIES_COUNT}x${EPISODES_PER_SEASON * 2} done")

        // 详情：前 300 部电影补类型/国家/演员，供筛选与演员搜索。
        repeat(300) { i ->
            val title = movieTitle(i)
            val year = 1990 + i % 35
            store.saveDetails(
                path = "电影/$title ($year)/$title.mp4",
                tmdbId = 10_000 + i,
                releaseDate = "$year-01-01",
                runtimeMinutes = 100 + i % 40,
                genres = "动画,奇幻",
                country = "日本",
                castJson = """[{"name":"演员甲"},{"name":"演员乙"}]""",
                totalEpisodes = null,
                totalSeasons = null,
                fetchedAt = System.currentTimeMillis(),
            )
        }
        // 收藏与进度：让首页推荐行与进度子查询有真实数据。
        repeat(20) { i ->
            val title = movieTitle(i)
            val year = 1990 + i % 35
            store.toggleFavorite("电影/$title ($year)/$title.mp4", title, year, null, null)
            store.saveProgress(
                "电影/$title ($year)/$title.mp4", title, year, null, null,
                positionMs = 500_000, durationMs = 1_000_000, updatedAt = 1L,
            )
        }
        samplePss()
    }

    private fun movieTitle(i: Int): String =
        if (i % 100 == 0) "Avatar $i" else "${BASES[i % BASES.size]} $i"

    private fun seriesTitle(i: Int): String =
        "${BASES[(i + 7) % BASES.size]} 剧$i"

    // ------------------------------------------------------------------ 测量

    private class Measurement(val p50: Double, val p95: Double, val samples: LongArray, val rowCount: Int)

    private fun loadFirstPage(query: SupportSQLiteQuery): List<WorkCard> = runBlocking {
        val source = db.worksCardDao().pagingWorks(query)
        val result = source.load(PagingSource.LoadParams.Refresh<Int>(null, PAGE_SIZE, false))
        val page = result as PagingSource.LoadResult.Page
        page.data.map { it.toWorkCard() }
    }

    private suspend fun measure(name: String, repeats: Int, block: suspend () -> List<*>): Measurement {
        // 预热一次，排除语句准备与页缓存冷启动。
        val first = block()
        val samples = LongArray(repeats)
        for (i in 0 until repeats) {
            val start = System.nanoTime()
            block()
            samples[i] = System.nanoTime() - start
        }
        val p50 = p50(samples)
        val p95 = p95(samples)
        Log.i(TAG, "$name rows=${first.size} p50=${fmt(p50)} ms p95=${fmt(p95)} ms")
        return Measurement(p50, p95, samples, first.size)
    }

    private data class ScrollStats(
        val pages: Int,
        val rows: Int,
        val totalMs: Long,
        val p50: Double,
        val p95: Double,
    )

    private fun scrollAll(query: SupportSQLiteQuery, pageSize: Int): ScrollStats = runBlocking {
        val source = db.worksCardDao().pagingWorks(query)
        var params: PagingSource.LoadParams<Int> =
            PagingSource.LoadParams.Refresh(null, pageSize, false)
        var pages = 0
        var rows = 0
        val samples = ArrayList<Long>()
        val start = System.nanoTime()
        while (true) {
            val t0 = System.nanoTime()
            val result = source.load(params)
            samples += System.nanoTime() - t0
            check(result is PagingSource.LoadResult.Page) {
                "分页失败: ${(result as? PagingSource.LoadResult.Error)?.throwable}"
            }
            pages++
            rows += result.data.size
            val next = result.nextKey ?: break
            params = PagingSource.LoadParams.Append(next, pageSize, false)
        }
        val all = samples.toLongArray()
        ScrollStats(pages, rows, (System.nanoTime() - start) / 1_000_000, p50(all), p95(all))
    }

    private fun pssKb(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getProcessMemoryInfo(intArrayOf(Process.myPid()))[0].totalPss
    }

    private fun samplePss() {
        val current = pssKb()
        if (current > peakPssKb) peakPssKb = current
    }

    private var peakPssKb = 0

    private fun dbSizeBytes(): Long {
        db.openHelper.writableDatabase.query(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)")).use { }
        val base = context.getDatabasePath(dbName)
        return listOf(base, File("${base.path}-wal"), File("${base.path}-shm"))
            .sumOf { if (it.exists()) it.length() else 0L }
    }

    private fun count(sql: String): Int =
        db.openHelper.readableDatabase.query(sql).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    private fun deleteDbFiles() {
        val base = context.getDatabasePath(dbName)
        listOf(base, File("${base.path}-wal"), File("${base.path}-shm"), File("${base.path}-journal"))
            .forEach { if (it.exists()) it.delete() }
    }

    private fun p50(samples: LongArray): Double =
        samples.sorted()[samples.size / 2] / 1_000_000.0

    private fun p95(samples: LongArray): Double =
        p95(samples.toList())

    private fun p95(samples: List<Long>): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        val index = ((sorted.size - 1) * 0.95).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index] / 1_000_000.0
    }

    private fun ms(since: Long): Long = (System.nanoTime() - since) / 1_000_000

    private fun mb(kb: Int): Double = kb / 1024.0
    private fun mb(bytes: Long): Double = bytes / 1024.0 / 1024.0
    private fun fmt(value: Double): String = String.format(Locale.US, "%.1f", value)

    private companion object {
        const val TAG = "E1.5Stress"
        const val PAGE_SIZE = 30
        const val REPEATS = 20
        const val MOVIE_COUNT = 4_000
        const val SERIES_COUNT = 1_000
        const val EPISODES_PER_SEASON = 8
        const val FILE_COUNT = MOVIE_COUNT + SERIES_COUNT * EPISODES_PER_SEASON * 2
        const val WORK_COUNT = MOVIE_COUNT + SERIES_COUNT
        const val EPISODE_COUNT = SERIES_COUNT * EPISODES_PER_SEASON * 2

        val BASES = listOf(
            "葬送的芙莉莲", "四月是你的谎言", "秒速五厘米", "你的名字", "天气之子",
            "铃芽之旅", "辉夜大小姐想让我告白", "进击的巨人", "鬼灭之刃", "咒术回战",
            "间谍过家家", "紫罗兰永恒花园", "未闻花名", "千与千寻", "龙猫",
            "哈尔的移动城堡", "天空之城", "风之谷", "幽灵公主", "红猪",
            "起风了", "声之形", "言叶之庭", "追逐繁星的孩子", "云之彼端约定的地方",
            "某科学的超电磁炮", "命运石之门", "钢之炼金术师", "死亡笔记", "海贼王",
            "火影忍者", "名侦探柯南", "灌篮高手", "排球少年", "黑子的篮球",
            "东京喰种", "一拳超人", "灵能百分百", "心理测量者", "攻壳机动队",
            "星际牛仔", "新世纪福音战士", "高达", "超时空要塞", "fate",
            "原神", "崩坏", "明日方舟", "碧蓝航线", "战双帕弥什",
        )
    }
}
