package com.lovelev.mediadock.data

/** 哪些扩展名算视频、哪些算字幕，以及「这个字幕是不是配这个视频」。
 *  扫描器、浏览页、播放页都要用同一份判断，不得重复实现。 */
object MediaTypes {

    private val VIDEO_EXTENSIONS = setOf(
        "mkv", "mp4", "m4v", "mov", "avi", "wmv", "flv", "webm",
        "ts", "m2ts", "mpg", "mpeg", "vob", "rmvb", "rm", "3gp", "ogv",
    )

    private val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt")

    fun isVideoFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    fun isSubtitleFile(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in SUBTITLE_EXTENSIONS

    /**
     * 这个字幕文件是不是配这个视频的。
     *
     * 实际命名：视频 `河西走廊.EP01.4K.X264.AAC.Mandarin.CHS.HQC.mp4`
     * 配字幕 `河西走廊.EP01.4K.X264.AAC.Mandarin.CHS.srt` —— 字幕名是视频名的**前缀**；
     * 反过来（`movie.mkv` 配 `movie.chs.ass`）视频名是字幕名的前缀。两个方向都认。
     *
     * 但**必须卡在分隔符上**：`Ep1.mkv` 不能被 `Ep10.srt` 认领，
     * 所以只认「前缀之后紧跟着 . - _ 空格」或完全相等。
     */
    fun matchesSubtitle(videoName: String, subtitleName: String): Boolean {
        if (!isSubtitleFile(subtitleName)) return false
        val video = stem(videoName)
        val subtitle = stem(subtitleName)
        return video.equals(subtitle, ignoreCase = true) ||
            extendsOnBoundary(subtitle, video) ||
            extendsOnBoundary(video, subtitle)
    }

    /** [long] 是否以 [short] 开头，且紧跟着一个分隔符。 */
    private fun extendsOnBoundary(short: String, long: String): Boolean {
        if (short.isEmpty() || long.length <= short.length) return false
        if (!long.startsWith(short, ignoreCase = true)) return false
        return long[short.length] in BOUNDARIES
    }

    private fun stem(name: String): String {
        val leaf = name.substringAfterLast('/')
        return leaf.substringBeforeLast('.', leaf)
    }

    /**
     * 从文件名里读分辨率标签（VidHub 详情页那行「46分钟 | 1.63 GB | 4K」的最后一项）。
     * 只是个标签，读不出来就不显示 —— **不去探测真实分辨率**（那要发请求或解码）。
     */
    fun resolutionLabel(name: String): String? {
        val lower = name.lowercase()
        return when {
            RESOLUTION_8K.containsMatchIn(lower) -> "8K"
            RESOLUTION_4K.containsMatchIn(lower) -> "4K"
            RESOLUTION_1080.containsMatchIn(lower) -> "1080P"
            RESOLUTION_720.containsMatchIn(lower) -> "720P"
            RESOLUTION_480.containsMatchIn(lower) -> "480P"
            else -> null
        }
    }

    private val RESOLUTION_8K = Regex("(?<![0-9])8k(?![0-9])|4320p")
    private val RESOLUTION_4K = Regex("(?<![0-9])4k(?![0-9])|2160p")
    private val RESOLUTION_1080 = Regex("1080[pi]")
    private val RESOLUTION_720 = Regex("720[pi]")
    private val RESOLUTION_480 = Regex("480[pi]")

    private const val BOUNDARIES = ".-_ "
}
