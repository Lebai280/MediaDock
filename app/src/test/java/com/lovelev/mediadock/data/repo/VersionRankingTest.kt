package com.lovelev.mediadock.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionRankingTest {

    @Test
    fun `pinned beats source priority`() {
        val pinned = VersionRank(pinned = true, priority = 100, local = false, sizeBytes = 1, path = "z")
        val preferred = VersionRank(pinned = false, priority = 0, local = false, sizeBytes = 9, path = "a")

        assertEquals(listOf(pinned, preferred), listOf(preferred, pinned).sortedWith(versionOrder()))
    }

    @Test
    fun `lower source priority wins`() {
        val first = VersionRank(pinned = false, priority = 1, local = false, sizeBytes = 1, path = "b")
        val second = VersionRank(pinned = false, priority = 2, local = false, sizeBytes = 9, path = "a")

        assertEquals(listOf(first, second), listOf(second, first).sortedWith(versionOrder()))
    }

    @Test
    fun `local source beats remote at the same priority`() {
        val local = VersionRank(pinned = false, priority = 0, local = true, sizeBytes = 1, path = "z")
        val remote = VersionRank(pinned = false, priority = 0, local = false, sizeBytes = 9, path = "a")

        assertEquals(listOf(local, remote), listOf(remote, local).sortedWith(versionOrder()))
    }

    @Test
    fun `larger file wins then path breaks the tie`() {
        val large = VersionRank(pinned = false, priority = 0, local = false, sizeBytes = 9, path = "z")
        val small = VersionRank(pinned = false, priority = 0, local = false, sizeBytes = 1, path = "a")
        val sameSizeA = VersionRank(pinned = false, priority = 0, local = false, sizeBytes = 9, path = "a")

        assertEquals(
            listOf(sameSizeA, large, small),
            listOf(large, small, sameSizeA).sortedWith(versionOrder()),
        )
    }
}
