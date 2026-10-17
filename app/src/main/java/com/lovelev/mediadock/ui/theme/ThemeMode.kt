package com.lovelev.mediadock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

/**
 * 主题三态。
 *
 * 为什么是三态而不是一个开关：用户明确要「跟随系统 / 浅色 / 深色」三种，一个按钮循环切换。
 * 两态开关做不到「跟随系统」—— 关掉跟随之后，晚上系统切深色它也不会跟着变。
 */
enum class ThemeMode(val stored: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    /** 循环到下一个（跟随 → 浅 → 深 → 跟随）。 */
    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.stored == value } ?: SYSTEM
    }
}

/** 按当前模式解出「这一刻到底是不是深色」。 */
@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
