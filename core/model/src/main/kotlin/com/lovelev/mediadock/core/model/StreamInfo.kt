package com.lovelev.mediadock.core.model

enum class HdrFormat {
    SDR,
    HDR10,
    HDR10_PLUS,
    HLG,
    DOLBY_VISION,
    UNKNOWN,
}

enum class ProbeStatus { OK, UNKNOWN, UNSUPPORTED, FAILED }

enum class ProbeFailure { UNRESOLVED, HTTP, TIMEOUT, UNSUPPORTED_FORMAT, IO, BUDGET }

/** 一个文件版本的真实容器/轨道信息；探测失败时 status 不为 OK，播放不受影响。 */
data class StreamInfo(
    val path: String,
    val sizeBytes: Long?,
    val container: String?,
    val durationMs: Long?,
    val video: List<VideoStreamInfo>,
    val audio: List<AudioStreamInfo>,
    val subtitles: List<SubtitleStreamInfo>,
    val status: ProbeStatus,
    val failure: ProbeFailure?,
    val probedAt: Long,
)

data class VideoStreamInfo(
    val mime: String?,
    val codec: String?,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val bitrate: Int?,
    val profile: Int?,
    val level: Int?,
    val bitDepth: Int?,
    val hdr: HdrFormat,
    val isDolbyVision: Boolean,
)

data class AudioStreamInfo(
    val mime: String?,
    val codec: String?,
    val channels: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val language: String?,
    val isJoc: Boolean,
)

data class SubtitleStreamInfo(
    val mime: String?,
    val language: String?,
    val forced: Boolean?,
)
