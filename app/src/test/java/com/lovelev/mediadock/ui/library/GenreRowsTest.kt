package com.lovelev.mediadock.ui.library

import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenreRowsTest {

    private fun item(path: String) = MediaItemEntity(
        path = path,
        name = path.substringAfterLast('/'),
        sizeBytes = null,
        lastModified = null,
        parsedTitle = path.substringAfterLast('/').substringBefore('.'),
        year = 2020,
        season = null,
        episode = null,
    )

    private fun detail(path: String, genres: String) = TitleDetail(
        path = path,
        tmdbId = 1,
        releaseDate = null,
        runtimeMinutes = null,
        genres = genres,
        country = null,
        castJson = "[]",
        fetchedAt = 0L,
    )

    /** 造 count 条「动画」+ [others] 条别的，返回 (items, details)。 */
    private fun library(count: Int, others: Int = 0): Pair<List<MediaItemEntity>, List<TitleDetail>> {
        val items = (1..count).map { item("a/$it.mkv") } + (1..others).map { item("b/$it.mkv") }
        val details = (1..count).map { detail("a/$it.mkv", "动画,短片") } +
            (1..others).map { detail("b/$it.mkv", "剧情") }
        return items to details
    }

    @Test
    fun `没有详情就不出分类行（懒加载没补过的正常表现）`() {
        val (items, _) = library(5)
        assertEquals(emptyList<GenreRow>(), genreRows(items, emptyList()))
    }

    @Test
    fun `少于三条的类型不成行`() {
        val (items, details) = library(count = 2, others = 3)
        val rows = genreRows(items, details)
        // 动画只有 2 条 → 不出；剧情 3 条 → 出
        assertEquals(listOf("剧情"), rows.map { it.genre })
    }

    @Test
    fun `按预设优先级排 不是按数量排`() {
        // 剧情 5 条、动画 3 条：数量上剧情多，但动画在优先级表里更靠前
        val items = (1..3).map { item("a/$it.mkv") } + (1..5).map { item("b/$it.mkv") }
        val details = (1..3).map { detail("a/$it.mkv", "动画") } +
            (1..5).map { detail("b/$it.mkv", "剧情") }
        assertEquals(listOf("动画", "剧情"), genreRows(items, details).map { it.genre })
    }

    @Test
    fun `一行最多六条 超出的截掉`() {
        // 八个类型各 3 条，优先级表里有它们的顺序是 动画 喜剧 家庭 剧情 短片 冒险 音乐 纪录片
        val genres = listOf("动画", "喜剧", "家庭", "剧情", "短片", "冒险", "音乐", "纪录片")
        val items = mutableListOf<MediaItemEntity>()
        val details = mutableListOf<TitleDetail>()
        genres.forEachIndexed { gi, g ->
            (1..3).forEach { i ->
                items += item("g$gi/$i.mkv")
                details += detail("g$gi/$i.mkv", g)
            }
        }
        val rows = genreRows(items, details)
        assertEquals(MAX_GENRE_ROWS, rows.size)
        assertEquals(listOf("动画", "喜剧", "家庭", "剧情", "短片", "冒险"), rows.map { it.genre })
    }

    @Test
    fun `一条多类型会被多行都收录`() {
        val items = (1..3).map { item("a/$it.mkv") }
        val details = (1..3).map { detail("a/$it.mkv", "动画,家庭,喜剧") }
        assertEquals(listOf("动画", "喜剧", "家庭"), genreRows(items, details).map { it.genre })
    }

    @Test
    fun `行内项目被截断时 total 仍是真实总数`() {
        val (items, details) = library(count = MAX_PER_GENRE + 5)
        val row = genreRows(items, details).first { it.genre == "动画" }
        assertEquals(MAX_PER_GENRE, row.items.size)
        assertEquals(MAX_PER_GENRE + 5, row.total)
    }

    @Test
    fun `不属于优先级表的类型不出现`() {
        val items = (1..4).map { item("a/$it.mkv") }
        val details = (1..4).map { detail("a/$it.mkv", "真人秀,脱口秀") }
        assertTrue(genreRows(items, details).isEmpty())
    }

    @Test
    fun `空库返回空`() {
        assertEquals(emptyList<GenreRow>(), genreRows(emptyList(), emptyList()))
    }
}
