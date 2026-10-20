package com.lovelev.mediadock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 多源刮削身份：旧源保持不带后缀，额外源按源隔离。 */
class ScopedLogicalKeyTest {

    @Test
    fun legacySourcesKeepUnscopedKeys() {
        assertEquals("tmdb:movie:1", LogicalKeyCodec.movie(1))
        assertEquals("tmdb:movie:1", LogicalKeyCodec.movie(1, SourceId.WEBDAV))
        assertEquals("tmdb:movie:1", LogicalKeyCodec.movie(1, SourceId.LOCAL))
        assertEquals("tmdb:tv:2", LogicalKeyCodec.show(2, SourceId.WEBDAV))
    }

    @Test
    fun extraSourcesAreScopedBySourceId() {
        assertEquals(
            "tmdb:movie:1@webdav-b",
            LogicalKeyCodec.movie(1, SourceId("webdav-b")),
        )
        assertEquals(
            "tmdb:tv:2@smb-1",
            LogicalKeyCodec.show(2, SourceId("smb-1")),
        )
        assertNotEquals(
            LogicalKeyCodec.movie(1),
            LogicalKeyCodec.movie(1, SourceId("webdav-b")),
        )
    }
}
