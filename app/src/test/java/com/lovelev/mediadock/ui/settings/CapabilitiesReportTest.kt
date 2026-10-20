package com.lovelev.mediadock.ui.settings

import com.lovelev.mediadock.core.model.AudioOutputCapabilities
import com.lovelev.mediadock.core.model.DeviceCapabilities
import com.lovelev.mediadock.core.model.DeviceIdentity
import com.lovelev.mediadock.core.model.DisplayCapabilities
import com.lovelev.mediadock.core.model.HdrFormat
import com.lovelev.mediadock.player.diagnostics.PlaybackDiagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesReportTest {

    @Test
    fun `report omits source paths urls and credentials`() {
        val device = DeviceCapabilities(
            identity = DeviceIdentity("Samsung", "SM-G998U1", "p3q", 35, "15", listOf("arm64-v8a")),
            display = DisplayCapabilities(setOf(HdrFormat.HDR10), 1000f, 600f, 0.1f, null, emptyList()),
            videoDecoders = emptyList(),
            audioDecoders = emptyList(),
            audioOutputs = emptyList(),
            audioOutputCapabilities = AudioOutputCapabilities(8, emptyList()),
            probedAt = 1,
        )
        val playback = PlaybackDiagnostics(
            mediaId = "source:secret-source/影视/电影/movie.mkv",
            videoDecoder = "c2.qti.hevc.decoder",
        )

        val report = CapabilitiesReport.build(device, playback)

        assertTrue(report.contains("movie.mkv"))
        assertTrue(report.contains("HDR10"))
        assertFalse(report.contains("secret-source"))
        assertFalse(report.contains("http"))
        assertFalse(report.contains("@"))
        assertFalse(report.contains("password", ignoreCase = true))
    }

    @Test
    fun `null device still renders playback section`() {
        val report = CapabilitiesReport.build(null, PlaybackDiagnostics(mediaId = "a/b/c.mp4"))
        assertTrue(report.contains("设备能力：尚未读取"))
        assertTrue(report.contains("c.mp4"))
    }
}
