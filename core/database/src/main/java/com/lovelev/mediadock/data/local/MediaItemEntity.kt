package com.lovelev.mediadock.data.local

import androidx.room.DatabaseView

/** 刮削状态。未刮过 / 匹配上了 / 匹配失败（失败的下次刮削会再试）。 */
object ScrapeStatus {
    const val UNSCRAPED = "UNSCRAPED"
    const val MATCHED = "MATCHED"
    const val FAILED = "FAILED"
}

/**
 * 媒体库的一条 —— **v9 起是只读兼容 VIEW**，数据来自 `works` / `episodes` / `media_files`
 * 与各自的版本表。列名保持 v8 原样，现有 `MediaDao` 与界面查询不变。
 *
 * 一个物理文件在视图里恰好一行；多集文件取 `segment_index` 最小的那一集。
 */
@DatabaseView(viewName = "media_items", value = ViewSql.MEDIA_ITEMS)
data class MediaItemEntity(
    val path: String,
    val name: String,
    val sizeBytes: Long?,
    val lastModified: String?,
    val parsedTitle: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
    val scrapeState: String = ScrapeStatus.UNSCRAPED,
    val tmdbId: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val scrapedAt: Long? = null,
)
