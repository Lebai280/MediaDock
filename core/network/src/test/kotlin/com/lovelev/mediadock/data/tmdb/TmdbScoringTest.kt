package com.lovelev.mediadock.data.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TmdbScoringTest {

    private fun result(
        id: Int,
        title: String,
        year: Int? = null,
        original: String? = null,
        poster: String? = "/p.jpg",
        popularity: Double = 10.0,
    ) = SearchResult(
        id = id,
        title = title,
        original_title = original,
        release_date = year?.let { "$it-01-01" },
        poster_path = poster,
        popularity = popularity,
    )

    @Test
    fun exactTitleAndYearBeatsPopularWrongResult() {
        val results = listOf(
            result(1, "芙莉莲", 2023, popularity = 500.0),
            result(2, "葬送的芙莉莲", 2023, popularity = 5.0),
        )
        assertEquals(2, TmdbScoring.pick("葬送的芙莉莲", 2023, results)?.id)
    }

    @Test
    fun shortQueryDoesNotMatchUnrelatedTitle() {
        val results = listOf(result(1, "完全无关的影片", 2020))
        assertNull(TmdbScoring.pick("星际", null, results))
    }

    @Test
    fun yearOflByOneStillAccepted() {
        val results = listOf(result(1, "某部电影", 2019))
        assertNotNull(TmdbScoring.pick("某部电影", 2020, results))
    }

    @Test
    fun originalTitleIsConsidered() {
        val results = listOf(result(1, "千与千寻", 2001, original = "Spirited Away"))
        assertEquals(1, TmdbScoring.pick("Spirited Away", null, results)?.id)
    }

    @Test
    fun punctuationAndSpacesAreIgnored() {
        val results = listOf(result(1, "Spider-Man: No Way Home", 2021))
        assertEquals(1, TmdbScoring.pick("spider man no way home", 2021, results)?.id)
    }

    @Test
    fun variantsStripSeasonBracketsAndColonTail() {
        val variants = TmdbScoring.queryVariants("葬送的芙莉莲 第2季")
        assertEquals("葬送的芙莉莲", variants[1])
        val bracket = TmdbScoring.queryVariants("【合集】某剧 (2020)")
        assertEquals("某剧", bracket[1])
        val colon = TmdbScoring.queryVariants("某剧：副标题")
        assertEquals("某剧", colon[1])
    }

    @Test
    fun noTitleRelationIsRejectedEvenWithExactYear() {
        val results = listOf(result(1, "别的片子", 2020))
        assertNull(TmdbScoring.pick("我的片子", 2020, results))
    }
}
