package com.lovelev.mediadock.ui.library

import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchTest {

    private fun item(path: String, title: String, name: String = title) = MediaItemEntity(
        path = path,
        name = name,
        sizeBytes = null,
        lastModified = null,
        parsedTitle = title,
        year = 2020,
        season = null,
        episode = null,
    )

    private fun detail(
        path: String,
        genres: String = "",
        country: String? = null,
        cast: List<String> = emptyList(),
    ) = TitleDetail(
        path = path,
        tmdbId = 1,
        releaseDate = null,
        runtimeMinutes = null,
        genres = genres,
        country = country,
        castJson = cast.joinToString(",", "[", "]") { """{"name":"$it"}""" },
        fetchedAt = 0L,
    )

    private val oldMill = item("奥斯卡/老磨坊.mp4", "老磨坊")
    private val quXiaoya = item("皮克斯/丑小鸭.mp4", "丑小鸭")
    private val galactic = item("合集/银河护卫队3.mp4", "银河护卫队3")

    private val items = listOf(oldMill, quXiaoya, galactic)

    @Test
    fun `空查询返回全部`() {
        assertEquals(items, filterLibraryItems(items, emptyList(), ""))
        assertEquals(items, filterLibraryItems(items, emptyList(), "   "))
    }

    @Test
    fun `按片名匹配`() {
        val hit = filterLibraryItems(items, emptyList(), "老磨坊")
        assertEquals(listOf(oldMill), hit)
    }

    @Test
    fun `片名匹配忽略大小写 也匹配原始文件名`() {
        val titanic = item("A/Titanic.1997.mkv", "泰坦尼克号", name = "Titanic.1997.mkv")
        assertEquals(listOf(titanic), filterLibraryItems(listOf(titanic), emptyList(), "titanic"))
    }

    @Test
    fun `按类型匹配（要详情）`() {
        val details = listOf(detail(galactic.path, genres = "动作,科幻,冒险"))
        val hit = filterLibraryItems(items, details, "科幻")
        assertEquals(listOf(galactic), hit)
    }

    @Test
    fun `按国家匹配`() {
        val details = listOf(detail(oldMill.path, country = "美国"))
        assertEquals(listOf(oldMill), filterLibraryItems(items, details, "美国"))
    }

    @Test
    fun `按演职人员匹配`() {
        val details = listOf(detail(galactic.path, cast = listOf("克里斯·帕拉特", "佐伊·索尔达娜")))
        assertEquals(listOf(galactic), filterLibraryItems(items, details, "佐伊"))
    }

    @Test
    fun `多个关键词要全中`() {
        val details = listOf(
            detail(galactic.path, genres = "动作,科幻", country = "美国"),
            detail(quXiaoya.path, genres = "动画", country = "美国"),
        )
        // 「美国 动画」只该命中丑小鸭：银河护卫队是美国片但不是动画
        assertEquals(listOf(quXiaoya), filterLibraryItems(items, details, "美国 动画"))
    }

    @Test
    fun `没有详情时按类型搜不到 但按片名照常`() {
        // 详情是懒加载的，没补齐就搜不到类型 —— 这条锁住的是「不会因为缺详情而崩」
        assertEquals(emptyList<MediaItemEntity>(), filterLibraryItems(items, emptyList(), "科幻"))
        assertEquals(listOf(galactic), filterLibraryItems(items, emptyList(), "银河"))
    }
}
