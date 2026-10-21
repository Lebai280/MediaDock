package com.lovelev.mediadock.player.capabilities

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioCapabilities
import com.lovelev.mediadock.core.model.AudioDecoderCapability
import com.lovelev.mediadock.core.model.AudioOutputCapabilities
import com.lovelev.mediadock.core.model.AudioOutputDevice
import com.lovelev.mediadock.core.model.DecoderProfile
import com.lovelev.mediadock.core.model.DeviceCapabilities
import com.lovelev.mediadock.core.model.DeviceIdentity
import com.lovelev.mediadock.core.model.DisplayCapabilities
import com.lovelev.mediadock.core.model.DisplayModeInfo
import com.lovelev.mediadock.core.model.HdrFormat
import com.lovelev.mediadock.core.model.VideoDecoderCapability
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 设备声明能力。昂贵枚举只在诊断页打开时执行，结果进程内缓存。 */
class DeviceCapabilityProbe(private val context: Context) {

    @Volatile
    private var cached: DeviceCapabilities? = null

    suspend fun probe(force: Boolean = false): DeviceCapabilities {
        if (!force) cached?.let { return it }
        return withContext(Dispatchers.Default) {
            build().also { cached = it }
        }
    }

    fun invalidate() {
        cached = null
    }

    /** 详情页徽标只需要显示 HDR 列表，不触发完整 codec 枚举。 */
    fun displayHdrFormats(): Set<HdrFormat> {
        val hdr = runCatching {
            context.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)
                ?.hdrCapabilities
        }.getOrNull()
        return hdr?.supportedHdrTypes
            ?.toList()
            ?.mapNotNull(::hdrFormat)
            ?.toSet()
            .orEmpty()
    }

    private fun build(): DeviceCapabilities {
        val display = displayCapabilities()
        val (systemVideo, systemAudio) = codecCapabilities()
        return DeviceCapabilities(
            identity = DeviceIdentity(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                model = Build.MODEL.orEmpty(),
                device = Build.DEVICE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                release = Build.VERSION.RELEASE.orEmpty(),
                abis = Build.SUPPORTED_ABIS.toList(),
            ),
            display = display,
            videoDecoders = withFfmpegVideo(systemVideo),
            audioDecoders = withFfmpegAudio(systemAudio),
            audioOutputs = audioOutputs(),
            audioOutputCapabilities = audioOutputCapabilities(),
            probedAt = System.currentTimeMillis(),
        )
    }

    private fun displayCapabilities(): DisplayCapabilities {
        val display = runCatching {
            context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        }.getOrNull()
        val hdr = runCatching { display?.hdrCapabilities }.getOrNull()
        val hdrFormats = hdr?.supportedHdrTypes
            ?.toList()
            ?.mapNotNull(::hdrFormat)
            ?.toSet()
            .orEmpty()
        val modes = runCatching {
            display?.supportedModes?.map {
                DisplayModeInfo(it.physicalWidth, it.physicalHeight, it.refreshRate)
            }.orEmpty()
        }.getOrDefault(emptyList())
        val current = runCatching {
            display?.mode?.let { DisplayModeInfo(it.physicalWidth, it.physicalHeight, it.refreshRate) }
        }.getOrNull()
        return DisplayCapabilities(
            hdrFormats = hdrFormats,
            maxLuminance = hdr?.desiredMaxLuminance,
            maxAverageLuminance = hdr?.desiredMaxAverageLuminance,
            minLuminance = hdr?.desiredMinLuminance,
            currentMode = current,
            supportedModes = modes.distinct(),
        )
    }

    private fun hdrFormat(type: Int): HdrFormat? = when (type) {
        Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> HdrFormat.DOLBY_VISION
        Display.HdrCapabilities.HDR_TYPE_HDR10 -> HdrFormat.HDR10
        Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> HdrFormat.HDR10_PLUS
        Display.HdrCapabilities.HDR_TYPE_HLG -> HdrFormat.HLG
        else -> null
    }

    private fun codecCapabilities(): Pair<List<VideoDecoderCapability>, List<AudioDecoderCapability>> {
        val videos = mutableListOf<VideoDecoderCapability>()
        val audios = mutableListOf<AudioDecoderCapability>()
        val list = runCatching { MediaCodecList(MediaCodecList.ALL_CODECS) }.getOrNull()
            ?: return videos to audios
        list.codecInfos.forEach { info ->
            if (info.isEncoder) return@forEach
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                runCatching { info.isAlias }.getOrDefault(false)
            ) {
                return@forEach
            }
            info.supportedTypes.forEach { mime ->
                val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: return@forEach
                val hardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { info.isHardwareAccelerated }.getOrDefault(false)
                } else {
                    !info.name.contains("android", ignoreCase = true)
                }
                when {
                    mime.startsWith("video/") -> {
                        val video = runCatching { caps.videoCapabilities }.getOrNull()
                        videos += VideoDecoderCapability(
                            mime = mime,
                            decoderName = info.name,
                            hardware = hardware,
                            softwareOnly = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                runCatching { info.isSoftwareOnly }.getOrDefault(false)
                            } else {
                                false
                            },
                            profiles = caps.profileLevels.map { DecoderProfile(it.profile, it.level) },
                            maxWidth = runCatching { video?.supportedWidths?.upper }.getOrNull(),
                            maxHeight = runCatching { video?.supportedHeights?.upper }.getOrNull(),
                            maxFrameRate = runCatching {
                                video?.getSupportedFrameRates()?.upper?.toFloat()
                            }.getOrNull(),
                        )
                    }
                    mime.startsWith("audio/") -> {
                        audios += AudioDecoderCapability(
                            mime = mime,
                            decoderName = info.name,
                            hardware = hardware,
                            maxChannels = runCatching {
                                caps.audioCapabilities?.maxInputChannelCount
                            }.getOrNull(),
                        )
                    }
                }
            }
        }
        return videos.distinctBy { it.mime to it.decoderName } to
            audios.distinctBy { it.mime to it.decoderName }
    }

    /**
     * 并入 nextlib 的软件解码器：否则播放计划与诊断会把 TrueHD / DTS 判成
     * 「无解码器，将静音」，而实际播放已经能由 FFmpeg 接管。
     * 只补平台没有任何解码器的 MIME；原生库不可用（ABI 不支持）时不添加。
     */
    private fun withFfmpegAudio(system: List<AudioDecoderCapability>): List<AudioDecoderCapability> {
        if (!ffmpegAvailable()) return system
        val present = system.mapTo(HashSet()) { it.mime }
        return system + FFMPEG_AUDIO_MIMES
            .filter { it !in present && FfmpegLibrary.supportsFormat(it) }
            .map {
                AudioDecoderCapability(
                    mime = it,
                    decoderName = FFMPEG_DECODER_NAME,
                    hardware = false,
                    maxChannels = null,
                )
            }
    }

    private fun withFfmpegVideo(system: List<VideoDecoderCapability>): List<VideoDecoderCapability> {
        if (!ffmpegAvailable()) return system
        val present = system.mapTo(HashSet()) { it.mime }
        return system + FFMPEG_VIDEO_MIMES
            .filter { it !in present && FfmpegLibrary.supportsFormat(it) }
            .map {
                VideoDecoderCapability(
                    mime = it,
                    decoderName = FFMPEG_DECODER_NAME,
                    hardware = false,
                    softwareOnly = true,
                    profiles = emptyList(),
                    maxWidth = null,
                    maxHeight = null,
                    maxFrameRate = null,
                )
            }
    }

    private fun ffmpegAvailable(): Boolean =
        runCatching { FfmpegLibrary.isAvailable() }.getOrDefault(false)

    private fun audioOutputs(): List<AudioOutputDevice> {
        val am = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        return runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map {
                AudioOutputDevice(
                    name = it.productName?.toString().orEmpty(),
                    type = it.type,
                    channelCounts = it.channelCounts.toList(),
                    sampleRates = it.sampleRates.toList(),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun audioOutputCapabilities(): AudioOutputCapabilities {
        val caps = runCatching { AudioCapabilities.getCapabilities(context) }.getOrNull()
        val passthrough = if (caps == null) {
            emptySet()
        } else {
            PASSTHROUGH_MIMES.filter { mime ->
                runCatching {
                    caps.isPassthroughPlaybackSupported(
                        Format.Builder()
                            .setSampleMimeType(mime)
                            .setChannelCount(6)
                            .setSampleRate(48_000)
                            .build(),
                    )
                }.getOrDefault(false)
            }.toSet()
        }
        return AudioOutputCapabilities(
            maxChannelCount = caps?.maxChannelCount,
            passthroughMimes = passthrough,
        )
    }

    private companion object {
        const val FFMPEG_DECODER_NAME = "ffmpeg (nextlib)"

        /**
         * 与 nextlib 1.11.1-0.16.0 的 `FfmpegLibrary.getCodecName` 一一对应。
         * 升级 nextlib 时必须同步核对这张表，否则诊断会多报或漏报解码能力。
         */
        val FFMPEG_AUDIO_MIMES = listOf(
            MimeTypes.AUDIO_AAC,
            MimeTypes.AUDIO_MPEG,
            MimeTypes.AUDIO_MPEG_L1,
            MimeTypes.AUDIO_MPEG_L2,
            MimeTypes.AUDIO_AC3,
            MimeTypes.AUDIO_E_AC3,
            MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_TRUEHD,
            MimeTypes.AUDIO_DTS,
            MimeTypes.AUDIO_DTS_HD,
            MimeTypes.AUDIO_VORBIS,
            MimeTypes.AUDIO_OPUS,
            MimeTypes.AUDIO_AMR_NB,
            MimeTypes.AUDIO_AMR_WB,
            MimeTypes.AUDIO_FLAC,
            MimeTypes.AUDIO_ALAC,
            MimeTypes.AUDIO_MLAW,
            MimeTypes.AUDIO_ALAW,
        )

        val FFMPEG_VIDEO_MIMES = listOf(
            MimeTypes.VIDEO_H264,
            MimeTypes.VIDEO_H265,
            MimeTypes.VIDEO_AV1,
            MimeTypes.VIDEO_MPEG,
            MimeTypes.VIDEO_MPEG2,
            MimeTypes.VIDEO_VP8,
            MimeTypes.VIDEO_VP9,
        )

        val PASSTHROUGH_MIMES = listOf(
            "audio/ac3",
            "audio/eac3",
            "audio/eac3-joc",
            "audio/truehd",
            "audio/vnd.dts",
            "audio/vnd.dts.hd",
            "audio/mp4a-latm",
            "audio/opus",
            "audio/flac",
        )
    }
}
