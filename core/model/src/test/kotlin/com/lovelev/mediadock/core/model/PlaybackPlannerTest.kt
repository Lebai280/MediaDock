package com.lovelev.mediadock.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPlannerTest {

    private val device = DeviceCapabilities(
        identity = DeviceIdentity("samsung", "SM-G998U1", "p3q", 35, "15", listOf("arm64-v8a")),
        display = DisplayCapabilities(
            hdrFormats = setOf(HdrFormat.HDR10, HdrFormat.HLG, HdrFormat.HDR10_PLUS),
            maxLuminance = 1000f,
            maxAverageLuminance = 600f,
            minLuminance = 0.1f,
            currentMode = null,
            supportedModes = emptyList(),
        ),
        videoDecoders = listOf(
            VideoDecoderCapability(
                mime = "video/hevc",
                decoderName = "c2.qti.hevc.decoder",
                hardware = true,
                softwareOnly = false,
                profiles = emptyList(),
                maxWidth = 7680,
                maxHeight = 4320,
                maxFrameRate = 60f,
            ),
        ),
        audioDecoders = listOf(
            AudioDecoderCapability("audio/ac3", "decoder", true, 6),
            AudioDecoderCapability("audio/eac3", "decoder", true, 8),
        ),
        audioOutputs = emptyList(),
        audioOutputCapabilities = AudioOutputCapabilities(
            maxChannelCount = 8,
            passthroughMimes = emptySet(),
        ),
        probedAt = 1,
    )

    @Test
    fun `supported hdr is direct and hardware decoder is reported`() {
        val plan = PlaybackPlanner.plan(
            PlaybackPlanInput(HdrFormat.HDR10, frameRate = 23.976f, videoMime = "video/hevc"),
            device,
            refreshMatching = true,
            maxAudioChannels = Int.MAX_VALUE,
        )
        assertEquals(HdrMode.DIRECT, plan.hdrMode)
        assertEquals(true, plan.hardwareVideoDecoder)
        assertTrue(plan.refreshMatching)
    }

    @Test
    fun `unsupported hdr is marked unsupported`() {
        val plan = PlaybackPlanner.plan(
            PlaybackPlanInput(HdrFormat.DOLBY_VISION, isDolbyVision = true),
            device,
            refreshMatching = false,
            maxAudioChannels = Int.MAX_VALUE,
        )
        assertEquals(HdrMode.UNSUPPORTED, plan.hdrMode)
        assertTrue(plan.notes.any { it.contains("DV") })
    }

    @Test
    fun `sdr stays sdr`() {
        val plan = PlaybackPlanner.plan(
            PlaybackPlanInput(HdrFormat.SDR),
            device,
            refreshMatching = true,
            maxAudioChannels = Int.MAX_VALUE,
        )
        assertEquals(HdrMode.SDR, plan.hdrMode)
    }

    @Test
    fun `audio chooses passthrough when declared then decode then no decoder`() {
        val passthroughDevice = device.copy(
            audioOutputCapabilities = device.audioOutputCapabilities.copy(
                passthroughMimes = setOf("audio/eac3"),
            ),
        )
        assertEquals(
            AudioMode.PASSTHROUGH,
            PlaybackPlanner.plan(
                PlaybackPlanInput(HdrFormat.SDR, audioMime = "audio/eac3"),
                passthroughDevice, true, Int.MAX_VALUE,
            ).audioMode,
        )
        assertEquals(
            AudioMode.DECODE,
            PlaybackPlanner.plan(
                PlaybackPlanInput(HdrFormat.SDR, audioMime = "audio/ac3"),
                device, true, Int.MAX_VALUE,
            ).audioMode,
        )
        val noDecoder = PlaybackPlanner.plan(
            PlaybackPlanInput(HdrFormat.SDR, audioMime = "audio/truehd"),
            device, true, Int.MAX_VALUE,
        )
        assertEquals(AudioMode.NO_DECODER, noDecoder.audioMode)
        assertTrue(noDecoder.notes.any { it.contains("静音") })
    }

    @Test
    fun `channel cap adds a note when the track exceeds it`() {
        val plan = PlaybackPlanner.plan(
            PlaybackPlanInput(HdrFormat.SDR, audioMime = "audio/eac3", audioChannels = 8),
            device, true, maxAudioChannels = 2,
        )
        assertTrue(plan.notes.any { it.contains("声道上限 2") })
        assertFalse(plan.notes.any { it.contains("无解码器") })
    }
}
