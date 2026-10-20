package com.lovelev.mediadock.data.repo

import android.net.Uri
import android.os.SystemClock
import androidx.room.withTransaction
import com.lovelev.mediadock.data.local.MediaStoreScanner
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.data.local.LocalScanner
import com.lovelev.mediadock.data.local.LocalMedia
import com.lovelev.mediadock.data.local.LocalFile
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaDao
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.scan.ScanScheduler
import com.lovelev.mediadock.data.scrape.ScrapeScheduler
import com.lovelev.mediadock.data.scrape.ScrapeWorker
import com.lovelev.mediadock.data.scan.ScanWorker
import androidx.work.WorkInfo
import com.lovelev.mediadock.core.data.LibraryQueryRepository
import com.lovelev.mediadock.core.data.MetadataScraper
import com.lovelev.mediadock.core.data.withTitleOverride
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.local.ScrapeStatus
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.core.model.TitleParser
import com.lovelev.mediadock.data.tmdb.TmdbAuthException
import com.lovelev.mediadock.data.tmdb.TmdbException
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import com.lovelev.mediadock.data.tmdb.TmdbSecret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

/**
 * 媒体库：Room 是海报墙的唯一数据源，扫描只负责往库里灌数据。
 *
 * 扫描的规矩（都是硬约束，不得放松）：
 *  - **只由用户主动触发**，绝不在启动时自动跑。
 *  - **顺序 BFS**，一次只发一个 PROPFIND —— 不做并发轰炸。
 *  - **有界**：目录数 / 深度 / 文件数三个上限，撞到就标记 [ScanState.Done.truncated]。
 *  - **可取消**：每轮循环之间检查取消，取消后**不写库**（保留上一次的库，不会留半成品）。
 *  - 扫完在**一个事务里整体替换**，所以扫描过程中海报墙不会忽空忽满。
 *
 * 刮削（Stage 5）另起一次用户动作，只打 TMDB，碰不到 WebDAV。
 */
class LibraryRepository(
    private val db: MediaDatabase,
    private val credentials: SecureCredentialStore,
    private val sources: com.lovelev.mediadock.data.source.SourceRepository,
    private val localScanner: LocalScanner,
    private val mediaStoreScanner: MediaStoreScanner,
    private val store: MediaLibraryStore,
    private val query: LibraryQueryRepository,
    private val scraper: MetadataScraper,
    private val scanScheduler: ScanScheduler,
    private val scrapeScheduler: ScrapeScheduler,
) {

    /** 应用作用域：扫描不跟着页面走，退出媒体库页/旋屏都不会把它打断。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _scrape = MutableStateFlow<ScrapeState>(ScrapeState.Idle)
    val scrape: StateFlow<ScrapeState> = _scrape.asStateFlow()

    /** 只有本会话主动发起过扫描时才把 WorkManager 的终态映射成 UI 状态。 */
    private var workScanRequested = false
    private var workScrapeRequested = false

    /** 本轮扫描涉及的源，以及各源最近一次 WorkInfo。 */
    private var requestedSourceIds: Set<String> = emptySet()
    private val sourceStates = mutableMapOf<String, WorkInfo?>()
    private val observationJobs = mutableListOf<Job>()

    /** 401/429 封锁只作用于对应源；其它源仍可扫描。 */
    private val blockedSourceIds = mutableSetOf<String>()

    init {
        scope.launch {
            sources.refresh()
            sources.sources.collectLatest { list ->
                observationJobs.forEach { it.cancel() }
                observationJobs.clear()
                list.filter { it.isScannable && it.enabled }.forEach { config ->
                    observationJobs += scope.launch {
                        scanScheduler.observe(SourceId(config.id)).collect { info ->
                            sourceStates[config.id] = info
                            onRemoteScanUpdate()
                        }
                    }
                }
            }
        }
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
            triggerAutoScrape()
        }
    }

    init {
        scope.launch {
            scrapeScheduler.observe().collect { info ->
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

    /** 库里的条目（查询层已合并手工覆盖）。 */
    val items: Flow<List<MediaItemEntity>> get() = query.items
    val matchedCount: Flow<Int> = db.mediaDao().observeMatchedCount()

    /** 全部**已缓存**的详情（首页搜索按类型/国家/演员过滤用）。 */
    val details: Flow<List<TitleDetail>> get() = query.details

    /** 详情页要的那一批条目（空列表由查询层挡住）。 */
    fun observeByPaths(paths: List<String>): Flow<List<MediaItemEntity>> =
        query.observeByPaths(paths)

    /** 一个物理版本：同一作品/分集可能来自不同数据源。 */
    data class PlayableVersion(
        val path: String,
        val name: String,
        val sourceId: String,
        val sourceName: String,
        val sizeBytes: Long?,
        val pinned: Boolean,
    )

    /**
     * 某个文件所属内容的全部版本，按选源规则排序：
     * 固定（pinned）> 源优先级（数字小者优先）> 本地源优先 > 体积更大者。
     */
    suspend fun versionsOf(path: String): List<PlayableVersion> {
        val resolved = store.resolve(path) ?: return emptyList()
        val dao = db.mediaDao()
        val episodeId = resolved.episodeId
        val rows = if (episodeId != null) {
            dao.episodeVersionRows(episodeId)
        } else {
            dao.movieVersionRows(resolved.workId)
        }
        if (rows.isEmpty()) return emptyList()
        if (sources.cachedAll().isEmpty()) sources.refresh()
        val byId = sources.cachedAll().associateBy { it.id }
        return rows
            .sortedWith(
                compareByDescending<com.lovelev.mediadock.data.local.WorkVersionRow> { it.pinned > 0 }
                    .thenBy { byId[it.sourceId]?.priority ?: 100 }
                    .thenBy {
                        if (byId[it.sourceId]?.type == com.lovelev.mediadock.core.model.SourceType.LOCAL) 0 else 1
                    }
                    .thenByDescending { it.sizeBytes ?: 0L }
                    .thenBy { it.path },
            )
            .map {
                PlayableVersion(
                    path = it.path,
                    name = it.path.substringAfterLast('/'),
                    sourceId = it.sourceId,
                    sourceName = byId[it.sourceId]?.name ?: it.sourceId,
                    sizeBytes = it.sizeBytes,
                    pinned = it.pinned > 0,
                )
            }
    }

    /** 手工改片名/年份。空标题表示清除覆盖，回到自动解析的结果。 */
    suspend fun saveOverride(path: String, title: String, year: Int?) =
        saveOverride(listOf(path), title, year)

    /** 整部剧一起改：所有集的覆盖都写同一份，重新刮削时用同一检索词。 */
    suspend fun saveOverride(paths: List<String>, title: String, year: Int?) {
        if (paths.isEmpty()) return
        store.saveOverride(paths, title, year)
    }

    /** 手动匹配的候选：剧集走 tv，电影走 movie。 */
    suspend fun searchMatchCandidates(query: String, year: Int?, isTv: Boolean): List<TmdbMatch> {
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: throw TmdbAuthException("还没填写 TMDB 凭据")
        return scraper.searchCandidates(query, year, isTv, secret)
    }

    /**
     * 把一个手选的匹配应用到**整部剧/整部片**（所有 episode paths），
     * 并把选中的片名/年份写进覆盖表 —— 以后重新扫描也沿用。
     */
    suspend fun applyMatch(paths: List<String>, match: TmdbMatch) {
        if (paths.isEmpty()) return
        val now = System.currentTimeMillis()
        paths.forEach { path ->
            store.applyScrape(
                path = path,
                tmdbId = match.tmdbId,
                title = match.title,
                year = match.year,
                posterPath = match.posterPath,
                backdropPath = match.backdropPath,
                overview = match.overview,
                rating = match.rating,
                matched = true,
                scrapedAt = now,
            )
        }
        paths.forEach { store.completeScrapeTask(it) }
        match.title?.takeIf { it.isNotBlank() }?.let { title ->
            store.saveOverride(paths, title, match.year)
        }
        store.deleteDetails(paths)
    }

    /** 用覆盖后的片名重刮整部剧；成功后把同一匹配应用到所有集。 */
    suspend fun rescrapeSeries(paths: List<String>): String? {
        if (paths.isEmpty()) return "找不到条目"
        val dao = db.mediaDao()
        val overrides = db.titleOverrideDao().observeAll().first().associateBy { it.path }
        val items = dao.observeByPaths(paths).first()
            .map { it.withTitleOverride(overrides[it.path]) }
        if (items.isEmpty()) return "找不到条目"
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: return "还没填写 TMDB 凭据"
        return try {
            val match = scrapeItem(items.first(), secret) ?: return "没匹配到"
            applyMatch(paths, match)
            null
        } catch (e: TmdbAuthException) {
            e.message ?: "TMDB 凭据无效"
        } catch (e: TmdbException) {
            e.message ?: "刮削失败"
        }
    }

    /** 只重刮一条（详情页右上角菜单用）。返回 null 表示成功。 */
    suspend fun rescrapeOne(path: String): String? {
        val dao = db.mediaDao()
        val override = db.titleOverrideDao().observeAll().first().firstOrNull { it.path == path }
        val item = dao.observeByPaths(listOf(path)).first().firstOrNull()
            ?.withTitleOverride(override)
            ?: return "找不到这一条"
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: return "还没填写 TMDB 凭据"
        return try {
            val match = scrapeItem(item, secret)
            store.applyScrape(
                path = path,
                tmdbId = match?.tmdbId,
                title = match?.title,
                year = match?.year,
                posterPath = match?.posterPath,
                backdropPath = match?.backdropPath,
                overview = match?.overview,
                rating = match?.rating,
                matched = match != null,
                scrapedAt = System.currentTimeMillis(),
            )
            if (match == null) "没匹配到，可以试试「修改信息」换个片名" else null
        } catch (e: TmdbAuthException) {
            e.message ?: "TMDB 凭据无效"
        } catch (e: TmdbException) {
            e.message ?: "刮削失败"
        }
    }

    /** 本地扫描（SAF / 系统媒体库）的 job；WebDAV 扫描与刮削由 WorkManager 承载。 */
    private var scanJob: Job? = null

    // ---------------------------------------------------------------- 扫描

    fun start() {
        if (scanJob?.isActive == true) return
        // 刮削在跑时不并发扫描：扫描是整表替换 media_items，会把刮削中的结果一起换掉
        if (workScrapeRequested) return

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
            list.forEach { scanScheduler.start(SourceId(it.id)) }
        }
    }

    // ---------------------------------------------------------------- 本地媒体

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
        if (workScrapeRequested) return

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
            triggerAutoScrape()
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
        if (workScrapeRequested) return

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
            triggerAutoScrape()
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

    /** 和 WebDAV 条目走同一个解析器 —— 所以本地文件扫进来也有像样的片名和海报墙。 */
    private fun LocalFile.toScannedFile(): MediaLibraryStore.ScannedFile {
        val storedPath = LocalMedia.toStoredPath(key)
        val seasonFromPath = TitleParser.seasonOfPath(storedPath)
        val parsed = TitleParser.parse(
            fileName = name,
            folderName = folderName,
            inSeasonDir = seasonFromPath != null,
        )
        return MediaLibraryStore.ScannedFile(
            path = storedPath,
            name = name,
            sizeBytes = sizeBytes,
            // WebDAV 那边存的是 RFC1123 字符串；本地是毫秒数，转字符串只当元信息。
            mtime = lastModified.toString(),
            title = parsed.title,
            year = parsed.year,
            // 文件名只给集数时，季号往往在上层目录（S01 / Season 1 / 第1季）——
            // 不补的话 S01/S02 的同集号会互相覆盖排序
            season = parsed.season ?: seasonFromPath,
            episode = parsed.episode,
        )
    }

    // ---------------------------------------------------------------- 刮削

    /** 扫描完成后自动为新条目刮削（仅未匹配作品，不自动重试已退避的失败项）。 */
    private fun triggerAutoScrape() {
        scope.launch {
            kotlinx.coroutines.delay(800)
            if (scanJob?.isActive == true || workScanRequested) return@launch
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
            scrapeScheduler.start()
        }
    }

    fun scrapeStart() {
        if (workScrapeRequested) return
        if (scanJob?.isActive == true || workScanRequested) return
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
            scrapeScheduler.start()
        }
    }

    /** 单条：剧集走 search/tv，电影走 search/movie；年份先带上，搜不到会自动去掉年份重试。 */
    private suspend fun scrapeItem(item: MediaItemEntity, secret: TmdbSecret): TmdbMatch? {
        val isEpisode = item.episode != null || item.season != null
        return scraper.match(item.parsedTitle, item.year, isEpisode, secret)
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
        ids.forEach { scanScheduler.cancel(SourceId(it)) }
        requestedSourceIds = emptySet()
        workScanRequested = false
        _scan.value = ScanState.Idle
    }

    /** 删除数据源前先停掉该源的扫描，避免 Worker 在删除后把文件写回来。 */
    fun cancelScanFor(sourceId: String) {
        scanScheduler.cancel(SourceId(sourceId))
        requestedSourceIds = requestedSourceIds - sourceId
        sourceStates.remove(sourceId)
        if (requestedSourceIds.isEmpty() && workScanRequested) {
            workScanRequested = false
            _scan.value = ScanState.Idle
        }
    }

    /** 取消刮削：只动刮削，**不会**把扫描结果提示（「共 N 个视频」）一起抹掉。 */
    fun cancelScrape() {
        scrapeScheduler.cancel()
        workScrapeRequested = false
        _scrape.value = ScrapeState.Idle
    }

    /** 两个一起停：清库 / 重新登录这类要全部收手的场合。 */
    fun cancelAll() {
        cancelScan()
        cancelScrape()
    }

    /**
     * 解开 401/429 的封锁。
     *
     * 本仓库是**应用作用域**（不像 ViewModel 会随页面重建），所以封锁不会自己消失 ——
     * 用户走「重新登录」这条路时由导航层显式调一次，否则重填密码后仍然扫不了。
     */
    fun reset() {
        cancelAll()
        blockedSourceIds.clear()
        scope.launch { sources.refresh() }
    }

    /** 凭据改过了：把「没凭据」造成的封锁也解开。 */
    fun onSecretChanged() {
        if ((_scrape.value as? ScrapeState.Failed)?.needsSecret == true) {
            _scrape.value = ScrapeState.Idle
        }
    }

    /**
     * 清空媒体库（设置页那个动作）。**不动播放进度** ——
     * 进度按 path 存、是可续播的用户数据，不是缓存；重新扫描后还接着续。
     */
    suspend fun clearLibrary() {
        cancelAll() // 避免扫描或刮削期间执行清理
        store.clearLibrary()
    }
}
