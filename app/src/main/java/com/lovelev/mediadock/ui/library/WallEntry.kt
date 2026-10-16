package com.lovelev.mediadock.ui.library

import com.lovelev.mediadock.data.local.MediaItemEntity

/**
 * 海报墙上的一块：要么是一部单片，要么是一部剧（旗下若干集）。
 */
sealed interface WallEntry {
    /** 排序与去重用。 */
    val key: String

    /** 拿它取海报/标题/评分 —— 同一部剧各集的刮削结果是一样的。 */
    val sample: MediaItemEntity

    data class Movie(override val sample: MediaItemEntity) : WallEntry {
        override val key: String = sample.path
    }

    data class Series(val episodes: List<MediaItemEntity>) : WallEntry {
        override val sample: MediaItemEntity = episodes.first()
        override val key: String = episodes.first().path.substringBeforeLast('/', "")
    }
}

/** 集数排序：先季、再集、最后名字兜底。 */
fun List<MediaItemEntity>.orderedEpisodes(): List<MediaItemEntity> =
    sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }, { it.name }))

/**
 * 把媒体条目收成海报墙的块。
 *
 * **只对「识别出集数」的条目做归并，而且必须是同一个目录里的 ≥2 集**：
 *  - 不能简单按目录分组 —— 「豆瓣电影Top250」这类合集目录里平铺着一堆电影，
 *    按目录分会把几百部电影并成一块。
 *  - 单单一条「有集数」的条目（比如片名里碰巧匹配到 E 数字）不算剧，还是当单片，
 *    否则会出现只有 1 集的「剧」。
 *
 * 已知不覆盖的情况：同一部剧被拆到 `Season 1/`、`Season 2/` 两个目录时，
 * 会显示成两块。真遇到再改（可以用 `tmdbId` 做二次合并）。
 */
fun groupWall(items: List<MediaItemEntity>): List<WallEntry> {
    val (numbered, standalone) = items.partition { it.episode != null }

    val series = numbered
        .groupBy { it.path.substringBeforeLast('/', "") }
        .filterValues { it.size >= 2 }
        .map { (_, episodes) -> WallEntry.Series(episodes.orderedEpisodes()) }

    val groupedPaths = series.flatMapTo(HashSet()) { entry -> entry.episodes.map { it.path } }
    val movies = standalone.map { WallEntry.Movie(it) } +
        numbered.filter { it.path !in groupedPaths }.map { WallEntry.Movie(it) }

    return (movies + series).sortedBy { it.key }
}
