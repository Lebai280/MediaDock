package com.lovelev.mediadock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourcePathsTest {

    @Test
    fun legacyWebDavAndLocalStayBare() {
        assertEquals("电影/片.mkv", SourcePaths.qualify(SourceId.WEBDAV, "电影/片.mkv"))
        assertEquals("local/片.mkv", SourcePaths.qualify(SourceId.LOCAL, "local/片.mkv"))
    }

    @Test
    fun extraSourcesAreQualified() {
        val ref = SourcePaths.qualify(SourceId("webdav-abc"), "电影/片.mkv")
        assertEquals("source:webdav-abc/电影/片.mkv", ref)
        assertEquals(SourceId("webdav-abc"), SourcePaths.parse(ref)?.sourceId)
        assertEquals("电影/片.mkv", SourcePaths.parse(ref)?.key)
    }

    @Test
    fun barePathsAreNotParsed() {
        assertNull(SourcePaths.parse("电影/片.mkv"))
        assertNull(SourcePaths.parse("local/片.mkv"))
        assertNull(SourcePaths.parse("content://media/external/video/media/1"))
        assertEquals(SourceId.WEBDAV, SourcePaths.sourceIdOf("电影/片.mkv"))
        assertEquals("电影/片.mkv", SourcePaths.keyOf("电影/片.mkv"))
    }

    @Test
    fun unknownPrefixIsTreatedAsPlainPath() {
        val path = "source:not-registered/电影/片.mkv"
        // 应用层按注册表判断：未注册 → 不当作限定路径
        assertNull(SourcePaths.parseKnown(path, setOf("webdav-abc")))
        assertEquals("电影/片.mkv", SourcePaths.keyOf(path))
        // 语法层仍能解析出 id，写层据此得知它确实是限定形式
        assertEquals("not-registered", SourcePaths.parse(path)?.sourceId?.value)
        assertEquals(SourceId("not-registered"), SourcePaths.sourceIdOf(path))
    }

    @Test
    fun malformedPrefixFallsBack() {
        assertNull(SourcePaths.parse("source:"))
        assertNull(SourcePaths.parse("source:abc"))
        assertNull(SourcePaths.parse("source:abc/"))
        assertNull(SourcePaths.parse("source:ABC/片.mkv"))
        assertNull(SourcePaths.parse("source:abc:def/片.mkv"))
    }

    @Test
    fun keyMayContainColonsAndSlashes() {
        val parsed = SourcePaths.parse("source:smb-1/共享/目录/片:1.mkv")
        assertEquals("共享/目录/片:1.mkv", parsed?.key)
    }
}
