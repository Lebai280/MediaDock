package com.lovelev.mediadock.ui.nav

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * 播放页自动横屏，其余页面交回系统。
 *
 * **为什么放在导航外壳里、而不是播放页自己管：** 强制横屏会让 Activity 重建一次，
 * composable 里闭包捕获的 `Activity` 随即变成已销毁的旧实例，在它身上设方向毫无反应
 * —— 表现就是「进了横屏之后再也回不去竖屏，整个 App 卡在横屏」。
 * 外壳（MainActivity.HomeScaffold）每次重建都会拿到**当前**的 Activity，
 * 而且 [LaunchedEffect] 在**路由变化时**就跑，那时 Activity 还活着。
 *
 * 为什么还要看用户的旋转锁：手机锁竖屏时（`accelerometer_rotation=0`），
 * `SENSOR_LANDSCAPE` 这类「跟传感器」的请求会被系统直接忽略（AOSP 的行为：
 * 旋转被锁时让用户偏好赢）—— 代码明明请求了，画面死活不转。
 * 所以锁着的时候只能用**硬性**的 `LANDSCAPE` 压过去；没锁时 `SENSOR_LANDSCAPE` 更好，
 * 横过来还能左右翻转。
 */
@Composable
internal fun PlayerOrientation(route: String?) {
    val context = LocalContext.current
    val activity = context.findActivity()
    LaunchedEffect(route, activity) {
        if (activity == null) return@LaunchedEffect
        activity.requestedOrientation = if (route == Routes.PLAYER) {
            if (activity.autoRotateEnabled()) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

/**
 * 播放页隐藏系统状态栏/导航栏，离开时恢复。
 *
 * 和方向一样放在导航外壳里（理由见上）：那里每次重建都能拿到当前 Activity。
 * 隐藏态下从边缘下滑仍能临时叫出系统栏（`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`），
 * 不会把用户困在全屏里。
 */
@Composable
internal fun PlayerImmersive(route: String?) {
    val context = LocalContext.current
    val activity = context.findActivity()
    DisposableEffect(route, activity) {
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            if (route == Routes.PLAYER) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}

/** 读系统设置里的「自动旋转」。不需要任何权限。 */
internal fun Context.autoRotateEnabled(): Boolean =
    Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1

/**
 * 系统栏图标明暗。
 *
 * 详情页是**始终深色**的一整块面（海报取色铺满），所以状态栏/导航栏要强制浅色图标；
 * 其余页面跟随 App 当前主题（用户可能把主题强制成深色，而系统还是浅色 ——
 * 这时若按系统算，会得到「深底 + 深图标」= 看不清）。
 */
@Composable
internal fun SystemBarAppearance(route: String?, darkTheme: Boolean) {
    val context = LocalContext.current
    val activity = context.findActivity()
    DisposableEffect(route, activity, darkTheme) {
        if (activity != null) {
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            val lightIcons = route == Routes.DETAIL || darkTheme
            controller.isAppearanceLightStatusBars = !lightIcons
            controller.isAppearanceLightNavigationBars = !lightIcons
        }
        onDispose { }
    }
}

/** 从 Compose 的 LocalContext 里把 Activity 剥出来（它可能被 ContextWrapper 包着）。 */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
