package com.lovelev.mediadock.data.scan

import android.net.Uri
import android.os.SystemClock
import androidx.work.WorkInfo
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.LocalMedia
import com.lovelev.mediadock.data.local.LocalScanner
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.MediaStoreScanner
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.data.repo.ScanState
import com.lovelev.mediadock.data.source.SourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 扫描状态机：远程 WorkManager（按源）与本地 SAF / MediaStore 共用同一个 [scan]。
 *
 * 401/429 的封锁只作用于对应源，保存在本类的会话内集合里，必须由 [resetBlocked] 显式解开。
 * 扫描/刮削互斥通过 [isScrapeRequested] 查询，不复制标志。
 */
class ScanCoordinator(
    private val scope: CoroutineScope,
    private val sources: SourceRepository,
    private val localScanner: LocalScanner,
    private val mediaStoreScanner: MediaStoreScanner,
    private val store: MediaLibraryStore,
    private val db: MediaDatabase,
    private val scheduler: ScanScheduler,
    private val isScrapeRequested: () -> Boolean,
    private val onScanDone: () -> Unit,
) {

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** 只有本会话主动发起过扫描时才把 WorkManager 的终态映射成 UI 状态。 */
    private var workScanRequested = false

    /** 本轮扫描涉及的源，以及各源最近一次 WorkInfo。 */
    private var requestedSourceIds: Set<String> = emptySet()
    private val sourceStates = mutableMapOf<String, WorkInfo?>()
    private val observationJobs = mutableListOf<Job>()

    /** 401/429 封锁只作用于对应源；其它源仍可扫描。 */
    private val blockedSourceIds = mutableSetOf<String>()

    /** 本地扫描（SAF / 系统媒体库）的 job；WebDAV 扫描与刮削由 WorkManager 承载。 */
    private var scanJob: Job? = null

    init {
        scope.launch {
            sources.refresh()
            sources.sources.collectLatest { list ->
                observationJobs.forEach { it.cancel() }
                observationJobs.clear()
                list.filter { it.isScannable && it.enabled }.forEach { config ->
                    observationJobs += scope.launch {
                        scheduler.observe(SourceId(config.id)).collect { info ->
                            sourceStates[config.id] = info
                            onRemoteScanUpdate()
                        }
                    }
                }
            }
        }
    }

    fun isLocalScanActive(): Boolean = scanJob?.isActive == true

    fun isScanRequested(): Boolean = workScanRequested

    fun start() {
        if (scanJob?.isActive == true) return
        // 刮削在跑时不并发扫描：扫描是整表替换 media_items，会把刮削中的结果一起换掉
        if (isScrapeRequested()) return

        scope.launch {
            val list = sources.refresh().filter {
                it.enabled && it.credentialOk && it.isScannable && it.id !in blockedSourceIds
            }
            if (list.isEmpty()) {
                _scan.value = ScanState.Failed("没有可扫描的数据源", blocked = false)
                return@launch
            }
            workScanRequested = true
            requestedSourceIds = list.map { it.id }.toSet()
            sourceStates.keys.retainAll(requestedSourceIds)
            _scan.value = ScanState.Running(directories = 0, files = 0)
            list.forEach { scheduler.start(SourceId(it.id)) }
        }
    }

    /**
     * 本地媒体扫描（用户选的 SAF 目录树）。
     *
     * 和 WebDAV 那次是**同一张表的两半**，各扫各的、互不覆盖 ——
     * 所以两边都用「按前缀删」而不是整表删。
     *
     * 本地遍历不涉及网络，也就没有那条「不许高频请求」的硬约束，跑起来快得多。
     */
    fun startLocal(treeUri: Uri) {
        if (scanJob?.isActive == true) return
        if (isScrapeRequested()) return

        scanJob = scope.launch {
            _scan.value = ScanState.Running(directories = 0, files = 0)
            val started = SystemClock.elapsedRealtime()
            val outcome = localScanner.scan(treeUri) { dirs, files ->
                _scan.value = ScanState.Running(directories = dirs, files = files)
            }
            AppLog.d(
                "scan",
                "文件夹扫描: ${outcome.files.size} 个，用时 ${SystemClock.elapsedRealtime() - started}ms",
            )

            // 取消：不写库，保留上一次的完整结果
            if (!isActive) return@launch

            if (!outcome.readable) {
                _scan.value = ScanState.Failed("读不了这个文件夹，可能授权被撤销了", blocked = false)
                return@launch
            }

            val entities = outcome.files.map { it.toScannedFile() }
            store.replaceScannedFiles(
                SourceId.LOCAL,
                entities,
                missingPrefix = LocalMedia.SAF_PREFIX,
            )
            _scan.value = ScanState.Done(files = entities.size, truncated = outcome.truncated)
            onScanDone()
        }
    }

    /**
     * 扫**系统媒体库**（MediaStore）。**不用用户去找文件夹**，一次权限弹窗就够 ——
     * 这是本地媒体的推荐入口，理由见 [MediaStoreScanner] 的注释（快、不用选、URI 稳定）。
     *
     * 与 SAF 那条**互斥**：跑之前先清掉本地那一半，免得同一个文件被两种方式各扫进来一份。
     */
    fun startMediaStore() {
        if (scanJob?.isActive == true) return
        if (isScrapeRequested()) return

        scanJob = scope.launch {
            _scan.value = ScanState.Running(directories = 0, files = 0)
            val started = SystemClock.elapsedRealtime()
            val outcome = mediaStoreScanner.scan { files ->
                _scan.value = ScanState.Running(directories = 0, files = files)
            }
            AppLog.d("scan", "媒体库扫描: ${outcome.files.size} 个，用时 ${SystemClock.elapsedRealtime() - started}ms")

            if (!isActive) return@launch

            if (!outcome.readable) {
                _scan.value = ScanState.Failed("读不了系统媒体库，检查一下视频权限", blocked = false)
                return@launch
            }

            val entities = outcome.files.map { it.toScannedFile() }
            store.replaceScannedFiles(
                SourceId.LOCAL,
                entities,
                missingPrefix = LocalMedia.CONTENT_SCHEME,
            )
            _scan.value = ScanState.Done(files = entities.size, truncated = outcome.truncated)
            onScanDone()
        }
    }

    /**
     * 只清本地媒体那一半。`clearLibrary()` 是「清空媒体库」（三半都清），
     * 这个只用在「移除本地文件夹」上 —— WebDAV 那边一根汗毛都不动。
     */
    suspend fun clearLocalItems() {
        store.replaceScannedFiles(SourceId.LOCAL, emptyList(), LocalMedia.SAF_PREFIX)
        store.replaceScannedFiles(SourceId.LOCAL, emptyList(), LocalMedia.CONTENT_SCHEME)
    }

    /** 汇总各源扫描进度；全部结束时再落到 Done/Failed。 */
    private suspend fun onRemoteScanUpdate() {
        if (!workScanRequested) return
        val infos = requestedSourceIds.mapNotNull { sourceStates[it] }
        val running = infos.filter {
            it.state == WorkInfo.State.ENQUEUED ||
                it.state == WorkInfo.State.BLOCKED ||
                it.state == WorkInfo.State.RUNNING
        }
        if (running.isNotEmpty()) {
            _scan.value = ScanState.Running(
                directories = running.sumOf { it.progress.getInt(ScanWorker.KEY_DIRS, 0) },
                files = running.sumOf { it.progress.getInt(ScanWorker.KEY_FILES, 0) },
            )
            return
        }
        // 还没见到某个源的 WorkInfo 时不能判定“结束”，否则刚入队就报完成
        val finished = requestedSourceIds.all { sourceStates[it]?.state?.isFinished == true }
        if (!finished) return

        workScanRequested = false
        val failed = requestedSourceIds.mapNotNull { id ->
            sourceStates[id]?.takeIf { it.state == WorkInfo.State.FAILED }?.let { id to it }
        }
        val succeeded = requestedSourceIds.any {
            sourceStates[it]?.state == WorkInfo.State.SUCCEEDED
        }
        if (failed.isNotEmpty()) {
            failed.forEach { (id, info) ->
                if (info.outputData.getBoolean(ScanWorker.KEY_BLOCKED, false)) blockedSourceIds += id
            }
            if (!succeeded) {
                val (_, first) = failed.first()
                _scan.value = ScanState.Failed(
                    message = first.outputData.getString(ScanWorker.KEY_MESSAGE) ?: "扫描失败",
                    blocked = failed.all { (_, info) ->
                        info.outputData.getBoolean(ScanWorker.KEY_BLOCKED, false)
                    },
                )
            }
        }
        if (succeeded) {
            val truncated = requestedSourceIds.any {
                sourceStates[it]?.outputData?.getBoolean(ScanWorker.KEY_TRUNCATED, false) == true
            }
            // 增量扫描的 files 是“本轮处理数”；界面要显示库的总条目数。
            _scan.value = ScanState.Done(files = db.mediaDao().allItems().size, truncated = truncated)
            onScanDone()
        }
    }

    /** 取消扫描：只动扫描，刮削的进度提示不受影响。 */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        val ids = if (requestedSourceIds.isNotEmpty()) {
            requestedSourceIds
        } else {
            sources.cachedAll().filter { it.isScannable }.map { it.id }.toSet()
        }
        ids.forEach { scheduler.cancel(SourceId(it)) }
        requestedSourceIds = emptySet()
        workScanRequested = false
        _scan.value = ScanState.Idle
    }

    /** 删除数据源前先停掉该源的扫描，避免 Worker 在删除后把文件写回来。 */
    fun cancelScanFor(sourceId: String) {
        scheduler.cancel(SourceId(sourceId))
        requestedSourceIds = requestedSourceIds - sourceId
        sourceStates.remove(sourceId)
        if (requestedSourceIds.isEmpty() && workScanRequested) {
            workScanRequested = false
            _scan.value = ScanState.Idle
        }
    }

    /** 解开 401/429 的封锁。由门面的 reset() 在「重新登录」时调用。 */
    fun resetBlocked() {
        blockedSourceIds.clear()
    }
}
