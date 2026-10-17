package com.lovelev.mediadock.data.repo

import android.net.Uri
import android.os.SystemClock
import androidx.room.withTransaction
import com.lovelev.mediadock.data.local.MediaStoreScanner
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.data.local.LocalScanner
import com.lovelev.mediadock.data.local.LocalMedia
import com.lovelev.mediadock.data.local.LocalFile
import com.lovelev.mediadock.data.MediaTypes
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaDao
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.local.TitleOverride
import com.lovelev.mediadock.data.local.ScrapeStatus
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.data.tmdb.TitleParser
import com.lovelev.mediadock.data.tmdb.TmdbAuthException
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.data.tmdb.TmdbException
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import com.lovelev.mediadock.data.tmdb.TmdbSecret
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavEntry
import com.lovelev.mediadock.data.webdav.WebDavException
import com.lovelev.mediadock.data.webdav.WebDavUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
 * 扫描的规矩（都是硬约束，别松）：
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
    private val client: WebDavClient,
    private val tmdb: TmdbClient,
    private val credentials: SecureCredentialStore,
    private val localScanner: LocalScanner,
    private val mediaStoreScanner: MediaStoreScanner,
) {

    /** 应用作用域：扫描不跟着页面走，退出媒体库页/旋屏都不会把它打断。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    private val _scrape = MutableStateFlow<ScrapeState>(ScrapeState.Idle)
    val scrape: StateFlow<ScrapeState> = _scrape.asStateFlow()

    /**
     * 库里的条目，**已经把手工覆盖的片名/年份合并进来了** ——
     * 海报墙、详情页、重新刮削的检索词都读这一份，保证「改过名字」到处都生效。
     */
    val items: Flow<List<MediaItemEntity>> =
        combine(db.mediaDao().observeAll(), db.titleOverrideDao().observeAll()) { all, overrides ->
            val byPath = overrides.associateBy { it.path }
            all.map { item -> item.withOverride(byPath[item.path]) }
        }
    val matchedCount: Flow<Int> = db.mediaDao().observeMatchedCount()

    /**
     * 全部**已缓存**的详情（首页搜索按类型/国家/演员过滤用）。
     * 注意详情是懒加载的（打开详情页才拉），所以这里不一定齐 —— 用「补齐影片信息」可以补齐。
     */
    val details: Flow<List<TitleDetail>> = db.titleDetailDao().observeAll()

    /**
     * 详情页要的那一批条目。
     * **空列表必须挡在这里** —— Room 会把 `IN (:paths)` 展开成 `IN ()`，那是 SQL 语法错。
     */
    fun observeByPaths(paths: List<String>): Flow<List<MediaItemEntity>> =
        if (paths.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                db.mediaDao().observeByPaths(paths),
                db.titleOverrideDao().observeAll(),
            ) { rows, overrides ->
                val byPath = overrides.associateBy { it.path }
                rows.map { item -> item.withOverride(byPath[item.path]) }
            }
        }

    /** 手工改片名/年份。空标题表示清除覆盖，回到自动解析的结果。 */
    suspend fun saveOverride(path: String, title: String, year: Int?) {
        if (title.isBlank()) {
            db.titleOverrideDao().remove(path)
        } else {
            db.titleOverrideDao().upsert(TitleOverride(path = path, title = title.trim(), year = year))
        }
    }

    /** 只重刮一条（详情页右上角菜单用）。返回 null 表示成功。 */
    suspend fun rescrapeOne(path: String): String? {
        val dao = db.mediaDao()
        val item = dao.observeByPaths(listOf(path)).first().firstOrNull()
            ?: return "找不到这一条"
        val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: return "还没填写 TMDB 凭据"
        return try {
            val match = scrapeItem(item, secret)
            dao.markScraped(
                path = path,
                state = if (match != null) ScrapeStatus.MATCHED else ScrapeStatus.FAILED,
                tmdbId = match?.tmdbId,
                posterPath = match?.posterPath,
                backdropPath = match?.backdropPath,
                overview = match?.overview,
                rating = match?.rating,
                scrapedAt = System.currentTimeMillis(),
            )
            if (match == null) "没匹配到，可以试试「修改信息」换个片名" else null
        } catch (e: TmdbAuthException) {
            e.message ?: "TMDB 凭据无效"
        } catch (e: TmdbException) {
            e.message ?: "刮削失败"
        }
    }

    /** 扫描与刮削各有独立的 job —— 取消其中一个不该影响另一个。 */
    private var scanJob: Job? = null
    private var scrapeJob: Job? = null

    // ---------------------------------------------------------------- 扫描

    fun start() {
        if (scanJob?.isActive == true) return
        // 刮削在跑时不并发扫描：扫描是整表替换 media_items，会把刮削中的结果一起换掉
        if (scrapeJob?.isActive == true) return
        // 401/429 之后不许再试 —— 那会连累所有设备的 WebDAV
        if ((_scan.value as? ScanState.Failed)?.blocked == true) return

        scanJob = scope.launch {
            val saved = credentials.load()
            val root = saved?.let { WebDavUrl.rootCollection(it.url, it.rootPath) }
            if (saved == null || root == null) {
                _scan.value = ScanState.Failed("数据源不可用，请重新添加", blocked = false)
                return@launch
            }
            val auth = WebDavClient.basicAuth(saved.username, saved.password)

            _scan.value = ScanState.Running(directories = 0, files = 0)

            val found = ArrayList<MediaItemEntity>()
            val queued = HashSet<String>().apply { add("") }
            val queue = ArrayDeque<String>().apply { add("") }
            var directories = 0
            var truncated = false

            try {
                while (queue.isNotEmpty() && isActive) {
                    if (directories >= MAX_DIRECTORIES) {
                        truncated = true
                        break
                    }
                    val dir = queue.removeFirst()
                    directories++

                    val entries = try {
                        client.propfind(
                            collection = WebDavUrl.child(root, dir, isCollection = true),
                            root = root,
                            basicAuthHeader = auth,
                        )
                    } catch (e: WebDavException.NotFound) {
                        // 扫描途中目录被改名/删掉：跳过它，别让整次扫描失败
                        if (dir.isEmpty()) throw e
                        continue
                    }

                    for (entry in entries) {
                        if (entry.isDirectory) {
                            if (depth(dir) < MAX_DEPTH && queued.add(entry.path)) {
                                queue.add(entry.path)
                            }
                        } else if (MediaTypes.isVideoFile(entry.name) && found.size < MAX_FILES) {
                            found += entry.toEntity()
                        }
                    }
                    _scan.value = ScanState.Running(directories, found.size)
                }

                if (found.size >= MAX_FILES) truncated = true

                // 被取消：**不写库**，保留上一次的完整结果（否则会把半份结果当成扫描结果落库）
                if (!isActive) return@launch

                db.withTransaction {
                    val dao = db.mediaDao()
                    // 先接住上一次的刮削结果，再整批替换 —— 否则每次重扫都要重刮全库
                    val merged = dao.keepScraped(found)
                    // **不是 deleteAll**：库里还有本地媒体那两半，一起删会把它们扫掉
                    dao.deleteWebDavItems(LocalMedia.SAF_LIKE, LocalMedia.CONTENT_LIKE)
                    dao.insertAll(merged)
                }
                _scan.value = ScanState.Done(files = found.size, truncated = truncated)
            } catch (e: WebDavException) {
                // 单次尝试即返回，绝不重试
                val blocked = e is WebDavException.Unauthorized || e is WebDavException.Locked
                AppLog.e("scan", "扫描失败(blocked=$blocked): ${e.userMessage}")
                _scan.value = ScanState.Failed(e.userMessage, blocked)
            }
        }
    }

    /**
     * 解析片名是在**扫描时**顺手做的：纯本地计算、不花请求，
     * 而且海报墙立刻就有像样的标题可显示（刮削不上的也不至于显示成整串文件名）。
     */
    private fun WebDavEntry.toEntity(): MediaItemEntity {
        val folderName = path.substringBeforeLast('/', "").substringAfterLast('/')
        val parsed = TitleParser.parse(fileName = name, folderName = folderName)
        return MediaItemEntity(
            path = path,
            name = name,
            sizeBytes = size,
            lastModified = lastModified,
            parsedTitle = parsed.title,
            year = parsed.year,
            // 文件名只给集数时，季号往往在上层目录（S01 / Season 1 / 第1季）——
            // 不补的话 S01/S02 的同集号会互相覆盖排序
            season = parsed.season ?: TitleParser.seasonOfPath(path),
            episode = parsed.episode,
        )
    }

    /**
     * 扫描是整批替换，但**刮削结果必须留下来**：
     * 每次加几集就重刮全库，既慢又白烧 TMDB 配额（用户明确反馈过）。
     * 按 path 把上一次的 MATCHED / FAILED 结果接回去；文件大小变了说明文件被换过，
     * 旧匹配不再可信，就让它重新刮。
     */
    private suspend fun MediaDao.keepScraped(fresh: List<MediaItemEntity>): List<MediaItemEntity> {
        val previous = allItems().associateBy { it.path }
        return fresh.map { item ->
            val old = previous[item.path] ?: return@map item
            val sameFile = old.sizeBytes == null || item.sizeBytes == null || old.sizeBytes == item.sizeBytes
            if (sameFile && old.scrapeState != ScrapeStatus.UNSCRAPED) {
                item.copy(
                    scrapeState = old.scrapeState,
                    tmdbId = old.tmdbId,
                    posterPath = old.posterPath,
                    backdropPath = old.backdropPath,
                    overview = old.overview,
                    rating = old.rating,
                    scrapedAt = old.scrapedAt,
                )
            } else {
                item
            }
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
        if (scrapeJob?.isActive == true) return

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

            val entities = outcome.files.map { it.toEntity() }
            db.withTransaction {
                val dao = db.mediaDao()
                val merged = dao.keepScraped(entities)
                dao.deleteLocalItems(LocalMedia.SAF_LIKE, LocalMedia.CONTENT_LIKE)
                dao.insertAll(merged)
            }
            _scan.value = ScanState.Done(files = entities.size, truncated = outcome.truncated)
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
        if (scrapeJob?.isActive == true) return

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

            val entities = outcome.files.map { it.toEntity() }
            db.withTransaction {
                val dao = db.mediaDao()
                val merged = dao.keepScraped(entities)
                dao.deleteLocalItems(LocalMedia.SAF_LIKE, LocalMedia.CONTENT_LIKE)
                dao.insertAll(merged)
            }
            _scan.value = ScanState.Done(files = entities.size, truncated = outcome.truncated)
        }
    }

    /**
     * 只清本地媒体那一半。`clearLibrary()` 是「清空媒体库」（三半都清），
     * 这个只用在「移除本地文件夹」上 —— WebDAV 那边一根汗毛都不动。
     */
    suspend fun clearLocalItems() {
        db.mediaDao().deleteLocalItems(LocalMedia.SAF_LIKE, LocalMedia.CONTENT_LIKE)
    }

    /** 和 WebDAV 条目走同一个解析器 —— 所以本地文件扫进来也有像样的片名和海报墙。 */
    private fun LocalFile.toEntity(): MediaItemEntity {
        val parsed = TitleParser.parse(fileName = name, folderName = folderName)
        val storedPath = LocalMedia.toStoredPath(key)
        return MediaItemEntity(
            path = storedPath,
            name = name,
            sizeBytes = sizeBytes,
            // 实体里这列是 String?（WebDAV 那边存的是 RFC1123 字符串）。
            // 本地拿到的是毫秒数，直接转字符串 —— 这列目前不用来比较，只当元信息存着。
            lastModified = lastModified.toString(),
            parsedTitle = parsed.title,
            year = parsed.year,
            // 文件名只给集数时，季号往往在上层目录（S01 / Season 1 / 第1季）——
            // 不补的话 S01/S02 的同集号会互相覆盖排序
            season = parsed.season ?: TitleParser.seasonOfPath(storedPath),
            episode = parsed.episode,
        )
    }

    // ---------------------------------------------------------------- 刮削

    fun scrapeStart() {
        if (scrapeJob?.isActive == true) return
        // 扫描在跑时不并发刮削（理由同 start()）
        if (scanJob?.isActive == true) return
        if ((_scrape.value as? ScrapeState.Failed)?.needsSecret == true) {
            // 凭据没填/不对：界面应该把人引去设置，而不是反复空转
            return
        }

        scrapeJob = scope.launch {
            val secret = credentials.loadTmdbSecret()?.let(::TmdbSecret)
            if (secret == null) {
                _scrape.value = ScrapeState.Failed("还没填写 TMDB 凭据", needsSecret = true)
                return@launch
            }

            val dao = db.mediaDao()
            // 覆盖过的片名要参与检索，否则「改了名字再刮」等于没改
            val overrideByPath = db.titleOverrideDao().observeAll().first().associateBy { it.path }
            val pending = dao.pendingScrape().map { it.withOverride(overrideByPath[it.path]) }
            if (pending.isEmpty()) {
                _scrape.value = ScrapeState.Done(matched = 0, failed = 0)
                return@launch
            }

            _scrape.value = ScrapeState.Running(done = 0, total = pending.size, matched = 0)
            var done = 0
            var matched = 0
            var failed = 0

            try {
                // 4 个一批：既不是一发一个那么慢，也不会把 TMDB 打爆
                for (batch in pending.chunked(SCRAPE_CONCURRENCY)) {
                    if (!isActive) return@launch
                    val results = coroutineScope {
                        batch.map { item ->
                            async {
                                // 单条失败（网络抖动 / 非 200）只算这一条没匹配上，**不让整轮中断** ——
                                // 否则 92 条跑到一半遇到一次抖动就前功尽弃。
                                // 凭据无效是另一回事，必须立刻中止整轮（照旧抛出去）。
                                val match = try {
                                    scrapeItem(item, secret)
                                } catch (e: TmdbAuthException) {
                                    throw e
                                } catch (e: TmdbException) {
                                    null
                                }
                                item.path to match
                            }
                        }.awaitAll()
                    }
                    for ((path, match) in results) {
                        dao.markScraped(
                            path = path,
                            state = if (match != null) ScrapeStatus.MATCHED else ScrapeStatus.FAILED,
                            tmdbId = match?.tmdbId,
                            posterPath = match?.posterPath,
                            backdropPath = match?.backdropPath,
                            overview = match?.overview,
                            rating = match?.rating,
                            scrapedAt = System.currentTimeMillis(),
                        )
                        if (match != null) matched++ else failed++
                        done++
                    }
                    _scrape.value = ScrapeState.Running(done, pending.size, matched)
                }
                _scrape.value = ScrapeState.Done(matched = matched, failed = failed)
            } catch (e: TmdbAuthException) {
                // 凭据无效：整轮停掉，剩下的别试了
                _scrape.value = ScrapeState.Failed(e.message ?: "TMDB 凭据无效", needsSecret = true)
            } catch (e: TmdbException) {
                _scrape.value = ScrapeState.Failed(e.message ?: "刮削失败", needsSecret = false)
            }
        }
    }

    /** 单条：剧集走 search/tv，电影走 search/movie；年份先带上，搜不到会自动去掉年份重试。 */
    private suspend fun scrapeItem(item: MediaItemEntity, secret: TmdbSecret): TmdbMatch? {
        val isEpisode = item.episode != null || item.season != null
        return if (isEpisode) {
            tmdb.searchTv(item.parsedTitle, item.year, secret)
        } else {
            tmdb.searchMovie(item.parsedTitle, item.year, secret)
        }
    }

    /** 取消扫描：只动扫描，刮削的进度提示不受影响。 */
    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _scan.value = ScanState.Idle
    }

    /** 取消刮削：只动刮削，**不会**把扫描结果提示（「共 N 个视频」）一起抹掉。 */
    fun cancelScrape() {
        scrapeJob?.cancel()
        scrapeJob = null
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
        cancelAll() // 别一边扫/刮一边清
        db.withTransaction { db.mediaDao().deleteAll() }
    }

    private fun depth(path: String): Int =
        if (path.isEmpty()) 0 else path.count { it == '/' } + 1

    private companion object {
        const val MAX_DEPTH = 6
        const val MAX_DIRECTORIES = 4_000
        const val MAX_FILES = 20_000
        const val SCRAPE_CONCURRENCY = 4
    }
}

/** 把手工覆盖并进条目：只改显示/检索用的片名与年份，不动路径。 */
private fun MediaItemEntity.withOverride(override: TitleOverride?): MediaItemEntity =
    if (override == null) this else copy(parsedTitle = override.title, year = override.year)
