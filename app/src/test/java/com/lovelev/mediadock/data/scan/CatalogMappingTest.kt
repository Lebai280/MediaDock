package com.lovelev.mediadock.data.scan

import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.emby.EmbyClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CatalogMappingTest {

    private val sourceId = SourceId("emby-1")

    private fun item(
        type: String,
        name: String = "名称",
        seriesName: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ) = EmbyClient.CatalogItem(
        itemId = "42",
        type = type,
        name = name,
        seriesId = "7",
        seriesName = seriesName,
        season = season,
        episode = episode,
        year = 2020,
        tmdbId = 123,
        runtimeMs = 1_000L,
        overview = "简介",
        rating = 8.5,
        posterUrl = "http://media/Items/42/Images/Primary",
    )

    @Test
    fun movieBecomesQualifiedPseudoFile() {
        val file = CatalogMapping.file(sourceId, item("Movie", name = "电影"))!!
        assertEquals("item:42", file.path)
        assertEquals("source:emby-1/item:42", file.legacyPath)
        assertEquals("电影", file.title)
        assertEquals("42", file.providerItemId)
        assertNull(file.season)
    }

    @Test
    fun episodeUsesSeriesTitleAndNumbering() {
        val file = CatalogMapping.file(
            sourceId,
            item("Episode", name = "第 1 集", seriesName = "某剧", season = 2, episode = 3),
        )!!
        assertEquals("某剧", file.title)
        assertEquals("第 1 集", file.name)
        assertEquals(2, file.season)
        assertEquals(3, file.episode)
    }

    @Test
    fun seriesContainerIsNotWritten() {
        assertNull(CatalogMapping.file(sourceId, item("Series")))
    }

    @Test
    fun metadataUsesServerFields() {
        val meta = CatalogMapping.meta(sourceId, item("Movie", name = "电影"))
        assertEquals("source:emby-1/item:42", meta.path)
        assertEquals("电影", meta.title)
        assertEquals(8.5, meta.rating)
        assertEquals("http://media/Items/42/Images/Primary", meta.posterUrl)
    }
}
