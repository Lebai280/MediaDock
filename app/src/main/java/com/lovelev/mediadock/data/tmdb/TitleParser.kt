package com.lovelev.mediadock.data.tmdb

/** 从文件名里抠出来的检索线索。 */
data class ParsedTitle(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
)

/**
 * 文件名解析。规则是照着**用户库里真实的命名**定的，不得凭推测修改：
 *
 * ```
 * 河西走廊.HeXi.Corridor.2015.EP01.4K.X264.AAC.Mandarin.CHS.HQC.mp4
 * [虫虫危机].A.Bug's.Life.1998.BluRay.1080p.x264.DTS-WiKi     ← 这是目录名，电影的片名在目录上
 * 贝克汉姆 Beckham (2023)
 * ```
 *
 * 由此定下的三条：
 *  1. 集数标记可能是 `EP01` / `E01`，**不一定**是 `S01E01`。
 *  2. 中文名常常在**父目录**上（`[虫虫危机]`），文件里只有英文名 —— 所以解析要同时看这两层。
 *  3. 中文库里，能抠出连续中文段就用它当检索词（TMDB 的 zh-CN 对中文名匹配得很好），
 *     抠不出来才退回去用英文部分。
 */
object TitleParser {

    private val BRACKETED = Regex("[\\[【]([^\\]】]{1,80})[]】]")
    private val YEAR = Regex("(?<![0-9])(19[0-9]{2}|20[0-9]{2})(?![0-9])")
    private val SXXEXX = Regex("(?i)(?<![A-Za-z0-9])S(\\d{1,2})[\\s._-]*E(\\d{1,3})(?![0-9])")
    private val EPXX = Regex("(?i)(?<![A-Za-z0-9])(?:EP|E)(\\d{1,3})(?![0-9])")
    private val SEASON_WORD = Regex("(?i)(?<![A-Za-z0-9])season[\\s._-]*(\\d{1,2})(?![0-9])")
    private val SEASON_DIR_EN = Regex("(?i)^(?:s|season)\\s*(\\d{1,2})$")
    private val SEASON_DIR_CN = Regex("^第\\s*(\\d{1,2})\\s*季$")
    private val NXNN = Regex("(?<![0-9])(\\d{1,2})x(\\d{1,3})(?![0-9])")
    // 中文动漫常见的「裸集数」：葬送的芙莉莲01.mp4、四月是你的谎言 22.mp4
    private val BARE_EPISODE = Regex("(?<![0-9])(\\d{1,3})\\s*$")
    // 季目录里的「序号前缀」：S02/简日双语/29.简日双语.mp4。
    // 只在**已知位于季目录**时才当集数 —— 否则会和「1.某电影」这种目录序号混淆。
    private val LEADING_EPISODE = Regex("^\\s*(\\d{1,3})\\s*[.\\-_]\\s*")
    private val RESOLUTION_NUMBERS = setOf(0, 144, 240, 360, 480, 576, 720)
    private val LEADING_INDEX = Regex("^\\s*\\d{1,3}\\s*[.\\-_]\\s*")
    private val CHANNEL_TAG = Regex("(?i)(?<![A-Za-z0-9])MA[\\s._-]?\\d(?:\\.\\d)?(?![0-9])")
    private val CJK_RUN = Regex("[\\u3400-\\u4dbf\\u4e00-\\u9fff]+")
    private val SEPARATOR = Regex("[\\s._+·]+")

    private val TAGS = setOf(
        // 分辨率 / 编码 / 色深
        "4k", "8k", "2160p", "1080p", "720p", "480p", "hd", "fhd", "uhd", "sd",
        "x264", "x265", "h264", "h265", "hevc", "avc", "av1", "vp9", "divx", "xvid",
        "10bit", "8bit", "hdr", "hdr10", "sdr", "dv", "dolby", "vision", "imax",
        // 片源 / 压制
        "bluray", "blu", "ray", "bdrip", "brrip", "dvdrip", "hdrip", "web", "webdl",
        "webrip", "remux", "repack", "proper", "internal", "limited", "complete", "season",
        // 音轨 / 字幕
        "aac", "ac3", "eac3", "dts", "dtshd", "truehd", "atmos", "ddp", "flac", "mp3",
        "2d", "3d", "2in1", "3in1", "multi", "multiaudio", "triaudio", "3audio", "2audio",
        "mandarin", "cantonese", "chinese", "english", "chs", "cht", "chi", "eng", "hfr",
        // 发布组 / 站名
        "wiki", "beast", "hqc", "hk", "tw", "nt", "bhd", "chd", "mytv",
    )

    /**
     * @param folderName 父目录名。很多中文命名把片名放在目录上，只有这一层能救回来。
     * @param inSeasonDir 文件是否位于 `S01` / `Season 1` / `第1季` 这类季目录下。
     *   是的话，「29.简日双语.mp4」开头的 29 按集数处理；否则开头数字仍只当目录序号。
     */
    fun parse(
        fileName: String,
        folderName: String? = null,
        inSeasonDir: Boolean = false,
    ): ParsedTitle {
        val rawFileStem = stripExtension(fileName)
        val fileStem = stripLeadingIndex(rawFileStem)
        val folderStem = folderName?.let { stripExtension(stripLeadingIndex(it)) }

        // 季目录里的文件用「未剥序号」的名字找集数，才能认出 29.xxx 这种写法
        val episodeSource = if (inSeasonDir) rawFileStem else fileStem
        val (season, episode) = episodeOf(episodeSource, allowLeading = inSeasonDir)
            ?: episodeOf(folderStem ?: "")
            ?: (null to null)
        val seasonFinal = season ?: seasonWordOf(folderStem ?: "")
        val year = YEAR.find(fileStem)?.value?.toIntOrNull()
            ?: folderStem?.let { YEAR.find(it)?.value?.toIntOrNull() }

        val title = pickTitle(fileStem, folderStem)

        return ParsedTitle(
            title = title,
            year = year,
            season = seasonFinal,
            episode = episode,
        )
    }

    /**
     * 选检索词的优先级：
     * 1. 文件或目录里 `[...]` 包着的中文（`[虫虫危机]`）—— 这是作者特意标的片名
     * 2. 文件名里能抠出的中文段
     * 3. 目录名里能抠出的中文段（文件只有英文名时靠这条）
     * 4. 文件名洗掉标签后的英文部分
     * 5. 回退：文件名的第一个词（无法提取时也不得使检索词为空）
     */
    private fun pickTitle(fileStem: String, folderStem: String?): String {
        bracketedCjk(fileStem)?.let { return it }
        bracketedCjk(folderStem.orEmpty())?.let { return it }

        cjkRun(cleaned(fileStem))?.let { return it }
        cjkRun(cleaned(folderStem.orEmpty()))?.let { return it }

        val latin = cleaned(fileStem)
        if (latin.isNotBlank()) return latin

        val fromFolder = cleaned(folderStem.orEmpty())
        if (fromFolder.isNotBlank()) return fromFolder

        return fileStem.split(SEPARATOR).firstOrNull { it.isNotBlank() }.orEmpty()
    }

    /** 方括号里是**分类**而不是片名的情况（用户库大量用 `【电影】片名….mp4`）。 */
    private val CATEGORY_TAGS = setOf(
        "电影", "动画", "动漫", "电视剧", "剧集", "纪录片", "综艺", "短片",
        "音乐", "演唱会", "合集", "系列", "剧场版",
    )

    /** `[...]` / `【...】` 里的中文段。分类标签不算片名。 */
    private fun bracketedCjk(source: String): String? =
        BRACKETED.findAll(source)
            .map { it.groupValues[1] }
            .map { cjkRun(it).orEmpty() }
            .firstOrNull { it.isNotBlank() && it !in CATEGORY_TAGS }

    private fun cjkRun(source: String): String? =
        CJK_RUN.find(source)?.value?.trim()

    /** 去掉方括号段、集数、年份、发布标签，留下片名候选。 */
    private fun cleaned(source: String): String {
        if (source.isBlank()) return ""
        var s = BRACKETED.replace(source, " ")
        s = s.replace(Regex("[()（）\\[\\]【】]"), " ")
        s = SXXEXX.replace(s, " ")
        s = EPXX.replace(s, " ")
        s = SEASON_WORD.replace(s, " ")
        s = CHANNEL_TAG.replace(s, " ")
        s = NXNN.replace(s, " ")
        s = YEAR.replace(s, " ")
        return s.split(SEPARATOR)
            .filter { it.isNotBlank() && !isJunk(it) }
            .joinToString(" ")
            .trim()
            .trimEnd('。', '！', '？', '.', '!', '?', '、', ',', '，')
    }

    private fun isJunk(token: String): Boolean {
        val t = token.trim()
        if (t.isEmpty()) return true
        if (t.all { it.isDigit() }) return true
        // "dts-wiki" / "blu-ray" / "x264-group"：破折号前面是标签就整段丢掉；
        // "spider-man" 前面不是标签，保留
        val head = t.substringBefore('-').lowercase()
        return head in TAGS || t.lowercase() in TAGS
    }

    private fun episodeOf(source: String, allowLeading: Boolean = false): Pair<Int?, Int?>? {
        SXXEXX.find(source)?.let { m ->
            return m.groupValues[1].toIntOrNull() to m.groupValues[2].toIntOrNull()
        }
        EPXX.find(source)?.let { m ->
            return null to m.groupValues[1].toIntOrNull()
        }
        NXNN.find(source)?.let { m ->
            return m.groupValues[1].toIntOrNull() to m.groupValues[2].toIntOrNull()
        }
        // 裸数字放最后：像 720 这种常见分辨率数值不认（文件名末尾恰好是它时宁可不当集数）
        BARE_EPISODE.find(source)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return@let
            if (n in RESOLUTION_NUMBERS) return@let
            return null to n
        }
        if (allowLeading) {
            LEADING_EPISODE.find(source)?.let { m ->
                val n = m.groupValues[1].toIntOrNull() ?: return@let
                if (n in RESOLUTION_NUMBERS) return@let
                return null to n
            }
        }
        return null
    }

    /**
     * 从完整路径里找季号：`S01` / `Season 1` / `第1季` 目录。
     * 扫描时如果文件名本身没给出季（`葬送的芙莉莲01.mp4` 放在 `S01/` 下），用它补上。
     */
    fun seasonOfPath(path: String): Int? {
        val dirs = path.substringBeforeLast('/', "").split('/')
        for (dir in dirs.asReversed()) {
            val t = dir.trim()
            SEASON_DIR_EN.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
            SEASON_DIR_CN.find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun seasonWordOf(source: String): Int? =
        SEASON_WORD.find(source)?.groupValues?.get(1)?.toIntOrNull()

    /** `1.河西走廊` → `河西走廊`：目录名前缀的序号不是片名的一部分。 */
    private fun stripLeadingIndex(name: String): String = LEADING_INDEX.replace(name, "")

    private fun stripExtension(name: String): String {
        val dot = name.lastIndexOf('.')
        // 只有点后面真的是扩展名（短且像后缀）才切，免得把 "你的名字。.2016" 切坏
        if (dot <= 0) return name
        val ext = name.substring(dot + 1)
        return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) name.substring(0, dot) else name
    }
}
