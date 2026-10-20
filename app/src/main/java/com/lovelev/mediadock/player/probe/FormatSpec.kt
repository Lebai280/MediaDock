package com.lovelev.mediadock.player.probe

import androidx.media3.common.C
import androidx.media3.common.Format
import com.lovelev.mediadock.core.model.HdrClassifier
import com.lovelev.mediadock.core.model.HdrFormat
import com.lovelev.mediadock.core.model.PlaybackPlanInput

/** 运行中 Format → HDR 分类；与探测层保持同一套判定，不猜 HDR10+。 */
internal fun Format.toHdrFormat(): HdrFormat {
    val codec = codecs.orEmpty()
    val dolbyVision = sampleMimeType == "video/dolby-vision" ||
        codec.startsWith("dvhe") ||
        codec.startsWith("dvh1") ||
        codec.startsWith("dav1") ||
        codec.startsWith("dvav")
    val color = colorInfo
    return HdrClassifier.classify(
        isDolbyVision = dolbyVision,
        isHdr10PlusProfile = false,
        isHlg = color?.colorTransfer == C.COLOR_TRANSFER_HLG,
        isPq = color?.colorTransfer == C.COLOR_TRANSFER_ST2084,
        hasHdrStaticInfo = color?.hdrStaticInfo != null,
        hasColorInfo = color != null,
    )
}

internal fun planInputFrom(video: Format?, audio: Format?): PlaybackPlanInput =
    PlaybackPlanInput(
        hdr = video?.toHdrFormat() ?: HdrFormat.UNKNOWN,
        isDolbyVision = video?.let {
            it.sampleMimeType == "video/dolby-vision" ||
                it.codecs.orEmpty().startsWith("dv")
        } == true,
        frameRate = video?.frameRate?.takeIf { it > 0f },
        videoMime = video?.sampleMimeType,
        audioMime = audio?.sampleMimeType,
        audioChannels = audio?.channelCount?.takeIf { it > 0 },
    )
