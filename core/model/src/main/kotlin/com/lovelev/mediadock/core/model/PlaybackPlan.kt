package com.lovelev.mediadock.core.model

enum class HdrMode { DIRECT, SDR, UNSUPPORTED, UNKNOWN }

enum class AudioMode { PASSTHROUGH, DECODE, NO_DECODER, UNKNOWN }

data class PlaybackPlanInput(
    val hdr: HdrFormat,
    val isDolbyVision: Boolean = false,
    val frameRate: Float? = null,
    val videoMime: String? = null,
    val audioMime: String? = null,
    val audioChannels: Int? = null,
)

data class PlaybackPlan(
    val hdrMode: HdrMode,
    val hdrFormat: HdrFormat,
    val frameRate: Float?,
    val refreshMatching: Boolean,
    val audioMode: AudioMode,
    val audioChannels: Int?,
    val hardwareVideoDecoder: Boolean?,
    val notes: List<String>,
)

/**
 * 只做“计划/对照”，不改变播放器渲染路径：
 * HDR 直出与帧率匹配由 Media3/系统默认路径负责，音频直通由当前输出设备声明决定。
 */
object PlaybackPlanner {

    fun plan(
        input: PlaybackPlanInput,
        device: DeviceCapabilities,
        refreshMatching: Boolean,
        maxAudioChannels: Int,
    ): PlaybackPlan {
        val notes = mutableListOf<String>()
        val hdrMode = when {
            input.hdr == HdrFormat.SDR -> HdrMode.SDR
            input.hdr == HdrFormat.UNKNOWN -> HdrMode.UNKNOWN
            input.isDolbyVision -> {
                val displayDv = HdrFormat.DOLBY_VISION in device.display.hdrFormats
                val decoderDv = device.videoDecoders.any { it.mime == "video/dolby-vision" }
                if (displayDv && decoderDv) HdrMode.DIRECT else {
                    notes += "DV 不承诺直出（显示或解码器不支持）"
                    HdrMode.UNSUPPORTED
                }
            }
            input.hdr in device.display.hdrFormats -> HdrMode.DIRECT
            else -> {
                notes += "设备未声明支持 ${input.hdr.name}，未做色调映射"
                HdrMode.UNSUPPORTED
            }
        }

        val hardwareDecoder = input.videoMime?.let { mime ->
            device.videoDecoders
                .filter { it.mime == mime }
                .map { it.hardware }
                .maxOrNull()
        }

        val audioMode = when {
            input.audioMime == null -> AudioMode.UNKNOWN
            input.audioMime in device.audioOutputCapabilities.passthroughMimes ->
                AudioMode.PASSTHROUGH
            device.audioDecoders.any { it.mime == input.audioMime } -> AudioMode.DECODE
            else -> {
                notes += "无 ${input.audioMime} 解码器，将静音"
                AudioMode.NO_DECODER
            }
        }
        if (audioMode == AudioMode.PASSTHROUGH) {
            notes += "直通依赖当前输出设备声明；换设备可能自动回落到解码"
        }
        input.audioChannels?.let { channels ->
            if (channels > maxAudioChannels) {
                notes += "声道上限 $maxAudioChannels 会限制该 ${channels} 声道轨道"
            }
        }

        return PlaybackPlan(
            hdrMode = hdrMode,
            hdrFormat = input.hdr,
            frameRate = input.frameRate,
            refreshMatching = refreshMatching,
            audioMode = audioMode,
            audioChannels = input.audioChannels,
            hardwareVideoDecoder = hardwareDecoder,
            notes = notes,
        )
    }
}
