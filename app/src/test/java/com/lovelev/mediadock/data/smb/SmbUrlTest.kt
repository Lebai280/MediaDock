package com.lovelev.mediadock.data.smb

import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbUrlTest {

    private val source = SourceConfig(
        id = "smb-1",
        type = SourceType.SMB,
        name = "NAS",
        url = "192.168.1.10:445",
        username = "u",
        password = "p",
        share = "media",
    )

    @Test
    fun buildsAndParsesRoundTrip() {
        val url = SmbUrl.build(source, "电影/你的名字 (2016).mkv")!!
        assertEquals(
            "smb://192.168.1.10:445/media/%E7%94%B5%E5%BD%B1/%E4%BD%A0%E7%9A%84%E5%90%8D%E5%AD%97%20%282016%29.mkv",
            url,
        )
        val parsed = SmbUrl.parse(url)!!
        assertEquals("192.168.1.10", parsed.host)
        assertEquals(445, parsed.port)
        assertEquals("media", parsed.share)
        assertEquals("电影/你的名字 (2016).mkv", parsed.path)
    }

    @Test
    fun defaultPortAndSchemeVariants() {
        assertEquals("nas", SmbUrl.hostOf("smb://nas/"))
        assertEquals("nas:1445", SmbUrl.hostOf(" nas:1445 "))
        val parsed = SmbUrl.parse("smb://nas/share/a.mkv")!!
        assertEquals(445, parsed.port)
    }

    @Test
    fun matchesHostPortAndShare() {
        val parsed = SmbUrl.parse("smb://192.168.1.10:445/media/x.mkv")!!
        assertTrue(SmbUrl.matches(source, parsed))
        val otherShare = SmbUrl.parse("smb://192.168.1.10:445/other/x.mkv")!!
        assertTrue(!SmbUrl.matches(source, otherShare))
        val otherHost = SmbUrl.parse("smb://192.168.1.11:445/media/x.mkv")!!
        assertTrue(!SmbUrl.matches(source, otherHost))
    }

    @Test
    fun rejectsInvalidUrls() {
        assertNull(SmbUrl.parse("https://host/share/x"))
        assertNull(SmbUrl.parse("smb://host"))
        assertNull(SmbUrl.parse("smb:///share/x"))
    }

    @Test
    fun missingShareReturnsNull() {
        assertNull(SmbUrl.build(source.copy(share = ""), "x.mkv"))
    }

    @Test
    fun literalPlusIsPreserved() {
        val url = SmbUrl.build(source, "a+b.mkv")!!
        assertEquals("a+b.mkv", SmbUrl.parse(url)?.path)
    }
}
