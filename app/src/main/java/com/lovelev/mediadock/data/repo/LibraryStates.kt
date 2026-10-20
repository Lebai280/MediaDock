package com.lovelev.mediadock.data.repo

sealed interface ScanState {
    data object Idle : ScanState

    data class Running(val directories: Int, val files: Int) : ScanState

    /** [truncated] = 撞到上限提前停了，库是不完整的。 */
    data class Done(val files: Int, val truncated: Boolean) : ScanState

    /** [blocked] = 401/429：**不再给任何重试入口**，只能重新登录。 */
    data class Failed(val message: String, val blocked: Boolean) : ScanState
}

sealed interface ScrapeState {
    data object Idle : ScrapeState

    data class Running(val done: Int, val total: Int, val matched: Int) : ScrapeState

    data class Done(val matched: Int, val failed: Int) : ScrapeState

    /** [needsSecret] = 没填凭据或凭据无效，界面该把设置引出来。 */
    data class Failed(val message: String, val needsSecret: Boolean) : ScrapeState
}
