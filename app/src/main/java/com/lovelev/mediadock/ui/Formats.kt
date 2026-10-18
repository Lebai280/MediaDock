package com.lovelev.mediadock.ui

import java.util.Locale

/** 界面上重复用到的格式化。放一处，避免在多个页面重复实现。 */

fun formatSize(bytes: Long?): String {
    if (bytes == null || bytes < 0) return ""
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

/** 时长：超过一小时才显示小时位。 */
fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** 「46分钟」那种人话时长。 */
fun formatDurationWords(ms: Long): String {
    val minutes = ms / 60_000
    if (minutes <= 0) return ""
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "${hours}小时${rest}分"
        hours > 0 -> "${hours}小时"
        else -> "${minutes}分钟"
    }
}
