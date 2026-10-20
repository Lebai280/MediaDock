package com.lovelev.mediadock.ui.playlist

import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaylistItem
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistShareTest {

    private val rows = listOf(
        row("b", 2),
        row("a", 3),
        row("c", 1),
    )

    private val byPath = mapOf(
        "a" to item("a", "Zebra", 1999),
        "b" to item("b", "apple", 2020),
        "c" to item("c", "", null),
    )

    @Test
    fun `default keeps playlist order`() {
        assertEquals(rows, sortPlaylistRows(rows, byPath, PlaylistSort.DEFAULT))
    }

    @Test
    fun `title sorts case-insensitively`() {
        // 既有行为：parsedTitle 为空串时不回退到 path，空串排最前。
        assertEquals(
            listOf("c", "b", "a"),
            sortPlaylistRows(rows, byPath, PlaylistSort.TITLE).map { it.path },
        )
    }

    @Test
    fun `year sorts descending with missing year last`() {
        assertEquals(
            listOf("b", "a", "c"),
            sortPlaylistRows(rows, byPath, PlaylistSort.YEAR).map { it.path },
        )
    }

    @Test
    fun `share text numbers rows and includes year`() {
        val text = buildPlaylistShareText("周末", sortPlaylistRows(rows, byPath, PlaylistSort.TITLE), byPath)
        assertEquals(
            "片单：周末\n1. c — c\n2. apple (2020) — b\n3. Zebra (1999) — a\n",
            text,
        )
    }

    @Test
    fun `blank name falls back to default title`() {
        assertEquals("片单：片单\n", buildPlaylistShareText(" ", emptyList(), emptyMap()))
    }

    private fun row(path: String, addedAt: Long) = PlaylistItem(
        playlistId = 1L,
        path = path,
        addedAt = addedAt,
    )

    private fun item(path: String, title: String, year: Int?) = MediaItemEntity(
        path = path,
        name = path,
        sizeBytes = null,
        lastModified = null,
        parsedTitle = title,
        year = year,
        season = null,
        episode = null,
    )
}
