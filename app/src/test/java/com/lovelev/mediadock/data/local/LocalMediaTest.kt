package com.lovelev.mediadock.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本地条目的路径编码。
 *
 * 这几条看着琐碎，但**库里存错前缀的后果是「两个来源互相覆盖」** ——
 * 扫描时按前缀删，前缀认错就会把另一边的条目一起删掉。
 */
class LocalMediaTest {

    @Test
    fun `编码解码是一对往返`() {
        val relative = "Movies/新海诚/秒速五厘米.mp4"
        val encoded = LocalMedia.encode(relative)
        assertEquals("local/Movies/新海诚/秒速五厘米.mp4", encoded)
        assertEquals(relative, LocalMedia.decode(encoded))
    }

    @Test
    fun `开头的斜杠不会编出两个斜杠`() {
        assertEquals("local/a/b.mp4", LocalMedia.encode("/a/b.mp4"))
    }

    @Test
    fun `WebDAV 的路径不会被误认成本地条目`() {
        assertFalse(LocalMedia.isLocal("【新海诚】系列电影/秒速五厘米.mp4"))
        assertNull(LocalMedia.decode("【新海诚】系列电影/秒速五厘米.mp4"))
    }

    @Test
    fun `前缀只是前缀 —— 目录名里出现 local 不算`() {
        // 「localx/...」不是本地条目，别把前缀匹配写成 contains
        assertFalse(LocalMedia.isLocal("localx/a.mp4"))
        assertFalse(LocalMedia.isLocal("a/local/b.mp4"))
    }

    @Test
    fun `document id 拼接符合 SAF 的约定`() {
        assertEquals(
            "primary:Movies/sub/a.mp4",
            LocalMedia.childDocumentId("primary:Movies", "sub/a.mp4"),
        )
        // 相对路径为空就是树根本身
        assertEquals("primary:Movies", LocalMedia.childDocumentId("primary:Movies", ""))
    }

    @Test
    fun `前缀本身是本地条目的合法路径`() {
        // decode 出来是空串 —— 树根，不该当成「不是本地条目」
        assertTrue(LocalMedia.isLocal(LocalMedia.SAF_PREFIX))
        assertEquals("", LocalMedia.decode(LocalMedia.SAF_PREFIX))
    }

    // ---- 系统媒体库那条路（2026-10-16 加）----

    @Test
    fun `媒体库的 URI 也算本地条目 但不该被当成 SAF 路径`() {
        val uri = "content://media/external/video/media/12345"
        assertTrue(LocalMedia.isLocal(uri))
        assertTrue(LocalMedia.isContentUri(uri))
        // decode 只对 SAF 那条有意义；媒体库的 URI 本身就是地址，没有「相对路径」可言
        assertNull(LocalMedia.decode(uri))
    }

    @Test
    fun `toStoredPath —— SAF 加前缀 媒体库原样存`() {
        assertEquals("local/a/b.mp4", LocalMedia.toStoredPath("a/b.mp4"))
        val uri = "content://media/external/video/media/12345"
        assertEquals(uri, LocalMedia.toStoredPath(uri))
    }

    @Test
    fun `WebDAV 路径两个 LIKE 模式都不匹配`() {
        // 这条最要紧：按前缀删的时候，WebDAV 条目不能被误删
        val webdav = "【新海诚】/秒速五厘米.mp4"
        assertFalse(webdav.startsWith(LocalMedia.SAF_PREFIX))
        assertFalse(webdav.startsWith(LocalMedia.CONTENT_SCHEME))
        assertFalse(LocalMedia.isLocal(webdav))
    }
}
