package com.lovelev.mediadock.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用例全部取自**用户库里真实存在的文件名/目录名**（2026-10-15 实际列举出来的），
 * 不是编出来的漂亮例子。
 */
class TitleParserTest {

    @Test
    fun `纪录片 集数用 EP 标记 中文名在文件名里`() {
        val p = TitleParser.parse(
            fileName = "河西走廊.HeXi.Corridor.2015.EP01.4K.X264.AAC.Mandarin.CHS.HQC.mp4",
            folderName = "1.河西走廊",
        )
        assertEquals("河西走廊", p.title)
        assertEquals(2015, p.year)
        assertEquals(1, p.episode)
        assertNull(p.season)
    }

    @Test
    fun `电影 中文名只在方括号目录上`() {
        val p = TitleParser.parse(
            fileName = "A.Bug's.Life.1998.1080p.mkv",
            folderName = "[虫虫危机].A.Bug's.Life.1998.BluRay.1080p.x264.DTS-WiKi",
        )
        assertEquals("虫虫危机", p.title)
        assertEquals(1998, p.year)
        assertNull(p.episode)
    }

    @Test
    fun `文件只有英文名时 靠目录名里的中文`() {
        val p = TitleParser.parse(
            fileName = "Beckham.S01E01.1080p.WEB-DL.mkv",
            folderName = "贝克汉姆 Beckham (2023)",
        )
        assertEquals("贝克汉姆", p.title)
        assertEquals(2023, p.year)
        assertEquals(1, p.season)
        assertEquals(1, p.episode)
    }

    @Test
    fun `季数写在目录里的 Season N`() {
        val p = TitleParser.parse(
            fileName = "Drive.to.Survive.S05E01.mkv",
            folderName = "一级方程式：疾速争胜 第五季 高帧率 Formula 1：Drive to Survive Season 5 HFR (2023)",
        )
        assertEquals("一级方程式", p.title)
        assertEquals(2023, p.year)
        assertEquals(5, p.season)
        assertEquals(1, p.episode)
    }

    @Test
    fun `普通中文电影`() {
        val p = TitleParser.parse("你的名字.2016.1080p.BluRay.x265.mkv")
        assertEquals("你的名字", p.title)
        assertEquals(2016, p.year)
    }

    @Test
    fun `标题自带句号的 全角句号要去掉`() {
        val p = TitleParser.parse("你的名字。.2016.1080p.mkv")
        assertEquals("你的名字", p.title)
        assertEquals(2016, p.year)
    }

    @Test
    fun `纯英文电影 目录没中文`() {
        val p = TitleParser.parse("Onward.mkv", folderName = "[½Öþ]Onward.2020.1080p.BluRay.x264.DTS-WiKi")
        assertEquals("Onward", p.title)
        assertEquals(2020, p.year)
    }

    @Test
    fun `纯英文电影 文件名自己带全部信息`() {
        val p = TitleParser.parse("Up.2009.BluRay.1080p.x264.mkv")
        assertEquals("Up", p.title)
        assertEquals(2009, p.year)
    }

    @Test
    fun `破折号连着的发布组 整段丢掉 但标题里的破折号要留`() {
        val p = TitleParser.parse("Spider-Man.2019.1080p.BluRay.x264-DTS-GROUP.mkv")
        assertEquals("Spider-Man", p.title)
        assertEquals(2019, p.year)
    }

    @Test
    fun `片名就是年份时 别把标题解析成空`() {
        val p = TitleParser.parse("1917.2019.1080p.BluRay.mkv")
        assertTrue("标题不该为空，实际=${p.title}", p.title.isNotBlank())
        assertTrue("标题里应含 1917，实际=${p.title}", p.title.contains("1917"))
    }

    @Test
    fun `目录带序号前缀 序号不是片名`() {
        val p = TitleParser.parse("12.片名.2018.1080p.mkv")
        assertEquals("片名", p.title)
        assertEquals(2018, p.year)
    }

    @Test
    fun `没有年份也要能出标题`() {
        val p = TitleParser.parse("逍遥法外.Catch.Me.If.You.Can.mkv")
        assertEquals("逍遥法外", p.title)
        assertNull(p.year)
    }

    @Test
    fun `扩展到 1x01 写法的集数`() {
        val p = TitleParser.parse("某剧.2020.1x03.1080p.mkv")
        assertEquals(1, p.season)
        assertEquals(3, p.episode)
    }

    @Test
    fun `方括号里是分类标签时 别当成片名`() {
        // 用户库里大量这种命名：【电影】/【动画】只是分类，真正的片名在后面
        val p = TitleParser.parse("【电影】秒速五厘米.简日双语特效.2007.1080P.mp4")
        assertEquals("秒速五厘米", p.title)
        assertEquals(2007, p.year)
    }
}
