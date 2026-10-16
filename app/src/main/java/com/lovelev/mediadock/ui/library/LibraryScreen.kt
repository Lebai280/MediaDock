package com.lovelev.mediadock.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.repo.DetailFillState
import com.lovelev.mediadock.data.repo.ScanState
import com.lovelev.mediadock.data.repo.ScrapeState

/**
 * 首页（底部的「媒体库」Tab，也是 App 的起始页）。
 *
 * 从上到下：**吸顶栏**（药丸搜索框 + ⋯，滚动时只有它留在顶部）→ 任务进度细线 → 滚动内容。
 * 滚动内容是一个 `LazyVerticalGrid`，整行的东西（随机推荐 / 分类行 / 「全部」标题 / 提示行）
 * 用 `item(span = { GridItemSpan(maxLineSpan) })` 跨满，海报条目走默认列宽。
 *
 * 吸顶栏样式照用户站点「星屿」的 `.search`：全圆角药丸、左侧放大镜、右侧圆形「×」、
 * 聚焦时描边变强调色（柠檬绿）—— 和站点同一套手感。
 */
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenDetail: (paths: List<String>) -> Unit,
    onAddSource: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items by vm.visible.collectAsState()
    val scan by vm.scan.collectAsState()
    val scrape by vm.scrape.collectAsState()
    val fill by vm.fill.collectAsState()
    val progress by vm.progress.collectAsState()
    val genreSections by vm.genreSections.collectAsState()
    val recommendReasons by vm.recommendReasons.collectAsState()
    val searching = vm.query.isNotBlank()

    // 「进首页换一批」：Tab 切回来会重新进组合，这里就跟着换
    LaunchedEffect(Unit) { vm.reshuffle() }

    // 读进局部 val 才做得了智能转换（`by` 委托属性不行）
    val scanState = scan
    val scrapeState = scrape
    val fillState = fill

    val blocked = (scanState as? ScanState.Failed)?.blocked == true

    val runningLabel: String? = when {
        scanState is ScanState.Running ->
            "已扫 ${scanState.directories} 个目录 · ${scanState.files} 个视频"
        scrapeState is ScrapeState.Running -> "刮削 ${scrapeState.done}/${scrapeState.total}"
        fillState is DetailFillState.Running -> "补齐 ${fillState.done}/${fillState.total}"
        else -> null
    }

    // 失败/告警只在吸顶栏下面挂一行 —— 库里已有的内容不该被一次失败抹掉
    val errorNotice: String? = when {
        scanState is ScanState.Failed && !blocked -> scanState.message
        scrapeState is ScrapeState.Failed -> scrapeState.message
        fillState is DetailFillState.Failed -> fillState.message
        else -> null
    }
    val hintNotice: String? = when {
        scanState is ScanState.Done && scanState.truncated -> "达到扫描上限，结果可能不完整"
        scrapeState is ScrapeState.Done && scrapeState.failed > 0 ->
            "${scrapeState.matched} 条匹配上 · ${scrapeState.failed} 条没匹配上"
        // 成功数和失败数分开报 —— 别把「处理过的总数」当成功数（网络不通时每条都会失败）
        fillState is DetailFillState.Done -> when {
            fillState.filled == 0 && fillState.failed == 0 -> null
            fillState.failed == 0 -> "已补齐 ${fillState.filled} 条影片信息"
            else -> "补齐 ${fillState.filled} 条 · ${fillState.failed} 条失败"
        }
        else -> null
    }

    Column(modifier.fillMaxSize()) {
        StickyBar(
            query = vm.query,
            onQuery = vm::editQuery,
            scan = scanState,
            scrape = scrapeState,
            fill = fillState,
            onToggleScan = vm::toggleScan,
            onToggleScrape = vm::toggleScrape,
            onToggleFill = vm::toggleFillDetails,
        )
        runningLabel?.let { RunningStrip(it) }
        HorizontalDivider()

        when {
            blocked -> Center {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = scanState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onRelogin) { Text("重新登录") }
                }
            }

            items.isEmpty() && searching -> Center {
                Text(
                    text = "没找到匹配的片子",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items.isEmpty() -> WelcomeHero(
                scanning = scanState is ScanState.Running,
                onScan = vm::toggleScan,
                onAddSource = onAddSource,
            )

            else -> HomeFeed(
                items = items,
                recommended = vm.recommended,
                recommendReasons = recommendReasons,
                genreSections = genreSections,
                progress = progress,
                searching = searching,
                errorNotice = errorNotice,
                hintNotice = hintNotice,
                onOpenDetail = onOpenDetail,
                onShuffle = vm::reshuffle,
            )
        }
    }
}

/**
 * 滚动内容。整行（随机推荐 / 分类行 / 「全部」标题 / 提示行）跨满列，海报条目按列排。
 */
@Composable
private fun HomeFeed(
    items: List<MediaItemEntity>,
    recommended: List<WallEntry>,
    recommendReasons: Map<String, String>,
    genreSections: List<GenreRow>,
    progress: Map<String, Float>,
    searching: Boolean,
    errorNotice: String?,
    hintNotice: String?,
    onOpenDetail: (List<String>) -> Unit,
    onShuffle: () -> Unit,
) {
    val wall = remember(items) { groupWall(items) }
    // 分类行先归并成「剧 / 单片」的块 —— 在网格外面算好（别在 item 里重复 groupWall）
    val genreWalls = remember(genreSections) {
        genreSections.map { row -> row to groupWall(row.items) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // 「分类」和「影片」之间松一点（用户要求）：行内自己还留了 ROW_BOTTOM_GAP
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        errorNotice?.let { text ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "notice-error") {
                Notice(text, isError = true)
            }
        }
        hintNotice?.let { text ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "notice-hint") {
                Notice(text, isError = false)
            }
        }

        // 搜索时只留过滤后的网格 —— 分类行与随机推荐都收起来
        if (!searching) {
            if (recommended.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "row-random") {
                    FeaturedRow(
                        entries = recommended,
                        reasons = recommendReasons,
                        progress = progress,
                        onOpenDetail = onOpenDetail,
                        onAction = onShuffle,
                    )
                }
            }
            genreWalls.forEach { (row, entries) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "row-${row.genre}") {
                    SectionRow(
                        title = row.genre,
                        count = row.total,
                        entries = entries,
                        progress = progress,
                        onOpenDetail = onOpenDetail,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "row-all") { SectionTitle("全部") }
        }

        items(wall, key = { it.key }) { entry ->
            PosterCard(
                entry = entry,
                fraction = wallFraction(entry, progress),
                onClick = { onOpenDetail(entry.paths()) },
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
        }
    }
}

/** 靠左的小节标题（用户要求的「分类标题靠左」）。 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Notice(text: String, isError: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/**
 * 吸顶栏：**药丸搜索框 + ⋯**。样式照用户站点「星屿」的 `.search`：
 * 全圆角、左侧放大镜（16dp）、右侧有内容时一个圆形 ×、聚焦时描边变强调色。
 */
@Composable
private fun StickyBar(
    query: String,
    onQuery: (String) -> Unit,
    scan: ScanState,
    scrape: ScrapeState,
    fill: DetailFillState,
    onToggleScan: () -> Unit,
    onToggleScrape: () -> Unit,
    onToggleFill: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(
                width = 1.dp,
                color = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            modifier = Modifier.weight(1f).height(40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp).size(16.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "搜索片名 / 类型 / 演员",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .onFocusChanged { focused = it.isFocused },
                )
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .clickable { onQuery("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }

        // ⋯：扫描 / 刮削 / 补齐都收在这里（照站点 .btn-icon 的圆形按钮）
        Box {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "更多操作",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                // 三个后台动作互斥：扫描会整表替换媒体条目，刮削和补齐都要打 TMDB。
                val idle = scan !is ScanState.Running &&
                    scrape !is ScrapeState.Running &&
                    fill !is DetailFillState.Running
                DropdownMenuItem(
                    text = { Text(if (scan is ScanState.Running) "取消扫描" else "扫描") },
                    enabled = scan is ScanState.Running || idle,
                    onClick = { menuOpen = false; onToggleScan() },
                )
                DropdownMenuItem(
                    text = { Text(if (scrape is ScrapeState.Running) "取消刮削" else "刮削") },
                    enabled = scrape is ScrapeState.Running || idle,
                    onClick = { menuOpen = false; onToggleScrape() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            if (fill is DetailFillState.Running) {
                                "取消补齐（${fill.done}/${fill.total}）"
                            } else {
                                "补齐影片信息"
                            },
                        )
                    },
                    enabled = fill is DetailFillState.Running || idle,
                    onClick = { menuOpen = false; onToggleFill() },
                )
            }
        }
    }
}

/** 任务运行时吸顶栏下面那条细线 + 一行小字（不占位、也不跟着内容滚走）。 */
@Composable
private fun RunningStrip(label: String) {
    Column {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 14.dp, top = 3.dp, bottom = 1.dp),
        )
    }
}

/** 库为空时的欢迎页（照用户给的 VidHub 参考做的）。 */
@Composable
private fun WelcomeHero(
    scanning: Boolean,
    onScan: () -> Unit,
    onAddSource: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "欢迎来到 MediaDock",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "媒体库为空",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = onScan,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (scanning) "扫描中…" else "扫描媒体库")
        }
        Spacer(Modifier.height(12.dp))
        // 这个 TextButton 也要显式给色：默认 contentColor 是 primary（亮柠檬绿），浅底上看不清
        TextButton(
            onClick = onAddSource,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.tertiary,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("添加数据源")
        }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
