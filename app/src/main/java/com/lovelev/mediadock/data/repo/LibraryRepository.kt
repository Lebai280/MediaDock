package com.lovelev.mediadock.data.repo

import android.net.Uri
import com.lovelev.mediadock.core.data.LibraryQueryRepository
import com.lovelev.mediadock.core.data.MetadataScraper
import com.lovelev.mediadock.data.local.LocalScanner
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.MediaStoreScanner
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.data.scan.ScanCoordinator
import com.lovelev.mediadock.data.scan.ScanScheduler
import com.lovelev.mediadock.data.scrape.ScrapeCoordinator
import com.lovelev.mediadock.data.scrape.ScrapeScheduler
import com.lovelev.mediadock.data.source.SourceRepository
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 媒体库门面：Room 是海报墙的唯一数据源，扫描只负责往库里灌数据。
 *
 * 扫描的规矩（都是硬约束，不得放松）：
 *  - **只由用户主动触发**，绝不在启动时自动跑。
 *  - **顺序 BFS**，一次只发一个 PROPFIND —— 不做并发轰炸。
 *  - **有界**：目录数 / 深度 / 文件数三个上限，撞到就标记 [ScanState.Done.truncated]。
 *  - **可取消**：每轮循环之间检查取消，取消后**不写库**（保留上一次的库，不会留半成品）。
 *  - 扫完在**一个事务里整体替换**，所以扫描过程中海报墙不会忽空忽满。
 *
 * 具体职责已拆到 [ScanCoordinator]（远程 + 本地扫描）、[ScrapeCoordinator]（刮削状态）、
 * [MetadataMatchService]（手工匹配/重刮）与 [PlaybackVersionRepository]（版本排序）；
 * 本类只保留装配、查询委托与生命周期入口。
 */
class LibraryRepository(
    private val db: MediaDatabase,
    private val credentials: SecureCredentialStore,
    private val sources: SourceRepository,
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

    private val versionRepository = PlaybackVersionRepository(db, store, sources)
    private val matchService = MetadataMatchService(db, credentials, scraper, store)

    private val scrapeCoordinator: ScrapeCoordinator
    private val scanCoordinator: ScanCoordinator

    init {
        // 先建刮削、再建扫描：两个 lambda 只在事件真正发生时读取对方，构造期不互相解引用。
        scrapeCoordinator = ScrapeCoordinator(
            scope = scope,
            scheduler = scrapeScheduler,
            credentials = credentials,
            store = store,
            isScanActive = { scanCoordinator.isLocalScanActive() },
            isScanRequested = { scanCoordinator.isScanRequested() },
        )
        scanCoordinator = ScanCoordinator(
            scope = scope,
            sources = sources,
            localScanner = localScanner,
            mediaStoreScanner = mediaStoreScanner,
            store = store,
            db = db,
            scheduler = scanScheduler,
            isScrapeRequested = { scrapeCoordinator.isScrapeRequested() },
            onScanDone = { scrapeCoordinator.triggerAutoScrape() },
        )
    }

    val scan: StateFlow<ScanState> get() = scanCoordinator.scan
    val scrape: StateFlow<ScrapeState> get() = scrapeCoordinator.scrape

    /** 库里的条目（查询层已合并手工覆盖）。 */
    val items: Flow<List<MediaItemEntity>> get() = query.items

    /** 全部**已缓存**的详情（首页搜索按类型/国家/演员过滤用）。 */
    val details: Flow<List<TitleDetail>> get() = query.details

    /** 详情页要的那一批条目（空列表由查询层挡住）。 */
    fun observeByPaths(paths: List<String>): Flow<List<MediaItemEntity>> =
        query.observeByPaths(paths)

    /** 一个物理版本：同一作品/分集可能来自不同数据源。 */
    suspend fun versionsOf(path: String): List<PlayableVersion> = versionRepository.versionsOf(path)

    /** 整部剧一起改：所有集的覆盖都写同一份，重新刮削时用同一检索词。 */
    suspend fun saveOverride(paths: List<String>, title: String, year: Int?) =
        matchService.saveOverride(paths, title, year)

    /** 手动匹配的候选：剧集走 tv，电影走 movie。 */
    suspend fun searchMatchCandidates(query: String, year: Int?, isTv: Boolean): List<TmdbMatch> =
        matchService.searchMatchCandidates(query, year, isTv)

    /**
     * 把一个手选的匹配应用到**整部剧/整部片**（所有 episode paths），
     * 并把选中的片名/年份写进覆盖表 —— 以后重新扫描也沿用。
     */
    suspend fun applyMatch(paths: List<String>, match: TmdbMatch) =
        matchService.applyMatch(paths, match)

    /** 用覆盖后的片名重刮整部剧；成功后把同一匹配应用到所有集。 */
    suspend fun rescrapeSeries(paths: List<String>): String? =
        matchService.rescrapeSeries(paths)

    /** 只重刮一条（详情页右上角菜单用）。返回 null 表示成功。 */
    suspend fun rescrapeOne(path: String): String? =
        matchService.rescrapeOne(path)

    // ---------------------------------------------------------------- 扫描

    fun start() = scanCoordinator.start()

    fun startLocal(treeUri: Uri) = scanCoordinator.startLocal(treeUri)

    fun startMediaStore() = scanCoordinator.startMediaStore()

    suspend fun clearLocalItems() = scanCoordinator.clearLocalItems()

    fun cancelScan() = scanCoordinator.cancelScan()

    /** 删除数据源前先停掉该源的扫描，避免 Worker 在删除后把文件写回来。 */
    fun cancelScanFor(sourceId: String) = scanCoordinator.cancelScanFor(sourceId)

    // ---------------------------------------------------------------- 刮削

    fun scrapeStart() = scrapeCoordinator.scrapeStart()

    /** 取消刮削：只动刮削，**不会**把扫描结果提示（「共 N 个视频」）一起抹掉。 */
    fun cancelScrape() = scrapeCoordinator.cancelScrape()

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
        scanCoordinator.resetBlocked()
        scope.launch { sources.refresh() }
    }

    /** 凭据改过了：把「没凭据」造成的封锁也解开。 */
    fun onSecretChanged() = scrapeCoordinator.onSecretChanged()

    /**
     * 清空媒体库（设置页那个动作）。**不动播放进度** ——
     * 进度按 path 存、是可续播的用户数据，不是缓存；重新扫描后还接着续。
     */
    suspend fun clearLibrary() {
        cancelAll() // 避免扫描或刮削期间执行清理
        store.clearLibrary()
    }
}
