package com.lovelev.mediadock.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSettingsParsingTest {

    @Test
    fun `refresh matching defaults to auto and parses off`() {
        assertEquals(RefreshMatchingMode.AUTO, parseRefreshMatching(null))
        assertEquals(RefreshMatchingMode.AUTO, parseRefreshMatching("garbage"))
        assertEquals(RefreshMatchingMode.OFF, parseRefreshMatching("OFF"))
    }

    @Test
    fun `audio channel cap defaults to auto and parses variants`() {
        assertEquals(AudioChannelCap.AUTO, parseAudioChannelCap(null))
        assertEquals(AudioChannelCap.AUTO, parseAudioChannelCap("garbage"))
        assertEquals(AudioChannelCap.SURROUND, parseAudioChannelCap("SURROUND"))
        assertEquals(AudioChannelCap.STEREO, parseAudioChannelCap("STEREO"))
        assertEquals(Int.MAX_VALUE, AudioChannelCap.AUTO.max)
        assertEquals(6, AudioChannelCap.SURROUND.max)
        assertEquals(2, AudioChannelCap.STEREO.max)
    }

    @Test
    fun `subtitle size defaults to normal and parses variants`() {
        assertEquals(SubtitleSize.NORMAL, parseSubtitleSize(null))
        assertEquals(SubtitleSize.NORMAL, parseSubtitleSize("garbage"))
        assertEquals(SubtitleSize.SMALL, parseSubtitleSize("SMALL"))
        assertEquals(SubtitleSize.HUGE, parseSubtitleSize("HUGE"))
        assertEquals(0.8f, SubtitleSize.SMALL.scale)
        assertEquals(1f, SubtitleSize.NORMAL.scale)
        assertEquals(1.5f, SubtitleSize.HUGE.scale)
    }
}
