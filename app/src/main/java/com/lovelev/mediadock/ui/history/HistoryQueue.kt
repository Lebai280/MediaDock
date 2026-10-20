package com.lovelev.mediadock.ui.history

import com.lovelev.mediadock.data.local.MediaItemEntity

/**
 * 同目录视频作为播放队列，和从浏览页点进来的行为一致（自动下一集）。
 * 库里没有同目录条目时退回单条路径，保证永远能播。
 */
internal fun historyPlayQueue(path: String, items: List<MediaItemEntity>): List<String> {
    val folder = path.substringBeforeLast('/', "")
    return items
        .filter { it.path.substringBeforeLast('/', "") == folder }
        .map { it.path }
        .ifEmpty { listOf(path) }
}
