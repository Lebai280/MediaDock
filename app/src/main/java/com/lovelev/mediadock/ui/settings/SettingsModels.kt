package com.lovelev.mediadock.ui.settings

/** 需要二次确认的不可逆操作。 */
enum class Confirmable(val title: String, val detail: String) {
    ClearLibrary("清空媒体库", "将清空媒体库并需要重新扫描；进度与收藏保留。"),
    Logout("退出登录", "将清除数据源登录信息。"),
}
