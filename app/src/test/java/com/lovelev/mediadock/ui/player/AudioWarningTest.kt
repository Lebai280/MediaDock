package com.lovelev.mediadock.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 无解码器音轨提示的判定（TrueHD / DTS 在目标机上无解码器，见 S4 结论）。 */
class AudioWarningTest {

    @Test
    fun unsupportedSelectedTrackWarnsWithCodecName() {
        assertEquals(
            "当前音轨（TrueHD）无解码器，播放可能无声",
            unsupportedAudioWarning(listOf(status(selected = true, supported = false, codec = "TrueHD"))),
        )
        assertEquals(
            "当前音轨（DTS）无解码器，播放可能无声",
            unsupportedAudioWarning(listOf(status(selected = true, supported = false, codec = "DTS"))),
        )
    }

    @Test
    fun supportedSelectedTrackDoesNotWarn() {
        assertNull(
            unsupportedAudioWarning(listOf(status(selected = true, supported = true, codec = null))),
        )
    }

    @Test
    fun unsupportedTrackNotSelectedWhileSupportedSelectedDoesNotWarn() {
        assertNull(
            unsupportedAudioWarning(
                listOf(
                    status(selected = false, supported = false, codec = "DTS"),
                    status(selected = true, supported = true, codec = null),
                ),
            ),
        )
    }

    @Test
    fun allAudioTracksUnsupportedAndNoneSelectedWarns() {
        // ExoPlayer 不会选不支持的轨道，于是 selected 全为 false —— 仍是静音，必须提示。
        assertEquals(
            "当前音轨（DTS）无解码器，播放可能无声",
            unsupportedAudioWarning(listOf(status(selected = false, supported = false, codec = "DTS"))),
        )
    }

    @Test
    fun supportedTrackNotSelectedDoesNotWarn() {
        // 用户主动关闭音轨（或播放器暂未选）时不误报。
        assertNull(
            unsupportedAudioWarning(listOf(status(selected = false, supported = true, codec = null))),
        )
    }

    @Test
    fun noAudioTracksDoesNotWarn() {
        assertNull(unsupportedAudioWarning(emptyList()))
    }

    @Test
    fun unknownUnsupportedCodecStillWarnsWithoutName() {
        assertEquals(
            "当前音轨无解码器，播放可能无声",
            unsupportedAudioWarning(listOf(status(selected = true, supported = false, codec = null))),
        )
    }

    private fun status(selected: Boolean, supported: Boolean, codec: String?) =
        AudioTrackStatus(selected = selected, supported = supported, codec = codec)
}
