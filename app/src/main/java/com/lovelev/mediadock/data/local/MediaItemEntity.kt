package com.lovelev.mediadock.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 刮削状态。未刮过 / 匹配上了 / 匹配失败（失败的下次刮削会再试）。 */
object ScrapeStatus {
    const val UNSCRAPED = "UNSCRAPED"
    const val MATCHED = "MATCHED"
    const val FAILED = "FAILED"
}

/**
 * 媒体库里的一条（目前只收视频文件）。
 *
 * [path] 是**相对数据源根**的路径，天然唯一，直接拿它当主键 ——
 * 既不用再发一个自增 id，又能原样喂回 `WebDavUrl.child()` 重建播放地址。
 *
 * 表是**缓存**：结构变了直接丢表重扫（见 AppContainer 里的 destructive migration），
 * 所以这里不做迁移、也不怕加列。
 */
@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val path: String,
    val name: String,
    val sizeBytes: Long?,
    val lastModified: String?,

    // 扫描时顺手解析出来的（纯本地计算，不花钱），海报墙先用它显示，
    // 刮削不上的条目也不至于显示成一长串文件名
    val parsedTitle: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,

    // 刮削结果
    val scrapeState: String = ScrapeStatus.UNSCRAPED,
    val tmdbId: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    val scrapedAt: Long? = null,
)
