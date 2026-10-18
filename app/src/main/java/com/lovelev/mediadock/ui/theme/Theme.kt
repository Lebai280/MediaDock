package com.lovelev.mediadock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 配色取用户自己站点（星屿）那颗柠檬绿。
 *
 * 关键取舍：**亮柠檬绿不能当文字用** —— 它在浅底上几乎看不见（对比度 1.5:1），
 * 但当作「填充按钮的底色 + 深色字」非常好看（用户站点那个「随机开一站」就是）。
 * 所以两个角色分开：
 *  - `primary`：亮柠檬绿，给填充按钮、进度条这类**色块**用，配 `onPrimary` 深色字。
 *  - `tertiary`：深一档的橄榄绿，给**文字**用（设置页的分组标题、详情页当前集高亮）。
 * 颜色改完记得跑一下对比度（见 WORKLOG 里那段脚本），不得仅凭视觉判断。
 */
private val Lime = Color(0xFFC4E04F)
private val LimeInk = Color(0xFF1A1C10)

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = LimeInk,
    primaryContainer = Color(0xFF3A4200),
    onPrimaryContainer = Color(0xFFE6F5A8),
    // 深色底上亮柠檬绿本身就是可读的（对比度足够），文字直接用 primary 那一档
    tertiary = Lime,
    onTertiary = LimeInk,
    background = Color(0xFF131417),
    onBackground = Color(0xFFECECE9),
    surface = Color(0xFF1B1C1F),
    onSurface = Color(0xFFECECE9),
    surfaceVariant = Color(0xFF26272B),
    onSurfaceVariant = Color(0xFFA8A9AD),
    outline = Color(0xFF3A3B40),
)

private val LightColors = lightColorScheme(
    primary = Lime,
    onPrimary = LimeInk,
    primaryContainer = Color(0xFFEDF7C8),
    onPrimaryContainer = Color(0xFF2B3300),
    // 浅底上要用深一档的绿才读得出来（#5A7200 对白卡 5.47:1、对浅灰卡 4.74:1，都过 4.5）
    tertiary = Color(0xFF5A7200),
    onTertiary = Color.White,
    background = Color(0xFFF6F6F3),
    onBackground = Color(0xFF17181A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17181A),
    surfaceVariant = Color(0xFFEFEFEA),
    onSurfaceVariant = Color(0xFF5F6063),
    outline = Color(0xFFE1E1DB),
)

/**
 * 整体圆角比 M3 默认再圆一档（用户要求「稍微圆润一些」）：
 * 默认是 8 / 12 / 16 / 28，这里放大到 10 / 12 / 18 / 24 / 30。
 * 卡片、对话框、菜单、按钮都吃这套，改一处全局生效。
 */
private val MediaDockShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun MediaDockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = MediaDockShapes,
        content = content,
    )
}
