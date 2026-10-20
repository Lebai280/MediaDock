package com.lovelev.mediadock.ui.history

import com.lovelev.mediadock.data.local.MediaItemEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryQueueTest {

    @Test
    fun `same folder becomes the queue in library order`() {
        val items = listOf(
            item("/影库/剧集/第1集.mp4"),
            item("/影库/剧集/第2集.mp4"),
            item("/影库/电影/别处.mp4"),
        )

        assertEquals(
            listOf("/影库/剧集/第1集.mp4", "/影库/剧集/第2集.mp4"),
            historyPlayQueue("/影库/剧集/第1集.mp4", items),
        )
    }

    @Test
    fun `content uri with no siblings falls back to the single path`() {
        val path = "content://media/external/video/42"
        assertEquals(listOf(path), historyPlayQueue(path, emptyList()))
    }

    @Test
    fun `only same folder is included`() {
        val items = listOf(
            item("a/b.mp4"),
            item("a/c.mp4"),
            item("a/sub/d.mp4"),
        )

        assertEquals(listOf("a/b.mp4", "a/c.mp4"), historyPlayQueue("a/b.mp4", items))
    }

    @Test
    fun `selected path is kept even when library has no match`() {
        val items = listOf(item("x/y.mp4"))
        assertEquals(listOf("other/z.mp4"), historyPlayQueue("other/z.mp4", items))
    }

    private fun item(path: String) = MediaItemEntity(
        path = path,
        name = path.substringAfterLast('/'),
        sizeBytes = null,
        lastModified = null,
        parsedTitle = path.substringAfterLast('/'),
        year = null,
        season = null,
        episode = null,
    )
}
