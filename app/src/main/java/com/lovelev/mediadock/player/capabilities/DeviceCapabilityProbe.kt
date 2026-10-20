package com.lovelev.mediadock.player.capabilities

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.media3.common.Format
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
            videoDecoders = codecCapabilities().first,
            audioDecoders = codecCapabilities().second,
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
