package com.lovelev.mediadock

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import com.lovelev.mediadock.ui.search.SearchScreen
import androidx.compose.ui.Alignment
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import com.lovelev.mediadock.ui.history.HistoryScreen
import com.lovelev.mediadock.ui.mine.MineScreen
import com.lovelev.mediadock.ui.theme.resolveDark
import com.lovelev.mediadock.ui.theme.ThemeMode
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.ui.browse.BrowserScreen
import com.lovelev.mediadock.ui.browse.BrowserViewModel
import com.lovelev.mediadock.ui.detail.DetailScreen
import com.lovelev.mediadock.ui.detail.DetailViewModel
import com.lovelev.mediadock.ui.favorites.FavoritesScreen
import com.lovelev.mediadock.ui.favorites.FavoritesViewModel
import com.lovelev.mediadock.ui.library.LibraryScreen
import com.lovelev.mediadock.ui.library.LibraryViewModel
import com.lovelev.mediadock.ui.nav.PlayerImmersive
import com.lovelev.mediadock.ui.nav.PlayerOrientation
import com.lovelev.mediadock.ui.nav.Routes
import com.lovelev.mediadock.ui.nav.SystemBarAppearance
import com.lovelev.mediadock.ui.player.MiniPlayerBar
import com.lovelev.mediadock.ui.player.NowPlayingViewModel
import com.lovelev.mediadock.ui.player.PlayerScreen
import com.lovelev.mediadock.ui.player.PlayerViewModel
import com.lovelev.mediadock.ui.settings.LogScreen
import com.lovelev.mediadock.ui.settings.SettingsScreen
import com.lovelev.mediadock.ui.settings.SettingsViewModel
import com.lovelev.mediadock.ui.source.AddSourceScreen
import com.lovelev.mediadock.ui.source.SourceViewModel
import com.lovelev.mediadock.ui.theme.MediaDockTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as MediaDockApp).container

        setContent {
            // 主题三态：跟随系统 / 浅色 / 深色（设置 → 外观 里切）
            val storedMode by container.credentials.themeMode
                .collectAsState(initial = container.credentials.cachedThemeMode)
            val themeMode = ThemeMode.fromStored(storedMode)

            MediaDockTheme(darkTheme = themeMode.resolveDark()) {
                val navController = rememberNavController()

                // 起始页看本地有没有已保存的数据源。只读 DataStore，不发任何网络请求。
                var startRoute by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    startRoute =
                        if (container.credentials.load() != null) Routes.LIBRARY else Routes.ADD_SOURCE
                }

                val start = startRoute
                if (start != null) {
                    HomeScaffold(navController, start, container, darkTheme = themeMode.resolveDark())
                }
            }
        }
    }
}

/** 四个 Tab 的顺序就是参考图里的顺序：媒体库在最左，启动也落在它上面。 */
private val TABS = listOf(
    Triple(Routes.LIBRARY, "媒体库", Icons.Filled.Home),
    Triple(Routes.FAVORITES, "收藏", Icons.Filled.FavoriteBorder),
    // 浏览与设置已经收进「我的」（照 NagoMusic 的 profile 页）——底部只留三个
    Triple(Routes.MINE, "我的", Icons.Filled.Person),
)

/**
 * 底部四个 Tab：媒体库 / 收藏 / 浏览 / 设置。
 * 详情页和播放页不放 Tab —— 它们是压进去的页面，各自全屏。
 */
@Composable
private fun HomeScaffold(
    navController: NavHostController,
    start: String,
    container: AppContainer,
    darkTheme: Boolean,
) {
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val onTab = TABS.any { it.first == currentRoute }

    // 迷你播放条挂在外壳上（活动级），不会因为进出播放页被清掉。
    // 播放页自己不显示它 —— 那里整屏都在播，再挂一条是多余的。
    val nowPlaying: NowPlayingViewModel =
        viewModel(factory = NowPlayingViewModel.factory(container))
    val showMini = currentRoute != null &&
        currentRoute != Routes.PLAYER &&
        currentRoute != Routes.ADD_SOURCE

    // 播放页自动横屏、离开时还回系统（见 PlayerOrientation 里的注释，别挪回播放页里）
    PlayerOrientation(currentRoute)
    PlayerImmersive(currentRoute)
    // 详情页整页深色，系统栏图标必须是浅色；其余页面跟随 App 主题
    SystemBarAppearance(currentRoute, darkTheme)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // 尺寸对齐 NagoMusic：底栏 56dp + 迷你条 52dp + 缝 6dp + 离底 6dp。
            // M3 的 NavigationBar 默认 80dp（内部是 defaultMinSize，所以外面给 .height 能压下来），
            // 80 在手机上明显偏高。
            Column {
                if (showMini) {
                    MiniPlayerBar(
                        vm = nowPlaying,
                        onOpen = {
                            // 进程被杀过的话 playbackSelection 是空的（它只在内存里）——
                            // 先从控制器把队列读回来，否则播放页会显示「没有选中要播放的文件」
                            nowPlaying.syncSelection()
                            navController.navigate(Routes.PLAYER)
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (onTab) {
                    // 底部：左边是三个 Tab，右边一颗圆形搜索按钮 —— 照 NagoMusic 的底栏。
                    // 搜索不再是首页顶上的输入框，而是点这里进独立搜索页。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 覆盖 M3 默认：它默认的 secondaryContainer 是淡紫的，跟柠檬绿不搭；
                        // 选中态改成淡柠檬绿胶囊 + 深色字（浅色主题下才读得清）。
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            // 底部内边距由外面这个 Row 统一给，否则按钮和导航栏会一高一低
                            windowInsets = WindowInsets(0),
                            modifier = Modifier.weight(1f).height(56.dp),
                        ) {
                            TABS.forEach { (route, label, icon) ->
                                TabItem(route, label, icon, currentRoute, navController)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = { navController.navigate(Routes.SEARCH) },
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = "搜索")
                        }
                    }
                }
            }
        },
    ) { insets ->
        // 播放页不套 Scaffold 的内边距 —— 不然视频会被状态栏/导航栏夹出黑边（用户提过）。
        // 详情页也一样：它本身是深色整页（海报取色铺满），套内边距会在上下露出窗口底色，
        // 看起来就是两条白边（2026-10-17 用户反馈）。内边距改由详情页自己处理。
        val contentModifier =
            if (currentRoute == Routes.PLAYER || currentRoute == Routes.DETAIL) {
                Modifier
            } else {
                Modifier.padding(insets)
            }
        AppNavHost(
            navController = navController,
            startRoute = start,
            modifier = contentModifier,
            container = container,
        )
    }
}

/**
 * 拿文字当图标：这个 App 还没有图标体系，硬留一个空图标位反而把标签挤歪，
 * 选中态交给 Material3 自带的指示器。
 */
/**
 * 切到某个 Tab。和 Tab 栏里那个点击逻辑必须一致 —— 两处都直接 navigate 的话，
 * 回退栈会越堆越深（同一个 Tab 点十次就是十条记录）。
 */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun RowScope.TabItem(
    route: String,
    label: String,
    icon: ImageVector,
    currentRoute: String?,
    navController: NavHostController,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = {
            // saveState/restoreState 让每个 Tab 各自记住自己的浏览位置，来回切不会越堆越深
            if (currentRoute != route) navController.switchTab(route)
        },
        // 只放图标不放文字（用户要求）；label 仍保留给无障碍朗读
        icon = {
            // 收藏那个按主流做法：未选中空心、选中实心
            val shown = if (label == "收藏" && currentRoute == route) Icons.Filled.Favorite else icon
            Icon(imageVector = shown, contentDescription = label)
        },
        colors = NavigationBarItemDefaults.colors(
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            selectedIconColor = MaterialTheme.colorScheme.onSurface,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    startRoute: String,
    modifier: Modifier,
    container: AppContainer,
) {
    // 401/429 之后唯一的出口（以及设置页的「退出登录」）：回数据源页重填密码。
    // 用 graph.id 弹整个图 —— 底部 Tab 切换会把根页面从回退栈里弹掉（saveState），
    // 所以不能指望 popUpTo(BROWSE) 能清干净。
    val relogin: () -> Unit = {
        container.library.reset() // 仓库是应用作用域的，封锁不会自己消失，得显式解开
        navController.navigate(Routes.ADD_SOURCE) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    val openDetail: (List<String>) -> Unit = { paths ->
        container.detailSelection.set(paths)
        navController.navigate(Routes.DETAIL)
    }

    NavHost(
        navController = navController,
        startDestination = startRoute,
        modifier = modifier,
        // 页面切换：淡入 + 一点点横向位移。幅度很小（1/20 屏宽），只为不硬切。
        enterTransition = {
            fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 20 }
        },
        exitTransition = { fadeOut(tween(140)) },
        popEnterTransition = { fadeIn(tween(200)) },
        popExitTransition = {
            fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { it / 20 }
        },
    ) {
        composable(Routes.ADD_SOURCE) {
            val vm: SourceViewModel = viewModel(factory = SourceViewModel.factory(container))
            AddSourceScreen(
                vm = vm,
                onConnected = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LIBRARY) {
            val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
            LibraryScreen(
                vm = vm,
                onOpenDetail = openDetail,
                onAddSource = { navController.navigate(Routes.ADD_SOURCE) },
                onRelogin = relogin,
            )
        }

        composable(Routes.FAVORITES) {
            val vm: FavoritesViewModel = viewModel(factory = FavoritesViewModel.factory(container))
            FavoritesScreen(vm = vm, onOpenDetail = openDetail)
        }

        composable(Routes.BROWSE) {
            val vm: BrowserViewModel = viewModel(factory = BrowserViewModel.factory(container))
            BrowserScreen(
                vm = vm,
                onPlay = { path, queue ->
                    container.playbackSelection.set(path, queue)
                    navController.navigate(Routes.PLAYER)
                },
                onRelogin = relogin,
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(
                vm = vm,
                onLoggedOut = relogin,
                onOpenLogs = { navController.navigate(Routes.LOGS) },
            )
        }

        composable(Routes.LOGS) {
            LogScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.MINE) {
            MineScreen(
                container = container,
                onOpenFavorites = { navController.switchTab(Routes.FAVORITES) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenBrowse = { navController.navigate(Routes.BROWSE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenLogs = { navController.navigate(Routes.LOGS) },
            )
        }

        composable(Routes.SEARCH) {
            SearchScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenDetail = openDetail,
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onPlay = { path, queue ->
                    container.playbackSelection.set(path, queue)
                    navController.navigate(Routes.PLAYER)
                },
            )
        }

        composable(Routes.DETAIL) {
            val vm: DetailViewModel = viewModel(factory = DetailViewModel.factory(container))
            DetailScreen(
                vm = vm,
                onPlay = { path, queue ->
                    container.playbackSelection.set(path, queue)
                    navController.navigate(Routes.PLAYER)
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PLAYER) {
            val vm: PlayerViewModel = viewModel(factory = PlayerViewModel.factory(container))
            PlayerScreen(vm)
        }
    }
}
