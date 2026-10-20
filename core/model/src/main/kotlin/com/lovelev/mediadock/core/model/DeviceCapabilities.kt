package com.lovelev.mediadock.core.model

data class DisplayModeInfo(
    val width: Int,
    val height: Int,
    val refreshRate: Float,
)

data class DisplayCapabilities(
    val hdrFormats: Set<HdrFormat>,
    val maxLuminance: Float?,
    val maxAverageLuminance: Float?,
    val minLuminance: Float?,
    val currentMode: DisplayModeInfo?,
    val supportedModes: List<DisplayModeInfo>,
)

data class DecoderProfile(val profile: Int, val level: Int)

data class VideoDecoderCapability(
    val mime: String,
    val decoderName: String,
    val hardware: Boolean,
    val softwareOnly: Boolean,
    val profiles: List<DecoderProfile>,
    val maxWidth: Int?,
    val maxHeight: Int?,
    val maxFrameRate: Float?,
)

data class AudioDecoderCapability(
    val mime: String,
    val decoderName: String,
    val hardware: Boolean,
    val maxChannels: Int?,
)

data class AudioOutputDevice(
    val name: String,
    val type: Int,
    val channelCounts: List<Int>,
    val sampleRates: List<Int>,
)

data class AudioOutputCapabilities(
    val maxChannelCount: Int?,
    val passthroughMimes: Set<String>,
)

data class DeviceIdentity(
    val manufacturer: String,
    val model: String,
    val device: String,
    val sdkInt: Int,
    val release: String,
    val abis: List<String>,
)

/** 设备声明能力；运行时的实际解码器/丢帧由 PlaybackDiagnostics 单独记录。 */
data class DeviceCapabilities(
    val identity: DeviceIdentity,
    val display: DisplayCapabilities,
    val videoDecoders: List<VideoDecoderCapability>,
    val audioDecoders: List<AudioDecoderCapability>,
    val audioOutputs: List<AudioOutputDevice>,
    val audioOutputCapabilities: AudioOutputCapabilities,
    val probedAt: Long,
)
