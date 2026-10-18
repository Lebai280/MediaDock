package com.lovelev.mediadock.data.subtitle

/** 一条字幕搜索结果。 */
data class SubtitleHit(
    /** 提供方内部 id（下载时用它）。 */
    val id: String,
    /** 片名 + 年份拼出来给人看的一行。 */
    val label: String,
    /** 原始语言标记，如 en / zh-cn / zh-tw。 */
    val language: String,
    /** 下载量（热度），用来排序。 */
    val downloads: Int?,
    /** 发布版本（帮用户对号入座）。 */
    val release: String?,
)

/** 下载到的字幕文件。 */
class SubtitleFile(
    val fileName: String,
    val bytes: ByteArray,
)

/**
 * 字幕源。想加别的站（Assrt 等）就再写一个实现，界面不用改 ——
 * 搜索框、结果列表、挂载逻辑都只认这个接口。
 */
interface SubtitleProvider {
    val name: String

    /** 凭据配好了没。未配置时引导用户去设置页（返回 false，不得抛出异常）。 */
    suspend fun isConfigured(): Boolean

    /**
     * 找字幕。[languages] 是逗号分隔的语言标记（`zh-cn,en`）；
     * [season] / [episode] 有值时按剧集找。
     */
    suspend fun search(
        query: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        languages: String,
    ): List<SubtitleHit>

    /** 下载一条。 */
    suspend fun download(hit: SubtitleHit): SubtitleFile
}

/** 字幕源的错误。message 都是能直接给用户看的一句话。 */
open class SubtitleException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 凭据没配 / 不对 —— 不重试，引导去设置页。 */
class SubtitleAuthException(message: String) : SubtitleException(message)

/** 额度用完 / 被限流 —— 告诉用户何时能再来，不重试。 */
class SubtitleQuotaException(message: String) : SubtitleException(message)

/** 语言标记 → 中文名。认不出来就原样返回，不编。 */
fun subtitleLanguageLabel(code: String): String = when (code.lowercase()) {
    "zh-cn", "zh", "chi", "zho" -> "简体中文"
    "zh-tw", "zh-hk", "zht" -> "繁体中文"
    "en", "eng" -> "英文"
    "ja", "jpn" -> "日文"
    "ko", "kor" -> "韩文"
    "fr", "fre", "fra" -> "法文"
    "de", "ger", "deu" -> "德文"
    "es", "spa" -> "西班牙文"
    "ru", "rus" -> "俄文"
    else -> code
}
