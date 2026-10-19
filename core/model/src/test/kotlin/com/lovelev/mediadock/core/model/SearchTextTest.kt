package com.lovelev.mediadock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchTextTest {

    @Test
    fun `索引文本把 CJK 逐字加空格`() {
        assertEquals("葬 送 的 芙 莉 莲", SearchText.index("葬送的芙莉莲"))
        assertEquals("四 月 是 你 的 谎 言 Season 1", SearchText.index("四月是你的谎言", "Season 1"))
    }

    @Test
    fun `中文查询转成逐字短语`() {
        assertEquals("\"芙 莉 莲\"", SearchText.query("芙莉莲"))
        assertEquals("\"四 月\" \"谎 言\"", SearchText.query("四月 谎言"))
    }

    @Test
    fun `拉丁查询按前缀`() {
        assertEquals("avatar*", SearchText.query("avatar"))
        assertEquals("blade* runner*", SearchText.query("blade runner"))
    }

    @Test
    fun `空查询返回 null`() {
        assertNull(SearchText.query("   "))
    }

    @Test
    fun `混合词保留中文短语与拉丁前缀`() {
        assertEquals("\"芙 莉 莲\" season*", SearchText.query("芙莉莲 season"))
    }
}
