package com.lovelev.mediadock.core.model

enum class BadgeSupport { SUPPORTED, UNSUPPORTED, UNKNOWN }

data class SpecBadge(val text: String, val support: BadgeSupport)

object HdrClassifier {
    /**
     * 只根据容器里能确认真实的事实分类。
     * `hdrStaticInfo` 在目标设备 MP4/MKV 上都为空，因此不能用来推断 HDR10+。
     */
    fun classify(
        isDolbyVision: Boolean,
        isHdr10PlusProfile: Boolean,
        isHlg: Boolean,
        isPq: Boolean,
        hasHdrStaticInfo: Boolean,
        hasColorInfo: Boolean,
    ): HdrFormat = when {
        isDolbyVision -> HdrFormat.DOLBY_VISION
        isHdr10PlusProfile -> HdrFormat.HDR10_PLUS
        isHlg -> HdrFormat.HLG
        isPq || hasHdrStaticInfo -> HdrFormat.HDR10
        hasColorInfo -> HdrFormat.SDR
        else -> HdrFormat.UNKNOWN
    }
}

object MediaSpecBadges {

    fun resolution(width: Int?, height: Int?): String? {
        if (width == null || height == null || width <= 0 || height <= 0) return null
        val longEdge = maxOf(width, height)
        return when {
            longEdge >= 7680 -> "8K"
            longEdge >= 3840 -> "4K"
            longEdge >= 1920 -> "1080P"
            longEdge >= 1280 -> "720P"
            else -> null
        }
    }

    fun badges(
        video: VideoStreamInfo?,
        audio: List<AudioStreamInfo>,
        displayHdr: Set<HdrFormat>,
    ): List<SpecBadge> {
        val badges = mutableListOf<SpecBadge>()
        resolution(video?.width, video?.height)?.let {
            badges += SpecBadge(it, BadgeSupport.SUPPORTED)
        }
        video?.hdr?.takeIf { it != HdrFormat.SDR && it != HdrFormat.UNKNOWN }?.let { hdr ->
            val text = when (hdr) {
                HdrFormat.DOLBY_VISION -> "DV"
                HdrFormat.HDR10_PLUS -> "HDR10+"
                HdrFormat.HLG -> "HLG"
                HdrFormat.HDR10 -> "HDR10"
                else -> return@let
            }
            val support = if (hdr in displayHdr) BadgeSupport.SUPPORTED else BadgeSupport.UNSUPPORTED
            badges += SpecBadge(text, support)
        }
        if (audio.any { it.isJoc }) {
            // 设备可能声明支持 JOC，但直通未实现/未验证 —— 只用中性状态表示。
            badges += SpecBadge("Atmos", BadgeSupport.UNKNOWN)
        }
        return badges
    }
}
