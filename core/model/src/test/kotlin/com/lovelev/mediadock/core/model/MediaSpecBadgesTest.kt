package com.lovelev.mediadock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSpecBadgesTest {

    @Test
    fun `resolution uses the long edge and ignores portrait orientation`() {
        assertEquals("4K", MediaSpecBadges.resolution(3840, 2160))
        assertEquals("4K", MediaSpecBadges.resolution(2160, 3840))
        assertEquals("1080P", MediaSpecBadges.resolution(1920, 1080))
        assertEquals("720P", MediaSpecBadges.resolution(1280, 720))
        assertEquals("8K", MediaSpecBadges.resolution(7680, 4320))
        assertNull(MediaSpecBadges.resolution(720, 480))
        assertNull(MediaSpecBadges.resolution(null, 1080))
    }

    @Test
    fun `hdr badge is supported only when the display supports it`() {
        val video = video(width = 3840, height = 2160, hdr = HdrFormat.HDR10)

        val supported = MediaSpecBadges.badges(video, emptyList(), setOf(HdrFormat.HDR10))
        assertEquals(
            listOf(SpecBadge("4K", BadgeSupport.SUPPORTED), SpecBadge("HDR10", BadgeSupport.SUPPORTED)),
            supported,
        )

        val unsupported = MediaSpecBadges.badges(video, emptyList(), setOf(HdrFormat.HLG))
        assertEquals(BadgeSupport.UNSUPPORTED, unsupported.last().support)
    }

    @Test
    fun `dolby vision badge is unsupported when display lacks dv`() {
        val video = video(width = 3840, height = 2160, hdr = HdrFormat.DOLBY_VISION, dv = true)
        val badges = MediaSpecBadges.badges(video, emptyList(), setOf(HdrFormat.HDR10))
        assertEquals(SpecBadge("DV", BadgeSupport.UNSUPPORTED), badges.last())
    }

    @Test
    fun `atmos only appears for joc tracks`() {
        val joc = audio(isJoc = true)
        val eac3 = audio(isJoc = false)

        val withAtmos = MediaSpecBadges.badges(null, listOf(joc), emptySet())
        assertEquals(listOf(SpecBadge("Atmos", BadgeSupport.UNKNOWN)), withAtmos)

        val withoutAtmos = MediaSpecBadges.badges(null, listOf(eac3), emptySet())
        assertEquals(emptyList<SpecBadge>(), withoutAtmos)
    }

    @Test
    fun `sdr and unknown do not produce hdr badges`() {
        assertEquals(emptyList<SpecBadge>(), MediaSpecBadges.badges(video(hdr = HdrFormat.SDR), emptyList(), emptySet()))
        assertEquals(emptyList<SpecBadge>(), MediaSpecBadges.badges(video(hdr = HdrFormat.UNKNOWN), emptyList(), emptySet()))
    }

    private fun video(
        width: Int? = null,
        height: Int? = null,
        hdr: HdrFormat = HdrFormat.UNKNOWN,
        dv: Boolean = false,
    ) = VideoStreamInfo(
        mime = "video/hevc",
        codec = null,
        width = width,
        height = height,
        frameRate = null,
        bitrate = null,
        profile = null,
        level = null,
        bitDepth = 10,
        hdr = hdr,
        isDolbyVision = dv,
    )

    private fun audio(isJoc: Boolean) = AudioStreamInfo(
        mime = if (isJoc) "audio/eac3-joc" else "audio/eac3",
        codec = null,
        channels = 6,
        sampleRate = 48_000,
        bitrate = null,
        language = "en",
        isJoc = isJoc,
    )
}
