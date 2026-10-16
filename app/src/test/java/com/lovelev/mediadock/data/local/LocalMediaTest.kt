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
        assertTrue(LocalMedia.isLocal(LocalMedia.PREFIX))
        assertEquals("", LocalMedia.decode(LocalMedia.PREFIX))
    }
}
