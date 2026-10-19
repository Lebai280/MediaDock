package com.lovelev.mediadock.di

import android.content.Context
import androidx.room.Room
import com.lovelev.mediadock.data.local.MediaStoreScanner
import com.lovelev.mediadock.data.local.LocalScanner
import com.lovelev.mediadock.data.local.MigrationGuard
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.data.repo.FavoriteRepository
import com.lovelev.mediadock.data.repo.DetailsRepository
import com.lovelev.mediadock.data.repo.LibraryRepository
import com.lovelev.mediadock.data.repo.PlaylistRepository
import com.lovelev.mediadock.data.repo.ProgressRepository
import com.lovelev.mediadock.data.repo.SubtitleRepository
import com.lovelev.mediadock.data.subtitle.OpenSubtitlesProvider
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.data.update.UpdateChecker
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.player.PlaybackSelection
import com.lovelev.mediadock.ui.detail.DetailSelection
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 手写依赖容器。
 *
 * 应用规模不需要 Hilt —— 少一个注解处理器、少一层构建复杂度，收益大于损失。
 */
class AppContainer(context: Context) {

    /** 播放页建 MediaController 要用它（控制器由 ViewModel 持有，见 PlayerViewModel）。 */
    val appContext: Context = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        // 仅连接层瞬时故障重试；HTTP 响应（尤其 401）绝不重发 ——
        // 未设置 Authenticator，OkHttp 就不会对 401 做内置重试。
        .retryOnConnectionFailure(true)
        .build()

    /**
     * 媒体流专用客户端。**刻意不设 callTimeout** ——
     * `callTimeout` 是「整个请求」的总时限，而一个几 GB 的视频要连着读好几分钟，
     * 设了它就会在中途被掐断（表现是播一会儿就断，看着像网络问题）。
     * 拖进度条的 Range 由 ExoPlayer 自己按 seek 位置发，这里不碰。
     */
    val mediaOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        // OkHttp 默认会加 Accept-Encoding: gzip 并**透明解压**响应体，
        // 那样 Content-Length 与实际字节数就对不上了，字节区间（Range）随即失效 ——
        // 正是「拖进度条」要用的东西。媒体流一律要原始字节。
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("Accept-Encoding", "identity").build(),
            )
        }
        .build()

    val webDavClient: WebDavClient = WebDavClient(okHttpClient)

    /** TMDB 走同一个普通客户端：它是短请求，跟媒体流不是一回事，可以带 callTimeout。 */
    val tmdbClient: TmdbClient = TmdbClient(okHttpClient)

    val credentials: SecureCredentialStore = SecureCredentialStore(appContext)

    /** 「下一个要播的文件」，由浏览页/媒体库写入、播放页读取。 */
    val playbackSelection: PlaybackSelection = PlaybackSelection()

    /** 详情页要看哪一批条目，由海报墙写入、详情页读取。 */
    val detailSelection: DetailSelection = DetailSelection()

    val database: MediaDatabase by lazy {
        // B6：v8→v9 前先自动备份（含 WAL 检查点），状态写入 filesDir/migration/v9_state.json。
        val guard = MigrationGuard(appContext)
        guard.prepareIfNeeded(MediaDatabase.VERSION)
        val db = Room.databaseBuilder(appContext, MediaDatabase::class.java, "library.db")
            // 纯新增表走真迁移 —— 进度/收藏/改名都是用户数据，不能丢。
            // 迁移清单在 MediaDatabase.MIGRATIONS 里，这里整批注册，避免逐项遗漏。
            .addMigrations(*MediaDatabase.MIGRATIONS)
            // **升级方向不留破坏性回退**：缺迁移就让 Room 抛异常（响亮），绝不静默清库。
            // 只有「降级」（手动装了旧版 APK）才允许破坏重建：那是人工动作，
            // 而且旧代码本来也读不懂新结构。dropAllTables = false = **只丢结构变了的表**。
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = false)
            .build()
        // 立即打开：迁移失败时在这里恢复备份，而不是等某次查询把异常抛到某个协程里。
        runCatching { db.openHelper.writableDatabase }
            .onSuccess {
                guard.markSuccess(MediaDatabase.VERSION)
                if (com.lovelev.mediadock.BuildConfig.DEBUG) {
                    com.lovelev.mediadock.data.local.ShadowCompare.run(db)
                }
            }
            .onFailure {
                guard.restoreLatest()
                throw it
            }
        db
    }

    /** v9 路径 API → UUID/内容模型的写适配层，所有仓库共用。 */
    val libraryStore: com.lovelev.mediadock.data.local.MediaLibraryStore by lazy {
        com.lovelev.mediadock.data.local.MediaLibraryStore(database)
    }

    /** 只读查询层（海报墙 / 详情 / 搜索）。 */
    val libraryQuery: com.lovelev.mediadock.core.data.LibraryQueryRepository by lazy {
        com.lovelev.mediadock.core.data.LibraryQueryRepository(database)
    }

    /** TMDB 匹配逻辑（纯网络，无状态机）。 */
    val metadataScraper: com.lovelev.mediadock.core.data.MetadataScraper by lazy {
        com.lovelev.mediadock.core.data.MetadataScraper(tmdbClient)
    }

    val library: LibraryRepository by lazy {
        LibraryRepository(
            database, webDavClient, credentials,
            LocalScanner(appContext), MediaStoreScanner(appContext), libraryStore,
            libraryQuery, metadataScraper,
        )
    }

    val progress: ProgressRepository by lazy { ProgressRepository(database, libraryStore) }

    val favorites: FavoriteRepository by lazy { FavoriteRepository(database, libraryStore) }

    /** 用户片单。 */
    val playlists: PlaylistRepository by lazy { PlaylistRepository(database, libraryStore) }

    /** 详情页的补充信息（类型/国家/时长/演职人员），打开详情页时才拉。 */
    val details: DetailsRepository by lazy {
        DetailsRepository(database, tmdbClient, credentials, libraryStore)
    }

    /** 在线版本检查：GET 站点上的 `mediadock/version.json`（见 UpdateChecker.UPDATE_URL）。 */
    val updateChecker: UpdateChecker = UpdateChecker(okHttpClient)

    /**
     * 字幕：搜索 / 下载 / 落盘。**源是可切换的** —— 现在只接了 OpenSubtitles，
     * 以后加别的站只要往 providers 里再塞一个实现（界面只认 SubtitleProvider 接口）。
     */
    val subtitles: SubtitleRepository by lazy {
        SubtitleRepository(
            context = appContext,
            providers = listOf(
                OpenSubtitlesProvider(okHttpClient) { credentials.loadSubtitleCredentials() },
            ),
        )
    }
}
