package com.lovelev.mediadock.spike

import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * S4 spike：真机高规格播放能力矩阵。
 *
 * 1. 显示端支持的 HDR 类型（HDR10 / HDR10+ / HLG / Dolby Vision）。
 * 2. 视频解码器对 HEVC Main10（含 HDR10 / HDR10+ profile）、AV1 10-bit、VP9、DV 的支持。
 * 3. 音频解码器对 AC-3 / E-AC-3 / E-AC-3 JOC（Atmos）/ DTS / TrueHD 的支持。
 * 4. 用合成样本实播，记录 ExoPlayer 实际选中的解码器、colorInfo 与首帧/丢帧。
 *
 * 样本由本地 FFmpeg 生成，位于 androidTest assets/s4；播放前复制到 cacheDir，
 * 避免压缩资源对 seekable 的影响。Media3 要求 Player 只在主线程访问。
 */
@RunWith(AndroidJUnit4::class)
class PlaybackCapabilitiesSpikeTest {

    @Test
    fun displayHdrCapabilities() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val hdr = checkNotNull(display.hdrCapabilities) { "no HDR capabilities" }
        val types = hdr.supportedHdrTypes.joinToString(",") { hdrName(it) }
        Log.i(
            TAG,
            "DISPLAY hdrTypes=[$types] maxLum=${hdr.desiredMaxLuminance} " +
                "maxAvgLum=${hdr.desiredMaxAverageLuminance} minLum=${hdr.desiredMinLuminance}",
        )
        assertTrue("HDR capability query completed", true)
    }

    @Test
    fun codecCapabilities() {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        val videoMimes = setOf(
            MediaFormat.MIMETYPE_VIDEO_HEVC,
            MediaFormat.MIMETYPE_VIDEO_AV1,
            MediaFormat.MIMETYPE_VIDEO_VP9,
            MediaFormat.MIMETYPE_VIDEO_AVC,
            MIME_DOLBY_VISION,
        )
        val audioMimes = setOf(
            MediaFormat.MIMETYPE_AUDIO_AC3,
            MediaFormat.MIMETYPE_AUDIO_EAC3,
            MediaFormat.MIMETYPE_AUDIO_EAC3_JOC,
            MIME_DTS,
            MIME_DTS_HD,
            MIME_TRUEHD,
        )
        for (info in list.codecInfos) {
            if (info.isEncoder) continue
            for (mime in info.supportedTypes) {
                if (mime !in videoMimes && mime !in audioMimes) continue
                val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: continue
                val detail = if (mime in videoMimes) {
                    caps.profileLevels.joinToString(",") {
                        "${videoProfileName(mime, it.profile)}(${it.profile})/L${it.level}"
                    }
                } else {
                    "maxChannels=${caps.audioCapabilities?.maxInputChannelCount ?: -1}"
                }
                Log.i(
                    TAG,
                    "CODEC ${if (mime in videoMimes) "video" else "audio"} " +
                        "name=${info.name} hw=${info.isHardwareAccelerated} " +
                        "mime=$mime $detail",
                )
            }
        }
        assertTrue("Codec enumeration completed", true)
    }

    @Test
    fun playbackMatrix() {
        val samples = listOf(
            Sample("hdr10_hevc_main10.mp4", "HDR10 / HEVC Main10 720p24 (MP4)"),
            Sample("hdr10_hevc_main10.mkv", "HDR10 / HEVC Main10 720p24 (MKV)"),
            Sample("hlg_hevc_main10.mp4", "HLG / HEVC Main10 720p24"),
            Sample("av1_10bit.mp4", "AV1 Main10 720p24"),
            Sample("hdr10_4k60.mp4", "HDR10 / HEVC Main10 4K60"),
            Sample("eac3_51.mp4", "E-AC-3 5.1"),
            Sample("truehd_51.mka", "TrueHD 5.1"),
            Sample("dts_51.mka", "DTS 5.1"),
        )
        samples.forEach { sample ->
            val result = runCatching { playAsset(sample.file) }
                .getOrElse { "PLAYBACK ${sample.file} THREW ${it.javaClass.simpleName}: ${it.message}" }
            Log.i(TAG, "${sample.label}: $result")
        }
        assertTrue("Playback matrix completed", true)
    }

    private fun playAsset(assetName: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val testContext = instrumentation.context
        val mediaFile = File(appContext.cacheDir, "s4-$assetName")
        if (!mediaFile.exists()) {
            testContext.assets.open("s4/$assetName").use { input ->
                mediaFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        var videoDecoder = "-"
        var audioDecoder = "-"
        var videoFormat = "-"
        var audioFormat = "-"
        var firstFrame = false
        var dropped = 0
        var error: String? = null
        var trackInfo = "-"
        var player: ExoPlayer? = null

        instrumentation.runOnMainSync {
            val exo = ExoPlayer.Builder(appContext)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext)))
                .build()
            exo.addAnalyticsListener(object : AnalyticsListener {
                override fun onVideoDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializationDurationMs: Long,
                ) {
                    videoDecoder = decoderName
                }

                override fun onVideoInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?,
                ) {
                    videoFormat = describe(format)
                }

                override fun onAudioDecoderInitialized(
                    eventTime: AnalyticsListener.EventTime,
                    decoderName: String,
                    initializationDurationMs: Long,
                ) {
                    audioDecoder = decoderName
                }

                override fun onAudioInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?,
                ) {
                    audioFormat = describe(format)
                }

                override fun onRenderedFirstFrame(
                    eventTime: AnalyticsListener.EventTime,
                    output: Any,
                    renderTimeMs: Long,
                ) {
                    firstFrame = true
                }

                override fun onDroppedVideoFrames(
                    eventTime: AnalyticsListener.EventTime,
                    droppedFrames: Int,
                    elapsedMs: Long,
                ) {
                    dropped += droppedFrames
                }

                override fun onPlayerError(
                    eventTime: AnalyticsListener.EventTime,
                    e: PlaybackException,
                ) {
                    val chain = buildString {
                        append("${e.errorCodeName}: ${e.message}")
                        var cause = e.cause
                        var depth = 0
                        while (cause != null && depth < 6) {
                            append(" <- ${cause.javaClass.simpleName}: ${cause.message}")
                            cause = cause.cause
                            depth++
                        }
                    }
                    error = chain
                }
            })
            exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(mediaFile)))
            Log.i(TAG, "PLAY $assetName file=${mediaFile.absolutePath} size=${mediaFile.length()}")
            exo.prepare()
            exo.playWhenReady = true
            player = exo
        }

        try {
            var state = Player.STATE_IDLE
            var position = 0L
            val deadline = SystemClock.elapsedRealtime() + WAIT_MS
            while (SystemClock.elapsedRealtime() < deadline && error == null) {
                instrumentation.runOnMainSync {
                    val p = checkNotNull(player)
                    state = p.playbackState
                    position = p.currentPosition
                }
                if (state == Player.STATE_ENDED) break
                if (state == Player.STATE_READY && position >= 2_000) break
                Thread.sleep(200)
            }
            instrumentation.runOnMainSync {
                val p = checkNotNull(player)
                state = p.playbackState
                position = p.currentPosition
                val groups = p.currentTracks.groups
                trackInfo = if (groups.isEmpty()) {
                    "no-tracks"
                } else {
                    groups.joinToString(";") { group ->
                        val format = runCatching { group.getTrackFormat(0) }.getOrNull()
                        "type=${group.type} supported=${group.isSupported} " +
                            "count=${group.length} mime=${format?.sampleMimeType} codecs=${format?.codecs}"
                    }
                }
            }

            val stateName = when (state) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                else -> "ENDED"
            }
            return "state=$stateName pos=$position firstFrame=$firstFrame " +
                "dropped=$dropped videoDecoder=$videoDecoder audioDecoder=$audioDecoder " +
                "videoFormat=$videoFormat audioFormat=$audioFormat tracks=[$trackInfo] error=$error"
        } finally {
            instrumentation.runOnMainSync { player?.release() }
            mediaFile.delete()
        }
    }

    private fun describe(format: Format): String {
        val color = format.colorInfo?.let {
            "colorSpace=${it.colorSpace} colorTransfer=${it.colorTransfer} " +
                "colorRange=${it.colorRange} hdrStatic=${it.hdrStaticInfo != null} " +
                "bitdepth=${it.lumaBitdepth}/${it.chromaBitdepth}"
        } ?: "colorInfo=null"
        return "[mime=${format.sampleMimeType} codecs=${format.codecs} " +
            "${format.width}x${format.height}@${format.frameRate} $color]"
    }

    private fun hdrName(type: Int): String = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "DOLBY_VISION"
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
        Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10_PLUS"
        Display.HdrCapabilities.HDR_TYPE_HLG_PLUS -> "HLG_PLUS"
        else -> "UNKNOWN($type)"
    }

    private fun videoProfileName(mime: String, profile: Int): String = when (mime) {
        MediaFormat.MIMETYPE_VIDEO_HEVC -> when (profile) {
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "Main"
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> "Main10"
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main10HDR10"
            MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main10HDR10Plus"
            else -> "HEVC"
        }
        MediaFormat.MIMETYPE_VIDEO_AV1 -> when (profile) {
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain8 -> "Main8"
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10 -> "Main10"
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10 -> "Main10HDR10"
            MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10Plus -> "Main10HDR10Plus"
            else -> "AV1"
        }
        MediaFormat.MIMETYPE_VIDEO_VP9 -> when (profile) {
            MediaCodecInfo.CodecProfileLevel.VP9Profile0 -> "Profile0"
            MediaCodecInfo.CodecProfileLevel.VP9Profile2 -> "Profile2"
            MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR -> "Profile2HDR"
            MediaCodecInfo.CodecProfileLevel.VP9Profile2HDR10Plus -> "Profile2HDR10Plus"
            else -> "VP9"
        }
        MediaFormat.MIMETYPE_VIDEO_AVC -> when (profile) {
            MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"
            MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"
            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"
            MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High10"
            else -> "AVC"
        }
        MIME_DOLBY_VISION -> "DV"
        else -> "?"
    }

    private data class Sample(val file: String, val label: String)

    private companion object {
        const val TAG = "S4Capabilities"
        const val WAIT_MS = 25_000L
        const val MIME_DOLBY_VISION = "video/dolby-vision"
        const val MIME_DTS = "audio/vnd.dts"
        const val MIME_DTS_HD = "audio/vnd.dts.hd"
        const val MIME_TRUEHD = "audio/true-hd"
    }
}
