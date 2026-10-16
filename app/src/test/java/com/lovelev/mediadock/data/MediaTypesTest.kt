package com.lovelev.mediadock.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 字幕配对是纯逻辑，用例照旧取真实/常见的命名。 */
class MediaTypesTest {

    @Test
    fun `字幕名是视频名的前缀 真实命名`() {
        // 用户库里真实存在的那种命名
        val video = "河西走廊.HeXi.Corridor.2015.EP01.4K.X264.AAC.Mandarin.CHS.HQC.mp4"
        assertTrue(MediaTypes.matchesSubtitle(video, "河西走廊.HeXi.Corridor.2015.EP01.4K.X264.AAC.Mandarin.CHS.srt"))
    }

    @Test
    fun `视频名是字幕名的前缀 带语言后缀的常见写法`() {
        assertTrue(MediaTypes.matchesSubtitle("电影.2020.1080p.mkv", "电影.2020.1080p.chs.ass"))
        assertTrue(MediaTypes.matchesSubtitle("电影.mkv", "电影.chs.ass"))
    }

    @Test
    fun `同名不同扩展名`() {
        assertTrue(MediaTypes.matchesSubtitle("你的名字.2016.mkv", "你的名字.2016.srt"))
    }

    @Test
    fun `Ep1 不能被 Ep10 的字幕认领`() {
        assertFalse(MediaTypes.matchesSubtitle("剧.Ep1.mkv", "剧.Ep10.srt"))
        assertFalse(MediaTypes.matchesSubtitle("剧.Ep10.mkv", "剧.Ep1.srt"))
    }

    @Test
    fun `别的片子别的集不算`() {
        assertFalse(MediaTypes.matchesSubtitle("A剧.EP01.mkv", "B剧.EP01.srt"))
        assertFalse(MediaTypes.matchesSubtitle("A剧.EP01.mkv", "A剧.EP02.srt"))
    }

    @Test
    fun `非字幕扩展名一律不认`() {
        assertFalse(MediaTypes.matchesSubtitle("电影.mkv", "电影.jpg"))
        assertFalse(MediaTypes.matchesSubtitle("电影.mkv", "电影.mkv"))
    }

    @Test
    fun `从文件名读分辨率标签`() {
        assertEquals("4K", MediaTypes.resolutionLabel("河西走廊.HeXi.Corridor.2015.EP01.4K.X264.AAC.mp4"))
        assertEquals("1080P", MediaTypes.resolutionLabel("电影.2020.1080p.BluRay.x264.mkv"))
        assertEquals("1080P", MediaTypes.resolutionLabel("电影.2020.1080i.mkv"))
        assertEquals("720P", MediaTypes.resolutionLabel("电影.720p.mkv"))
        assertEquals("2160p".let { "4K" }, MediaTypes.resolutionLabel("电影.2160p.mkv"))
        assertNull(MediaTypes.resolutionLabel("电影.2016.mkv"))
        // 不能被别的数字骗到：10800 里没有 1080
        assertNull(MediaTypes.resolutionLabel("片名.10800.mkv"))
    }

    @Test
    fun `视频与字幕扩展名判定`() {
        assertTrue(MediaTypes.isVideoFile("x.MKV"))
        assertTrue(MediaTypes.isSubtitleFile("x.SRT"))
        assertTrue(MediaTypes.isSubtitleFile("x.ass"))
        assertFalse(MediaTypes.isSubtitleFile("x.mkv"))
        assertFalse(MediaTypes.isVideoFile("x.srt"))
    }
}
