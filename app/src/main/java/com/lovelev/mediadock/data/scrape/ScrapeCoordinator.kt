package com.lovelev.mediadock.data.scrape

import androidx.work.WorkInfo
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.data.repo.ScrapeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 刮削状态机：唯一 WorkManager 任务 `scrape` 的开始、进度映射、取消与自动触发。
 *
 * 扫描/刮削互斥通过两个 lambda 查询，不复制标志：
 * [isScanActive] = 本地扫描 job 正在跑；[isScanRequested] = 远程扫描已入队。
 */
class ScrapeCoordinator(
    private val scope: CoroutineScope,
    private val scheduler: ScrapeScheduler,
    private val credentials: SecureCredentialStore,
    private val store: MediaLibraryStore,
    private val isScanActive: () -> Boolean,
    private val isScanRequested: () -> Boolean,
) {

    private val _scrape = MutableStateFlow<ScrapeState>(ScrapeState.Idle)
    val scrape: StateFlow<ScrapeState> = _scrape.asStateFlow()

    /** 只有本会话主动发起过刮削时才把 WorkManager 的终态映射成 UI 状态。 */
    private var workScrapeRequested = false

    init {
        scope.launch {
            scheduler.observe().collect { info ->
                if (!workScrapeRequested || info == null) return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                        _scrape.value = ScrapeState.Running(
                            done = info.progress.getInt(ScrapeWorker.KEY_DONE, 0),
                            total = info.progress.getInt(ScrapeWorker.KEY_TOTAL, 0),
                            matched = info.progress.getInt(ScrapeWorker.KEY_MATCHED, 0),
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        workScrapeRequested = false
                        _scrape.value = ScrapeState.Done(
                            matched = info.outputData.getInt(ScrapeWorker.KEY_MATCHED, 0),
                            failed = info.outputData.getInt(ScrapeWorker.KEY_FAILED, 0),
                        )
                    }
                    WorkInfo.State.FAILED -> {
                        workScrapeRequested = false
                        _scrape.value = ScrapeState.Failed(
                            message = info.outputData.getString(ScrapeWorker.KEY_MESSAGE) ?: "刮削失败",
                            needsSecret = info.outputData.getBoolean(ScrapeWorker.KEY_NEEDS_SECRET, false),
                        )
                    }
                    WorkInfo.State.CANCELLED -> {
                        workScrapeRequested = false
                        _scrape.value = ScrapeState.Idle
                    }
                }
            }
        }
    }

    fun isScrapeRequested(): Boolean = workScrapeRequested

    /** 扫描完成后自动为新条目刮削（仅未匹配作品，不自动重试已退避的失败项）。 */
    fun triggerAutoScrape() {
        scope.launch {
            delay(800)
            if (isScanActive() || isScanRequested()) return@launch
            if (workScrapeRequested) return@launch
            if ((_scrape.value as? ScrapeState.Failed)?.needsSecret == true) return@launch
            if (credentials.loadTmdbSecret() == null) return@launch
            val pending = store.enqueuePendingScrapeTasks()
            if (pending == 0) {
                _scrape.value = ScrapeState.Done(matched = 0, failed = 0)
                return@launch
            }
            workScrapeRequested = true
            _scrape.value = ScrapeState.Running(done = 0, total = pending, matched = 0)
            scheduler.start()
        }
    }

    fun scrapeStart() {
        if (workScrapeRequested) return
        if (isScanActive() || isScanRequested()) return
        if ((_scrape.value as? ScrapeState.Failed)?.needsSecret == true) return

        scope.launch {
            if (credentials.loadTmdbSecret() == null) {
                _scrape.value = ScrapeState.Failed("还没填写 TMDB 凭据", needsSecret = true)
                return@launch
            }
            val pending = store.enqueuePendingScrapeTasks()
            if (pending == 0) {
                _scrape.value = ScrapeState.Done(matched = 0, failed = 0)
                return@launch
            }
            workScrapeRequested = true
            _scrape.value = ScrapeState.Running(done = 0, total = pending, matched = 0)
            scheduler.start()
        }
    }

    /** 取消刮削：只动刮削，**不会**把扫描结果提示（「共 N 个视频」）一起抹掉。 */
    fun cancelScrape() {
        scheduler.cancel()
        workScrapeRequested = false
        _scrape.value = ScrapeState.Idle
    }

    /** 凭据改过了：把「没凭据」造成的封锁也解开。 */
    fun onSecretChanged() {
        if ((_scrape.value as? ScrapeState.Failed)?.needsSecret == true) {
            _scrape.value = ScrapeState.Idle
        }
    }
}
