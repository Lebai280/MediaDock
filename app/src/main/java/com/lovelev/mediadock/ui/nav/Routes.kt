package com.lovelev.mediadock.ui.nav

object Routes {
    const val ADD_SOURCE = "add_source"

    /** 数据源管理与编辑。 */
    const val SOURCES = "sources"
    const val EDIT_SOURCE = "edit_source/{id}"

    /** 四个 Tab（照用户给的 VidHub 参考）：媒体库 / 收藏 / 浏览 / 设置。 */
    const val LIBRARY = "library"
    const val FAVORITES = "favorites"
    const val BROWSE = "browse"
    const val SETTINGS = "settings"

    const val DETAIL = "detail"

    /** 「我的」：收拢了原来的 ⋯ 菜单、浏览、设置（照 NagoMusic 的 profile 页）。 */
    const val MINE = "mine"

    /** 播放历史。 */
    const val HISTORY = "history"

    /** 搜索页 —— 底部那颗圆形按钮点进来（照 NagoMusic，搜索不在首页顶上）。 */
    const val SEARCH = "search"

    /** 用户片单：列表与单个片单。 */
    const val PLAYLISTS = "playlists"
    const val PLAYLIST = "playlist/{id}"

    /**
     * 播放页**不带参数**：要播哪个文件由 `AppContainer.playbackSelection` 在内存里传递。
     * 路径里有中文与 `/`，放进 nav 参数得来回百分号编码，而且会进 saved instance state。
     */
    const val PLAYER = "player"

    /** 运行日志（设置 → 关于里的入口）。出问题时用户能自己看、自己复制。 */
    const val LOGS = "logs"

    /** 播放能力 / 诊断（设置 → 关于里的入口）。 */
    const val CAPABILITIES = "capabilities"
}
