package com.lovelev.mediadock.player.probe

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.lovelev.mediadock.core.model.ProbeFailure
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.player.WebDavLoadErrorPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface ExtractOutcome {
    data class Ok(val tracks: List<RawTrack>, val durationMs: Long?) : ExtractOutcome
    data class Failed(val failure: ProbeFailure) : ExtractOutcome
}

/**
 * 用一个独立的 ExoPlayer 做只读 prepare，等 `currentTracks` 出现后读 Format。
 * 不设置 Surface、音量 0、不播放；复用播放数据源与 401/429 策略。
 */
internal class StreamExtractor(
    private val context: Context,
    private val factory: DataSource.Factory,
) {

    suspend fun extract(
        uri: Uri,
        declaredSize: Long?,
        timeoutMs: Long = 15_000,
    ): ExtractOutcome = withContext(Dispatchers.Main.immediate) {
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(factory)
                    .setLoadErrorHandlingPolicy(WebDavLoadErrorPolicy()),
            )
            .build()
        val result = CompletableDeferred<ExtractOutcome>()
        val timeout = launch {
            delay(timeoutMs)
            result.complete(ExtractOutcome.Failed(ProbeFailure.TIMEOUT))
        }
        try {
            player.volume = 0f
            player.playWhenReady = false
            player.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    if (tracks.groups.any { it.length > 0 }) {
                        finish(player, result)
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY &&
                        player.currentTracks.groups.any { it.length > 0 }
                    ) {
                        finish(player, result)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val cause = error.cause?.javaClass?.simpleName
                    AppLog.d(
                        "probe",
                        "extract size=$declaredSize error=${error.errorCodeName}" +
                            (cause?.let { "/$it" } ?: ""),
                    )
                    result.complete(ExtractOutcome.Failed(ProbeFailure.IO))
                }
            })
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            result.await()
        } finally {
            timeout.cancel()
            player.release()
        }
    }

    private fun finish(player: ExoPlayer, result: CompletableDeferred<ExtractOutcome>) {
        if (result.isCompleted) return
        // 给音频/字幕轨一点时间一起上报，避免只拿到视频轨就收工。
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main.immediate).launch {
            delay(400)
            if (result.isCompleted) return@launch
            val formats = player.currentTracks.groups
                .filter { it.length > 0 }
                .map { it.getTrackFormat(0) }
            if (formats.isEmpty()) return@launch
            result.complete(
                ExtractOutcome.Ok(
                    tracks = formats.map { it.toRawTrack() },
                    durationMs = null,
                ),
            )
        }
    }

    private fun Format.toRawTrack(): RawTrack = RawTrack(
        mime = sampleMimeType,
        codec = codecs,
        width = width.takeIf { it != Format.NO_VALUE },
        height = height.takeIf { it != Format.NO_VALUE },
        frameRate = frameRate.takeIf { it != Format.NO_VALUE.toFloat() },
        bitrate = bitrate.takeIf { it > 0 },
        colorTransfer = colorInfo?.colorTransfer,
        colorStandard = colorInfo?.colorSpace,
        hasHdrStaticInfo = colorInfo?.hdrStaticInfo != null,
        channels = channelCount.takeIf { it > 0 },
        sampleRate = sampleRate.takeIf { it > 0 },
        bitrateAudio = bitrate.takeIf { it > 0 },
        language = language,
    )
}
