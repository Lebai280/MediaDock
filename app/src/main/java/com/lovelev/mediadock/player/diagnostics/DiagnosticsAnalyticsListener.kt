package com.lovelev.mediadock.player.diagnostics

import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.lovelev.mediadock.player.probe.toHdrFormat

/** 只写诊断 store；不改轨道选择、缓冲、音频焦点或错误策略。 */
class DiagnosticsAnalyticsListener(
    private val store: PlaybackDiagnosticsStore,
) : AnalyticsListener {

    override fun onMediaItemTransition(
        eventTime: AnalyticsListener.EventTime,
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        store.reset(mediaItem?.mediaId)
    }

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        store.update { it.copy(videoDecoder = decoderName) }
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        store.update { it.copy(audioDecoder = decoderName) }
    }

    override fun onVideoInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        store.update {
            it.copy(
                videoFormat = summary(format),
                videoHdr = format.toHdrFormat(),
                videoWidth = format.width.takeIf { width -> width > 0 },
                videoHeight = format.height.takeIf { height -> height > 0 },
                videoFrameRate = format.frameRate.takeIf { fps -> fps > 0f },
            )
        }
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: DecoderReuseEvaluation?,
    ) {
        store.update {
            it.copy(
                audioFormat = summary(format),
                audioMime = format.sampleMimeType,
                audioChannels = format.channelCount.takeIf { channels -> channels > 0 },
            )
        }
    }

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long,
    ) {
        store.update { it.copy(firstFrameRendered = true) }
    }

    override fun onDroppedVideoFrames(
        eventTime: AnalyticsListener.EventTime,
        droppedFrames: Int,
        elapsedMs: Long,
    ) {
        store.update { it.copy(droppedFrames = droppedFrames) }
    }

    override fun onPlayerError(eventTime: AnalyticsListener.EventTime, error: PlaybackException) {
        // 只记错误码与异常类名；message 可能含签名 URL，绝不落库/落日志。
        val cause = error.cause?.javaClass?.simpleName
        store.update {
            it.copy(errorCode = listOfNotNull(error.errorCodeName, cause).joinToString("/"))
        }
    }

    private fun summary(format: Format): String = buildList {
        format.sampleMimeType?.let(::add)
        format.codecs?.let(::add)
        if (format.width > 0 && format.height > 0) add("${format.width}x${format.height}")
        if (format.frameRate > 0f) add("${format.frameRate}fps")
        format.colorInfo?.let { color ->
            add("color=${color.colorTransfer}/${color.colorSpace}")
        }
    }.joinToString(" ")
}
