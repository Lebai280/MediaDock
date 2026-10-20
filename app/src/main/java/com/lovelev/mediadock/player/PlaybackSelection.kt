package com.lovelev.mediadock.player

/**
 * 「要播什么」：点中的文件 + 它所在目录的播放队列（都是相对数据源根的路径）。
 *
 * 只活在内存里 —— **刻意不放进导航参数**：那里最终会进 saved instance state（一个 Bundle），
 * 而路径里有中文、空格和 `/`，得来回百分号编码；何况 Bundle 会有落盘的可能，
 * 不该让凭据之类的东西有机会经过它。
 * 代价是进程被杀后回到播放页会看到「没有选中要播放的文件」，可接受。
 */
class PlaybackSelection {

    /** 点中的那个文件。 */
    var path: String? = null

    /**
     * 同目录的视频，按显示顺序排列，[path] 必在其中。
     * 有了它才有「自动播放下一集」—— 单个 MediaItem 是播完就停的。
     */
    var queue: List<String> = emptyList()

    /**
     * 选择令牌：每次显式选择自增。
     *
     * 播放页靠它区分「用户新选了一个条目」和「旋屏/配置变更后重新进入」：
     * 前者要按新选中项切换（即使队列没变），后者绝不能重设，否则播放会被拽回开头。
     */
    @Volatile
    private var selectionToken: Long = 0

    @Volatile
    private var appliedToken: Long = 0

    /** 当前选择还没有应用到播放器。 */
    val needsApply: Boolean get() = selectionToken != appliedToken

    fun set(path: String, queue: List<String>) {
        this.path = path
        this.queue = if (path in queue) queue else listOf(path)
        selectionToken++
    }

    /** 标记当前选择已应用（或本来就该沿用控制器上的状态）。 */
    fun markApplied() {
        appliedToken = selectionToken
    }
}
