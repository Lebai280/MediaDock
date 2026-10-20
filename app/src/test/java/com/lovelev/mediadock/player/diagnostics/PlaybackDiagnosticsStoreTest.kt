package com.lovelev.mediadock.player.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsStoreTest {

    @Test
    fun `reset keeps media id and clears observations`() {
        val store = PlaybackDiagnosticsStore()
        store.update { it.copy(videoDecoder = "dec", droppedFrames = 3, firstFrameRendered = true) }
        store.reset("path")

        val state = store.state.value
        assertEquals("path", state.mediaId)
        assertNull(state.videoDecoder)
        assertEquals(0, state.droppedFrames)
        assertFalse(state.firstFrameRendered)
    }

    @Test
    fun `update mutates only requested fields and stamps time`() {
        val store = PlaybackDiagnosticsStore()
        store.update { it.copy(audioDecoder = "audio", errorCode = "X/Y") }

        val state = store.state.value
        assertEquals("audio", state.audioDecoder)
        assertEquals("X/Y", state.errorCode)
        assertTrue(state.updatedAt > 0)
    }

    @Test
    fun `clear resets everything`() {
        val store = PlaybackDiagnosticsStore()
        store.reset("path")
        store.clear()
        assertEquals(PlaybackDiagnostics(), store.state.value)
    }
}
