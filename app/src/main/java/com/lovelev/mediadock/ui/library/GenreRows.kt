package com.lovelev.mediadock.ui.library

import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail

/**
 * 首页的一条分类行。
 *
 * @param total 这一类**总共有多少条**（[items] 是截断后的展示用列表）——
 *   标题上显示的是 [total]，不是 [items].size，别把截断当成真实数量。
 */
data class GenreRow(
    val genre: String,
    val items: List<MediaItemEntity>,
    val total: Int,
)

/** 首页分类行的**预设优先级**：同类影音 App 里这几种最常见，也最贴用户的库。 */
private val GENRE_PRIORITY = listOf(
    "动画", "喜剧", "家庭", "剧情", "短片", "冒险",
    "音乐", "纪录片", "历史", "战争", "动作", "科幻", "悬疑", "爱情",
)

/** 每条分类行最多放几部（横滑，再多也没人翻到底）。 */
internal const val MAX_PER_GENRE = 15

/** 首页最多几条分类行。 */
internal const val MAX_GENRE_ROWS = 6

/** 少于这个数就不单独成行（一条两条例出来像是数据坏了）。 */
internal const val MIN_PER_GENRE = 3

/**
 * 从「已缓存的详情」里按类型切出首页的分类行。**纯逻辑，有单测**（`GenreRowsTest`）。
 *
 * 注意：类型来自 `title_details`，那张表是懒加载的（打开详情页才拉 / 「补齐影片信息」补）——
 * **没补过就出不来分类行**，这是预期行为，不是 bug（见 WORKLOG 坑 57）。
 */
internal fun genreRows(
    items: List<MediaItemEntity>,
    details: List<TitleDetail>,
): List<GenreRow> {
    if (items.isEmpty() || details.isEmpty()) return emptyList()

    val byPath = details.associateBy { it.path }
    val genresOf: Map<String, Set<String>> = items.associate { item ->
        val genres = byPath[item.path]?.genres
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
        item.path to genres
    }

    return GENRE_PRIORITY
        .mapNotNull { genre ->
            val hits = items.filter { genre in genresOf[it.path].orEmpty() }
            if (hits.size < MIN_PER_GENRE) {
                null
            } else {
                GenreRow(genre = genre, items = hits.take(MAX_PER_GENRE), total = hits.size)
            }
        }
        .take(MAX_GENRE_ROWS)
}
