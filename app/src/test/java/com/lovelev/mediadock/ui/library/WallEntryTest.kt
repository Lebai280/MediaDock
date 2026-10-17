package com.lovelev.mediadock.ui.library

import com.lovelev.mediadock.data.local.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallEntryTest {

    private fun item(path: String, episode: Int? = null, season: Int? = null) = MediaItemEntity(
        path = path,
        name = path.substringAfterLast('/'),
        sizeBytes = null,
        lastModified = null,
        parsedTitle = path.substringAfterLast('/').substringBefore('.'),
        year = 2020,
        season = season,
        episode = episode,
    )

    @Test
    fun `同一目录的多集归成一部剧 并按集数排序`() {
        val wall = groupWall(
            listOf(
                item("知乎/1.河西走廊/河西走廊.EP03.mkv", episode = 3),
                item("知乎/1.河西走廊/河西走廊.EP01.mkv", episode = 1),
                item("知乎/1.河西走廊/河西走廊.EP02.mkv", episode = 2),
            ),
        )
        assertEquals(1, wall.size)
        val series = wall[0] as WallEntry.Series
        assertEquals(listOf(1, 2, 3), series.episodes.map { it.episode })
        assertEquals("知乎/1.河西走廊", series.key)
    }

    @Test
    fun `平铺的电影合集不会被并成一块`() {
        // 「豆瓣Top250」这类目录：几百部电影直接躺在同一个目录里
        val wall = groupWall(
            listOf(
                item("豆瓣/肖申克的救赎.1994.mkv"),
                item("豆瓣/霸王别姬.1993.mkv"),
                item("豆瓣/阿甘正传.1994.mkv"),
            ),
        )
        assertEquals(3, wall.size)
        assertTrue(wall.all { it is WallEntry.Movie })
    }

    @Test
    fun `目录里只有一集的不算剧`() {
        val wall = groupWall(listOf(item("皮克斯/[虫虫危机]/虫虫危机.EP01.mkv", episode = 1)))
        assertEquals(1, wall.size)
        assertTrue(wall[0] is WallEntry.Movie)
    }

    @Test
    fun `单片与剧共存 剧排在自己目录的位置上`() {
        val wall = groupWall(
            listOf(
                item("合集/1.甲.2020.mkv"),
                item("合集/2.乙剧/乙剧.EP01.mkv", episode = 1),
                item("合集/2.乙剧/乙剧.EP02.mkv", episode = 2),
                item("合集/3.丙.2021.mkv"),
            ),
        )
        assertEquals(3, wall.size)
        assertEquals(
            listOf("合集/1.甲.2020.mkv", "合集/2.乙剧", "合集/3.丙.2021.mkv"),
            wall.map { it.key },
        )
    }

    @Test
    fun `分季的剧按季再按集排`() {
        val wall = groupWall(
            listOf(
                item("剧/X.S02E01.mkv", episode = 1, season = 2),
                item("剧/X.S01E02.mkv", episode = 2, season = 1),
                item("剧/X.S01E01.mkv", episode = 1, season = 1),
                item("剧/X.S02E02.mkv", episode = 2, season = 2),
            ),
        )
        val series = wall.single() as WallEntry.Series
        assertEquals(listOf(1 to 1, 1 to 2, 2 to 1, 2 to 2), series.episodes.map { it.season to it.episode })
    }

    @Test
    fun `两个不同目录的剧不会互相合并`() {
        val wall = groupWall(
            listOf(
                item("A/剧一/D.EP01.mkv", episode = 1),
                item("A/剧一/D.EP02.mkv", episode = 2),
                item("A/剧二/E.EP01.mkv", episode = 1),
                item("A/剧二/E.EP02.mkv", episode = 2),
            ),
        )
        assertEquals(2, wall.size)
        assertEquals(listOf("A/剧一", "A/剧二"), wall.map { it.key })
    }

    @Test
    fun `跨 S01 S02 目录的同一部剧并成一块`() {
        // 用户的真实结构：动漫/葬送的芙莉莲/S01/简日双语.喵萌奶茶屋/葬送的芙莉莲01.mp4
        val wall = groupWall(
            listOf(
                item("动漫/葬送的芙莉莲/S01/简日双语.喵萌奶茶屋/葬送的芙莉莲01.mp4", episode = 1, season = 1),
                item("动漫/葬送的芙莉莲/S01/简日双语.喵萌奶茶屋/葬送的芙莉莲02.mp4", episode = 2, season = 1),
                item("动漫/葬送的芙莉莲/S02/简日双语.喵萌奶茶屋/葬送的芙莉莲01.mp4", episode = 1, season = 2),
            ),
        )
        val series = wall.single() as WallEntry.Series
        assertEquals("动漫/葬送的芙莉莲", series.key)
        assertEquals(
            listOf(1 to 1, 1 to 2, 2 to 1),
            series.episodes.map { it.season to it.episode },
        )
    }
}
