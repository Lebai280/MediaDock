package com.lovelev.mediadock.data.repo

/** 一个物理版本：同一作品/分集可能来自不同数据源。 */
data class PlayableVersion(
    val path: String,
    val name: String,
    val sourceId: String,
    val sourceName: String,
    val sizeBytes: Long?,
    val pinned: Boolean,
)
