package com.lovelev.mediadock.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.lovelev.mediadock.core.data.WorkCard
import com.lovelev.mediadock.data.repo.DetailFillState
import com.lovelev.mediadock.data.repo.ScanState
import com.lovelev.mediadock.data.repo.ScrapeState

/**
 * 首页（底部的「媒体库」Tab，也是 App 的起始页）。
 *
 * 海报墙由 **works 分页**驱动（Paging 3），随机推荐与分类行只取有界样本；
 * 不再把全库条目读进内存分组。整行内容跨满列，海报走网格。
 */
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenDetail: (paths: List<String>) -> Unit,
    onAddSource: () -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cards = vm.wall.collectAsLazyPagingItems()
    val scan by vm.scan.collectAsState()
    val scrape by vm.scrape.collectAsState()
    val fill by vm.fill.collectAsState()
    val genreSections by vm.genreSections.collectAsState()
    val recommended = vm.recommended
    val recommendReasons = vm.recommendReasons

    // 「进首页换一批」：Tab 切回来会重新进组合，这里就跟着换
    LaunchedEffect(Unit) { vm.reshuffle() }
    // 扫描完成后刷新推荐/分类行（两者都只查有界样本）
    LaunchedEffect(scan) { if (scan is ScanState.Done) vm.refreshHeader() }

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
        fillState is DetailFillState.Done -> when {
            fillState.filled == 0 && fillState.failed == 0 -> null
            fillState.failed == 0 -> "已补齐 ${fillState.filled} 条影片信息"
            else -> "补齐 ${fillState.filled} 条 · ${fillState.failed} 条失败"
        }
        else -> null
    }

    Column(modifier.fillMaxSize()) {
        if (runningLabel != null) {
            Spacer(Modifier.height(8.dp))
            RunningStrip(runningLabel)
            Spacer(Modifier.height(4.dp))
            HorizontalDivider()
        }

        val refreshing = cards.loadState.refresh is LoadState.Loading
        val empty = cards.itemCount == 0 && cards.loadState.refresh is LoadState.NotLoading

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

            empty -> WelcomeHero(
                scanning = scanState is ScanState.Running,
                onScan = vm::toggleScan,
                onAddSource = onAddSource,
            )

            refreshing && cards.itemCount == 0 -> LibrarySkeleton()

            cards.loadState.refresh is LoadState.Error && cards.itemCount == 0 -> Center {
                TextButton(onClick = { cards.retry() }) { Text("加载失败，点击重试") }
            }

            else -> HomeFeed(
                cards = cards,
                recommended = recommended,
                recommendReasons = recommendReasons,
                genreSections = genreSections,
                errorNotice = errorNotice,
                hintNotice = hintNotice,
                onOpenDetail = onOpenDetail,
                onShuffle = vm::reshuffle,
            )
        }
    }
}

@Composable
private fun HomeFeed(
    cards: LazyPagingItems<WorkCard>,
    recommended: List<WorkCard>,
    recommendReasons: Map<String, String>,
    genreSections: List<GenreCardRow>,
    errorNotice: String?,
    hintNotice: String?,
    onOpenDetail: (List<String>) -> Unit,
    onShuffle: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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

        if (recommended.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "row-random") {
                WorkFeaturedRow(
                    cards = recommended,
                    reasons = recommendReasons,
                    onOpenDetail = onOpenDetail,
                    onAction = onShuffle,
                )
            }
        }
        genreSections.forEach { row ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "row-${row.genre}") {
                WorkSectionRow(
                    title = row.genre,
                    cards = row.cards,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }, key = "row-all") { SectionTitle("全部") }

        items(count = cards.itemCount, key = cards.itemKey { it.id }) { index ->
            val card = cards[index] ?: return@items
            WorkPosterCard(
                card = card,
                onClick = { onOpenDetail(card.paths) },
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
        }

        val append = cards.loadState.append
        when (append) {
            is LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }, key = "paging-loading") {
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.3f))
                }
            }
            is LoadState.Error, is LoadState.NotLoading -> {
                if (append is LoadState.Error) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "paging-retry") {
                        TextButton(onClick = { cards.retry() }) { Text("加载失败，点击重试") }
                    }
                }
            }
        }
    }
}

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
            text = "媒体库为空",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
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
