package com.lovelev.mediadock.player

import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

/** 同主机多账号的播放鉴权路由。 */
class SourceUrlMatcherTest {

    private fun webdav(id: String, root: String, ok: Boolean = true) = SourceConfig(
        id = id,
        type = SourceType.WEBDAV,
        name = id,
        url = "https://files.example.com/dav",
        username = id,
        password = "pw",
        rootPath = root,
        credentialOk = ok,
    )

    private fun emby(id: String) = SourceConfig(
        id = id,
        type = SourceType.EMBY,
        name = id,
        url = "https://media.example.com:8096",
        apiKey = "token",
    )

    private fun enc(text: String): String = URLEncoder.encode(text, "UTF-8")

    @Test
    fun picksSourceByRootPathOnSameHost() {
        val a = webdav("webdav", "影视A")
        val b = webdav("webdav-b", "影视B")
        val sources = listOf(a, b)
        assertEquals(
            b,
            SourceUrlMatcher.bestMatch(
                sources,
                SourceUrlMatcher.UrlParts(
                    "https", "files.example.com", 443, "/dav/${enc("影视B")}/x.mkv",
                ),
            ),
        )
        assertEquals(
            a,
            SourceUrlMatcher.bestMatch(
                sources,
                SourceUrlMatcher.UrlParts(
                    "https", "files.example.com", 443, "/dav/${enc("影视A")}/x.mkv",
                ),
            ),
        )
    }

    @Test
    fun longestRootWins() {
        val broad = webdav("webdav", "")
        val narrow = webdav("webdav-b", "sub")
        val url = SourceUrlMatcher.UrlParts("https", "files.example.com", 443, "/dav/sub/x.mkv")
        assertEquals(narrow, SourceUrlMatcher.bestMatch(listOf(broad, narrow), url))
    }

    @Test
    fun noMatchOutsideAnyRoot() {
        val sources = listOf(webdav("webdav-b", "影视B"))
        assertNull(
            SourceUrlMatcher.bestMatch(
                sources,
                SourceUrlMatcher.UrlParts("https", "files.example.com", 443, "/other/x.mkv"),
            ),
        )
        assertNull(
            SourceUrlMatcher.bestMatch(
                sources,
                SourceUrlMatcher.UrlParts("https", "other.example.com", 443, "/dav/${enc("影视B")}/x.mkv"),
            ),
        )
    }

    @Test
    fun brokenCredentialIsIgnored() {
        val broken = webdav("webdav-b", "影视B", ok = false)
        assertNull(
            SourceUrlMatcher.bestMatch(
                listOf(broken),
                SourceUrlMatcher.UrlParts("https", "files.example.com", 443, "/dav/${enc("影视B")}/x.mkv"),
            ),
        )
    }

    @Test
    fun embyMatchesByBaseUrlAndDefaultPort() {
        val source = emby("emby-1")
        assertEquals(
            source,
            SourceUrlMatcher.bestMatch(
                listOf(source),
                SourceUrlMatcher.UrlParts(
                    "https", "media.example.com", 8096, "/Videos/123/stream",
                ),
            ),
        )
    }

    @Test
    fun httpDefaultPortIsNormalized() {
        val source = webdav("webdav-b", "影视B").copy(url = "http://files.example.com/dav")
        assertEquals(
            source,
            SourceUrlMatcher.bestMatch(
                listOf(source),
                SourceUrlMatcher.UrlParts("http", "files.example.com", -1, "/dav/${enc("影视B")}/x.mkv"),
            ),
        )
    }
}
