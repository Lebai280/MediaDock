package com.lovelev.mediadock.ui.player

import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController

/** 音轨在菜单里显示成什么。 */
internal fun audioTrackLabel(format: Format, index: Int): String {
    val name = format.label?.takeIf { it.isNotBlank() }
        ?: LANGUAGE_NAMES[format.language?.lowercase()]
        ?: "音轨 ${index + 1}"
    val channels = when (format.channelCount) {
        1 -> "单声道"
        2 -> "立体声"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }
    return if (channels != null) "$name · $channels" else name
}

/**
 * media3 报的 `language` 是容器里的原样标记，MKV 里通常是 ISO 639-2/T 的三字母码
 * （`jpn` / `chi` / `yue`…），而 `Locale.forLanguageTag` 只认 BCP-47 的两字母形式。
 */
private val LANGUAGE_NAMES = mapOf(
    "chi" to "中文", "zho" to "中文", "chs" to "中文（简）", "cht" to "中文（繁）",
    "cmn" to "国语", "yue" to "粤语", "eng" to "英语",
    "jpn" to "日语", "kor" to "韩语", "fra" to "法语", "deu" to "德语", "spa" to "西班牙语",
    "rus" to "俄语", "ita" to "意大利语", "por" to "葡萄牙语", "tha" to "泰语",
)

/** 控制器上挂的是不是就是这一批（mediaId 用的就是路径，所以能直接比）。 */
internal fun MediaController.holdsQueue(signature: String): Boolean {
    if (mediaItemCount != signature.split('\u0000').size) return false
    return (0 until mediaItemCount).joinToString("\u0000") { getMediaItemAt(it).mediaId } == signature
}

/** 将 ExoPlayer 错误转换为可操作的用户提示。 */
internal fun describePlaybackError(error: PlaybackException): String {
    // 状态码未必位于最外层 cause，沿因果链逐层查找
    val httpCode = generateSequence(error.cause) { it.cause }
        .take(10)
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode
    return when (httpCode) {
        401 -> "账号或密码不正确，请返回数据源页重新登录"
        429 -> "服务器已暂时锁定登录，请稍后重试；请勿反复尝试"
        404 -> "文件不存在或路径已失效"
        null -> "播放失败：${error.message ?: "未知错误"}"
        else -> "播放失败：服务器返回 HTTP $httpCode"
    }
}

internal fun speedLabel(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}×" else "$value×"

internal val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)
