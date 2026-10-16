package com.lovelev.mediadock.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendReasonTest {

    private fun reason(
        genres: List<String> = emptyList(),
        rating: Double? = null,
        progress: Float? = null,
        favorite: Boolean = false,
        favoriteGenres: Map<String, String> = emptyMap(),
    ) = recommendReason(genres, rating, progress, favorite, favoriteGenres)

    @Test
    fun `收藏过的排最前`() {
        assertEquals(
            "你收藏过的",
            reason(genres = listOf("动画"), rating = 9.0, progress = 0.5f, favorite = true),
        )
    }

    @Test
    fun `看了一半就提示接着看`() {
        assertEquals("看到 62% · 接着看", reason(progress = 0.62f, rating = 9.0))
    }

    @Test
    fun `刚开始几秒不算看过`() {
        // 1% 不算「看到一半」，应该落到下一条（这里是高分）
        assertEquals("★9.0 高分", reason(progress = 0.01f, rating = 9.0))
    }

    @Test
    fun `高分优先于同类型`() {
        assertEquals(
            "★8.4 高分",
            reason(genres = listOf("动画"), rating = 8.4, favoriteGenres = mapOf("动画" to "老磨坊")),
        )
    }

    @Test
    fun `和收藏同类型时说出是哪一部`() {
        assertEquals(
            "你收藏的《老磨坊》也是动画",
            reason(genres = listOf("动画", "家庭"), rating = 7.2, favoriteGenres = mapOf("动画" to "老磨坊")),
        )
    }

    @Test
    fun `口碑不错的档位`() {
        assertEquals("★7.6 口碑不错", reason(rating = 7.6))
    }

    @Test
    fun `什么都没有就只有类型`() {
        assertEquals("动画 · 家庭", reason(genres = listOf("动画", "家庭", "喜剧")))
    }

    @Test
    fun `连类型都没有就返回空串 界面不画那行`() {
        assertEquals("", reason())
    }

    @Test
    fun `评分四舍五入到一位小数`() {
        assertEquals("★8.0 高分", reason(rating = 7.96))
    }
}
