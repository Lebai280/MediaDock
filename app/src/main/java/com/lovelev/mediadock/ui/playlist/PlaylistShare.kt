package com.lovelev.mediadock.ui.playlist

import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaylistItem

enum class PlaylistSort(val label: String) {
    DEFAULT("默认顺序"),
    TITLE("按名称"),
    YEAR("按年份"),
}

internal fun sortPlaylistRows(
    rows: List<PlaylistItem>,
    byPath: Map<String, MediaItemEntity>,
    sort: PlaylistSort,
): List<PlaylistItem> = when (sort) {
    PlaylistSort.DEFAULT -> rows
    PlaylistSort.TITLE -> rows.sortedBy {
        byPath[it.path]?.parsedTitle?.lowercase() ?: it.path.lowercase()
    }
    PlaylistSort.YEAR -> rows.sortedByDescending { byPath[it.path]?.year ?: 0 }
}

internal fun buildPlaylistShareText(
    name: String,
    sortedRows: List<PlaylistItem>,
    byPath: Map<String, MediaItemEntity>,
): String = buildString {
    appendLine("片单：${name.ifBlank { "片单" }}")
    sortedRows.forEachIndexed { index, row ->
        val item = byPath[row.path]
        val title = item?.parsedTitle?.takeIf { it.isNotBlank() }
            ?: row.path.substringAfterLast('/')
        val year = item?.year?.let { " ($it)" }.orEmpty()
        appendLine("${index + 1}. $title$year — ${row.path}")
    }
}
