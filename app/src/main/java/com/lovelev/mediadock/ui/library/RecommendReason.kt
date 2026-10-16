package com.lovelev.mediadock.ui.library

import java.util.Locale

/**
 * 首页大卡片下面那行「推荐理由」。**纯逻辑，有单测**（`RecommendReasonTest`）。
 *
 * 规矩：**只用真数据推出理由**，推不出来就返回空串（界面那一行不画）。
 * 优先级从「最贴这个人」到「最泛」：
 *  1. 收藏过 → 「你收藏过的」
 *  2. 看了一半 → 「看到 62% · 接着看」
 *  3. 高分（≥8.0）→ 「★8.4 高分」
 *  4. 和收藏里某部同类型 → 「你收藏的《X》也是这个类型」
 *  5. 口碑不错（≥7.0）→ 「★7.6 口碑不错」
 *  6. 都没有但有类型 → 直接把类型摆出来当信息（不是理由，但总比空着强）
 *
 * 刻意**不编**「猜你喜欢」这种没有依据的话。
 */
internal fun recommendReason(
    genres: List<String>,
    rating: Double?,
    /** 看过多少，0..1；没看过传 null。 */
    progress: Float?,
    isFavorite: Boolean,
    /** 收藏里出现过的类型（用来做第 4 条）。 */
    favoriteGenres: Map<String, String>,
): String {
    if (isFavorite) return "你收藏过的"

    if (progress != null && progress > PROGRESS_FLOOR) {
        val percent = (progress * 100).toInt().coerceIn(1, 99)
        return "看到 $percent% · 接着看"
    }

    // 用**四舍五入到一位小数之后**的分判档位 —— 否则会出现「★8.0 口碑不错」这种自相矛盾的话
    val shown = rating?.let { String.format(Locale.US, "%.1f", it).toDoubleOrNull() }

    if (shown != null && shown >= HIGH_RATING) {
        return "★${formatRating(shown)} 高分"
    }

    favoriteGenres.entries.firstOrNull { (genre, _) -> genre in genres }?.let { (genre, title) ->
        return "你收藏的《$title》也是$genre"
    }

    if (shown != null && shown >= GOOD_RATING) {
        return "★${formatRating(shown)} 口碑不错"
    }

    return genres.take(2).joinToString(" · ")
}

/** 五分之一以上才算「看到一半」，一进去就退出的不算。 */
private const val PROGRESS_FLOOR = 0.02f
private const val HIGH_RATING = 8.0
private const val GOOD_RATING = 7.0

private fun formatRating(rating: Double): String = String.format(Locale.US, "%.1f", rating)
