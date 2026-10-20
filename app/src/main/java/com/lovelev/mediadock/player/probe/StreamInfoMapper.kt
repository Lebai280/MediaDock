package com.lovelev.mediadock.player.probe

import com.lovelev.mediadock.core.model.AudioStreamInfo
import com.lovelev.mediadock.core.model.HdrClassifier
import com.lovelev.mediadock.core.model.ProbeStatus
import com.lovelev.mediadock.core.model.StreamInfo
import com.lovelev.mediadock.core.model.SubtitleStreamInfo
import com.lovelev.mediadock.core.model.VideoStreamInfo

/** MediaFormat 的纯值投影，便于不依赖 Android 做单测。 */
internal data class RawTrack(
    val mime: String?,
    val codec: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val profile: Int? = null,
    val level: Int? = null,
    val colorTransfer: Int? = null,
    val colorStandard: Int? = null,
    val hasHdrStaticInfo: Boolean = false,
    val channels: Int? = null,
    val sampleRate: Int? = null,
    val bitrateAudio: Int? = null,
    val language: String? = null,
    val forced: Boolean? = null,
)

internal object StreamInfoMapper {

    fun fromRaw(
        path: String,
        sizeBytes: Long?,
        durationMs: Long?,
        tracks: List<RawTrack>,
        now: Long,
    ): StreamInfo = StreamInfo(
        path = path,
        sizeBytes = sizeBytes,
        container = null,
        durationMs = durationMs,
        video = tracks.filter { it.mime?.startsWith("video/") == true }.map(::video),
        audio = tracks.filter { it.mime?.startsWith("audio/") == true }.map(::audio),
        subtitles = tracks.filter(::isSubtitle).map(::subtitle),
        status = ProbeStatus.OK,
        failure = null,
        probedAt = now,
    )

    fun unknown(
        path: String,
        sizeBytes: Long?,
        failure: com.lovelev.mediadock.core.model.ProbeFailure,
        now: Long,
    ): StreamInfo = StreamInfo(
        path = path,
        sizeBytes = sizeBytes,
        container = null,
        durationMs = null,
        video = emptyList(),
        audio = emptyList(),
        subtitles = emptyList(),
        status = ProbeStatus.UNKNOWN,
        failure = failure,
        probedAt = now,
    )

    private fun video(track: RawTrack): VideoStreamInfo {
        val isDv = track.mime == "video/dolby-vision" ||
            track.codec?.startsWith("dvhe") == true ||
            track.codec?.startsWith("dvh1") == true ||
            track.codec?.startsWith("dav1") == true ||
            track.codec?.startsWith("dvav") == true
        val isHdr10Plus = isHdr10PlusProfile(track.mime, track.profile)
        val isHlg = track.colorTransfer == COLOR_TRANSFER_HLG
        val isPq = track.colorTransfer == COLOR_TRANSFER_ST2084
        val hasColorInfo = track.colorTransfer != null ||
            track.colorStandard != null ||
            track.hasHdrStaticInfo
        return VideoStreamInfo(
            mime = track.mime,
            codec = track.codec,
            width = track.width,
            height = track.height,
            frameRate = track.frameRate,
            bitrate = track.bitrate,
            profile = track.profile,
            level = track.level,
            bitDepth = bitDepth(track.mime, track.profile),
            hdr = HdrClassifier.classify(
                isDolbyVision = isDv,
                isHdr10PlusProfile = isHdr10Plus,
                isHlg = isHlg,
                isPq = isPq,
                hasHdrStaticInfo = track.hasHdrStaticInfo,
                hasColorInfo = hasColorInfo,
            ),
            isDolbyVision = isDv,
        )
    }

    private fun audio(track: RawTrack): AudioStreamInfo = AudioStreamInfo(
        mime = track.mime,
        codec = track.codec,
        channels = track.channels,
        sampleRate = track.sampleRate,
        bitrate = track.bitrateAudio,
        language = track.language,
        isJoc = track.mime == "audio/eac3-joc" || track.codec?.contains("joc", ignoreCase = true) == true,
    )

    private fun subtitle(track: RawTrack): SubtitleStreamInfo = SubtitleStreamInfo(
        mime = track.mime,
        language = track.language,
        forced = track.forced,
    )

    private fun isSubtitle(track: RawTrack): Boolean {
        val mime = track.mime ?: return false
        return mime.startsWith("text/") ||
            mime.contains("subrip") ||
            mime.contains("ttml") ||
            mime.contains("pgs") ||
            mime.contains("vtt")
    }

    private fun isHdr10PlusProfile(mime: String?, profile: Int?): Boolean = when (mime) {
        "video/hevc" -> profile == HEVC_MAIN10_HDR10_PLUS
        "video/av01" -> profile == AV1_MAIN10_HDR10_PLUS
        "video/x-vnd.on2.vp9" -> profile == VP9_PROFILE2_HDR10_PLUS
        else -> false
    }

    private fun bitDepth(mime: String?, profile: Int?): Int? = when (mime) {
        "video/hevc" -> if (profile in setOf(HEVC_MAIN10, HEVC_MAIN10_HDR10, HEVC_MAIN10_HDR10_PLUS)) 10 else 8
        "video/av01" -> if (profile in setOf(AV1_MAIN10, AV1_MAIN10_HDR10, AV1_MAIN10_HDR10_PLUS)) 10 else 8
        "video/x-vnd.on2.vp9" ->
            if (profile in setOf(VP9_PROFILE2, VP9_PROFILE2_HDR, VP9_PROFILE2_HDR10_PLUS)) 10 else 8
        "video/avc" -> if (profile == AVC_HIGH10) 10 else 8
        else -> null
    }

    // Android MediaCodecInfo.CodecProfileLevel 的值；用本地常量避免单元测试加载 Android 类。
    private const val HEVC_MAIN10 = 0x02
    private const val HEVC_MAIN10_HDR10 = 0x1000
    private const val HEVC_MAIN10_HDR10_PLUS = 0x2000
    private const val AV1_MAIN10 = 0x02
    private const val AV1_MAIN10_HDR10 = 0x1000
    private const val AV1_MAIN10_HDR10_PLUS = 0x2000
    private const val VP9_PROFILE2 = 0x04
    private const val VP9_PROFILE2_HDR = 0x1000
    private const val VP9_PROFILE2_HDR10_PLUS = 0x4000
    private const val AVC_HIGH10 = 0x1000

    private const val COLOR_TRANSFER_HLG = 3
    private const val COLOR_TRANSFER_ST2084 = 6
}
