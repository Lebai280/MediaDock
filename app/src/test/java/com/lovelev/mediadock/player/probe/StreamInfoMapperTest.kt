package com.lovelev.mediadock.player.probe

import com.lovelev.mediadock.core.model.HdrFormat
import com.lovelev.mediadock.core.model.ProbeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamInfoMapperTest {

    @Test
    fun `hevc hdr10 plus profile maps to hdr10 plus and 10 bit`() {
        val info = StreamInfoMapper.fromRaw(
            path = "movie.mkv",
            sizeBytes = 10,
            durationMs = 1_000,
            tracks = listOf(
                RawTrack(
                    mime = "video/hevc",
                    codec = "hvc1.2.4.L153.B0",
                    width = 3840,
                    height = 2160,
                    profile = 0x2000,
                    colorTransfer = 6,
                ),
            ),
            now = 1,
        )

        assertEquals(ProbeStatus.OK, info.status)
        assertEquals(HdrFormat.HDR10_PLUS, info.video.single().hdr)
        assertEquals(10, info.video.single().bitDepth)
    }

    @Test
    fun `dolby vision mime wins`() {
        val info = StreamInfoMapper.fromRaw(
            path = "dv.mp4",
            sizeBytes = null,
            durationMs = null,
            tracks = listOf(RawTrack(mime = "video/dolby-vision", codec = "dvhe.05.06")),
            now = 1,
        )
        assertTrue(info.video.single().isDolbyVision)
        assertEquals(HdrFormat.DOLBY_VISION, info.video.single().hdr)
    }

    @Test
    fun `hlg transfer is recognized`() {
        val info = StreamInfoMapper.fromRaw(
            path = "hlg.ts",
            sizeBytes = null,
            durationMs = null,
            tracks = listOf(RawTrack(mime = "video/hevc", colorTransfer = 3)),
            now = 1,
        )
        assertEquals(HdrFormat.HLG, info.video.single().hdr)
    }

    @Test
    fun `atmos only for joc audio`() {
        val info = StreamInfoMapper.fromRaw(
            path = "movie.mkv",
            sizeBytes = null,
            durationMs = null,
            tracks = listOf(
                RawTrack(mime = "audio/eac3", codec = "ec-3", channels = 6),
                RawTrack(mime = "audio/eac3-joc", codec = "ec-3", channels = 16),
            ),
            now = 1,
        )
        assertFalse(info.audio[0].isJoc)
        assertTrue(info.audio[1].isJoc)
    }

    @Test
    fun `subtitle track is separated from audio and video`() {
        val info = StreamInfoMapper.fromRaw(
            path = "movie.mkv",
            sizeBytes = null,
            durationMs = null,
            tracks = listOf(
                RawTrack(mime = "video/hevc"),
                RawTrack(mime = "audio/ac3"),
                RawTrack(mime = "text/vtt", language = "zh", forced = false),
            ),
            now = 1,
        )
        assertEquals(1, info.video.size)
        assertEquals(1, info.audio.size)
        assertEquals("zh", info.subtitles.single().language)
    }

    @Test
    fun `missing color info is unknown not sdr`() {
        val info = StreamInfoMapper.fromRaw(
            path = "movie.mkv",
            sizeBytes = null,
            durationMs = null,
            tracks = listOf(RawTrack(mime = "video/hevc")),
            now = 1,
        )
        assertEquals(HdrFormat.UNKNOWN, info.video.single().hdr)
    }

    @Test
    fun `unknown helper has no tracks and failure`() {
        val info = StreamInfoMapper.unknown(
            path = "x",
            sizeBytes = 1,
            failure = com.lovelev.mediadock.core.model.ProbeFailure.TIMEOUT,
            now = 2,
        )
        assertEquals(ProbeStatus.UNKNOWN, info.status)
        assertTrue(info.video.isEmpty())
        assertEquals("TIMEOUT", info.failure?.name)
        assertNull(info.container)
    }
}
