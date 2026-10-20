package com.lovelev.mediadock.player.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.lovelev.mediadock.core.model.HdrFormat

/** 最近一次播放的运行时观察值；只含解码器名、格式摘要与错误码，不含 URL/凭据。 */
data class PlaybackDiagnostics(
    val mediaId: String? = null,
    val videoDecoder: String? = null,
    val audioDecoder: String? = null,
    val videoFormat: String? = null,
    val audioFormat: String? = null,
    val videoHdr: HdrFormat? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val videoFrameRate: Float? = null,
    val audioMime: String? = null,
    val audioChannels: Int? = null,
    val droppedFrames: Int = 0,
    val firstFrameRendered: Boolean = false,
    val errorCode: String? = null,
    val updatedAt: Long = 0,
)

class PlaybackDiagnosticsStore {

    private val _state = MutableStateFlow(PlaybackDiagnostics())
    val state: StateFlow<PlaybackDiagnostics> = _state.asStateFlow()

    fun reset(mediaId: String?) {
        _state.value = PlaybackDiagnostics(mediaId = mediaId, updatedAt = System.currentTimeMillis())
    }

    fun clear() {
        _state.value = PlaybackDiagnostics()
    }

    fun update(transform: (PlaybackDiagnostics) -> PlaybackDiagnostics) {
        _state.update { transform(it).copy(updatedAt = System.currentTimeMillis()) }
    }
}
