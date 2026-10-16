package com.lovelev.mediadock.ui.detail

/**
 * 详情页要看哪一批条目（一部剧的所有集，或一部单片）。
 *
 * 和 `PlaybackSelection` 同样的理由只放内存：路径里有中文和 `/`，放进导航参数要来回编码，
 * 而且会进 saved instance state。代价是进程被杀后详情页会显示空，可接受。
 */
class DetailSelection {
    var paths: List<String> = emptyList()
        private set

    fun set(paths: List<String>) {
        this.paths = paths
    }
}
