package com.lovelev.mediadock.player.probe

import com.lovelev.mediadock.core.model.AudioStreamInfo
import com.lovelev.mediadock.core.model.HdrFormat
import com.lovelev.mediadock.core.model.ProbeFailure
import com.lovelev.mediadock.core.model.ProbeStatus
import com.lovelev.mediadock.core.model.StreamInfo
import com.lovelev.mediadock.core.model.SubtitleStreamInfo
import com.lovelev.mediadock.core.model.VideoStreamInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Room 里存的是这个 DTO 的 JSON；schema_version 在实体上单独记录。 */
@Serializable
internal data class StreamInfoDto(
    val path: String,
    val sizeBytes: Long?,
    val container: String?,
    val durationMs: Long?,
    val video: List<VideoDto>,
    val audio: List<AudioDto>,
    val subtitles: List<SubtitleDto>,
    val status: String,
    val failure: String?,
    val probedAt: Long,
) {
    @Serializable
    data class VideoDto(
        val mime: String?,
        val codec: String?,
        val width: Int?,
        val height: Int?,
        val frameRate: Float?,
        val bitrate: Int?,
        val profile: Int?,
        val level: Int?,
        val bitDepth: Int?,
        val hdr: String,
        val isDolbyVision: Boolean,
    )

    @Serializable
    data class AudioDto(
        val mime: String?,
        val codec: String?,
        val channels: Int?,
        val sampleRate: Int?,
        val bitrate: Int?,
        val language: String?,
        val isJoc: Boolean,
    )

    @Serializable
    data class SubtitleDto(
        val mime: String?,
        val language: String?,
        val forced: Boolean?,
    )
}

internal object StreamInfoCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(info: StreamInfo): String = json.encodeToString(
        StreamInfoDto.serializer(),
        StreamInfoDto(
            path = info.path,
            sizeBytes = info.sizeBytes,
            container = info.container,
            durationMs = info.durationMs,
            video = info.video.map {
                StreamInfoDto.VideoDto(
                    mime = it.mime,
                    codec = it.codec,
                    width = it.width,
                    height = it.height,
                    frameRate = it.frameRate,
                    bitrate = it.bitrate,
                    profile = it.profile,
                    level = it.level,
                    bitDepth = it.bitDepth,
                    hdr = it.hdr.name,
                    isDolbyVision = it.isDolbyVision,
                )
            },
            audio = info.audio.map {
                StreamInfoDto.AudioDto(
                    mime = it.mime,
                    codec = it.codec,
                    channels = it.channels,
                    sampleRate = it.sampleRate,
                    bitrate = it.bitrate,
                    language = it.language,
                    isJoc = it.isJoc,
                )
            },
            subtitles = info.subtitles.map {
                StreamInfoDto.SubtitleDto(
                    mime = it.mime,
                    language = it.language,
                    forced = it.forced,
                )
            },
            status = info.status.name,
            failure = info.failure?.name,
            probedAt = info.probedAt,
        ),
    )

    fun decode(payload: String): StreamInfo? = runCatching {
        val dto = json.decodeFromString(StreamInfoDto.serializer(), payload)
        StreamInfo(
            path = dto.path,
            sizeBytes = dto.sizeBytes,
            container = dto.container,
            durationMs = dto.durationMs,
            video = dto.video.map { VideoStreamInfo(
                mime = it.mime,
                codec = it.codec,
                width = it.width,
                height = it.height,
                frameRate = it.frameRate,
                bitrate = it.bitrate,
                profile = it.profile,
                level = it.level,
                bitDepth = it.bitDepth,
                hdr = enumOr(HdrFormat.UNKNOWN, it.hdr),
                isDolbyVision = it.isDolbyVision,
            ) },
            audio = dto.audio.map { AudioStreamInfo(
                mime = it.mime,
                codec = it.codec,
                channels = it.channels,
                sampleRate = it.sampleRate,
                bitrate = it.bitrate,
                language = it.language,
                isJoc = it.isJoc,
            ) },
            subtitles = dto.subtitles.map { SubtitleStreamInfo(
                mime = it.mime,
                language = it.language,
                forced = it.forced,
            ) },
            status = enumOr(ProbeStatus.UNKNOWN, dto.status),
            failure = dto.failure?.let { name ->
                enumValues<ProbeFailure>().firstOrNull { it.name == name }
            },
            probedAt = dto.probedAt,
        )
    }.getOrNull()

    private inline fun <reified T : Enum<T>> enumOr(fallback: T, name: String): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback
}
