package com.lovelev.mediadock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityTest {

    @Test
    fun `逻辑键编解码往返`() {
        val keys = listOf(
            LogicalKey.Movie(100),
            LogicalKey.Show(200),
            LogicalKey.Episode(200, 2, 29),
            LogicalKey.File(SourceId.WEBDAV, "动漫/葬送的芙莉莲/S01/01.mp4"),
            LogicalKey.File(SourceId.LOCAL, "content://media/external/video/media/123"),
        )
        keys.forEach { key ->
            assertEquals("往返失败：$key", key, LogicalKeyCodec.parse(key.encoded))
        }
    }

    @Test
    fun `非法逻辑键返回 null`() {
        listOf(
            "",
            "tmdb",
            "tmdb:movie",
            "tmdb:movie:abc",
            "tmdb:tv:1:sx:e1",
            "file:",
            "file:webdav:",
            "unknown:1:2",
        ).forEach { assertNull("应解析失败：$it", LogicalKeyCodec.parse(it)) }
    }

    @Test
    fun `远程键归一化保留 URI scheme`() {
        assertEquals(
            "content://media/external/video/media/123",
            normalizeRemoteKey(" content://media//external/video/media/123/ "),
        )
        assertEquals(
            "动漫/葬送的芙莉莲/S01/01.mp4",
            normalizeRemoteKey("动漫//葬送的芙莉莲/S01/01.mp4/"),
        )
        assertEquals("local/电影/片.mp4", normalizeRemoteKey("./local\\电影\\片.mp4"))
    }

    @Test
    fun `NFC 归一化对组合字符生效`() {
        val decomposed = "é.mp4" // e + 组合重音
        val composed = "é.mp4" // é
        assertEquals(normalizeRemoteKey(composed), normalizeRemoteKey(decomposed))
    }

    @Test
    fun `确定性 ID 稳定且区分来源`() {
        val a = ContentIds.file(SourceId.WEBDAV, "a.mp4")
        val b = ContentIds.file(SourceId.WEBDAV, "a.mp4")
        val c = ContentIds.file(SourceId.LOCAL, "a.mp4")
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(ContentIds.work("tmdb:movie:1"), ContentIds.work("tmdb:movie:1"))
    }

    @Test
    fun `SourceId 不允许冒号`() {
        runCatching { SourceId("a:b") }.onSuccess {
            throw AssertionError("带冒号的 SourceId 应被拒绝")
        }
    }
}
