package com.lovelev.mediadock.data.scan

import com.lovelev.mediadock.data.local.LocalFile
import com.lovelev.mediadock.data.local.LocalMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalFileMapperTest {

    @Test
    fun `saf file gets stored prefix and parsed metadata`() {
        val mapped = LocalFile(
            key = "贝克汉姆 Beckham (2023)/Beckham.S01E01.1080p.WEB-DL.mkv",
            name = "Beckham.S01E01.1080p.WEB-DL.mkv",
            sizeBytes = 123L,
            lastModified = 456L,
            folderName = "贝克汉姆 Beckham (2023)",
        ).toScannedFile()

        assertEquals(
            LocalMedia.SAF_PREFIX + "贝克汉姆 Beckham (2023)/Beckham.S01E01.1080p.WEB-DL.mkv",
            mapped.path,
        )
        assertEquals(123L, mapped.sizeBytes)
        assertEquals("456", mapped.mtime)
        assertEquals("贝克汉姆", mapped.title)
        assertEquals(2023, mapped.year)
        assertEquals(1, mapped.season)
        assertEquals(1, mapped.episode)
    }

    @Test
    fun `content uri is kept as is`() {
        val uri = "content://media/external/video/42"
        val mapped = LocalFile(
            key = uri,
            name = "Up.mkv",
            sizeBytes = 1L,
            lastModified = 2L,
        ).toScannedFile()

        assertEquals(uri, mapped.path)
        assertEquals("Up", mapped.title)
        assertNull(mapped.year)
        assertNull(mapped.season)
        assertNull(mapped.episode)
    }

    @Test
    fun `season is backfilled from the directory when filename only has an episode`() {
        val mapped = LocalFile(
            key = "剧集/S02/05.mp4",
            name = "05.mp4",
            sizeBytes = 1L,
            lastModified = 1L,
            folderName = "S02",
        ).toScannedFile()

        assertEquals(2, mapped.season)
        assertEquals(5, mapped.episode)
    }
}
