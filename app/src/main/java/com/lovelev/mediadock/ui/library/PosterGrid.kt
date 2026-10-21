package com.lovelev.mediadock.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.ui.common.pressScale
import com.lovelev.mediadock.ui.common.rememberHapticTick

/** 横滑行里一块海报的宽度。 */
internal val SECTION_CARD_WIDTH = 104.dp

/** 小节标题与它下面那排海报之间的间距。 */
private val ROW_TITLE_GAP = 8.dp

/** 每行底部额外留的空隙 —— 让「分类」和「影片」之间松一点（用户要求）。 */
private val ROW_BOTTOM_GAP = 12.dp

/**
 * 首页「随机推荐」：**一次只显示一张大卡片**（横滑翻页 + 下面的页码点），卡片上带**推荐理由**。
 */
@Composable
fun FeaturedRow(
    entries: List<WallEntry>,
    reasons: Map<String, String>,
    progress: Map<String, Float>,
    onOpenDetail: (paths: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "换一批",
) {
    if (entries.isEmpty()) return
    val pagerState = rememberPagerState { entries.size }

    Column(modifier = modifier.fillMaxWidth()) {
        RowHeader(title = "随机推荐", count = null, onAction = onAction, actionLabel = actionLabel)
        Spacer(Modifier.height(ROW_TITLE_GAP))

        HorizontalPager(state = pagerState, pageSpacing = 10.dp) { page ->
            val entry = entries[page]
            FeaturedCard(
                entry = entry,
                reason = reasons[entry.sample.path].orEmpty(),
                fraction = wallFraction(entry, progress),
                onClick = { onOpenDetail(entry.paths()) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (entries.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(entries.size) { index ->
                    val current = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (current) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (current) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }
        }
    }
}

/** 大卡片：整张宽度、16:9，图压在底部渐变里，标题 + 元信息 + **推荐理由** 叠在上面。 */
@Composable
private fun FeaturedCard(
    entry: WallEntry,
    reason: String,
    fraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sample = entry.sample
    // 大卡片用横版背景图更合适；没有就退回海报 —— 竖版海报用 Fit，避免被 16:9 的卡片裁掉
    val backdrop = sample.backdropPath
    val art = backdrop ?: sample.posterPath
    val title = sample.parsedTitle.ifBlank { sample.name }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.aspectRatio(16f / 9f).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (art != null) {
                AsyncImage(
                    model = TmdbClient.posterModel(art, width = 780),
                    contentDescription = title,
                    contentScale = if (backdrop != null) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // 压暗下半部，白字才读得清
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.5f to Color.Black.copy(alpha = 0.28f),
                            1f to Color.Black.copy(alpha = 0.80f),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val meta = buildList {
                    sample.rating?.let { add(String.format(java.util.Locale.US, "★%.1f", it)) }
                    sample.year?.let { add(it.toString()) }
                    if (entry is WallEntry.Series) add("共 ${entry.episodes.size} 集")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                    )
                }

                if (reason.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        shape = CircleShape,
                    ) {
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            fraction?.takeIf { it > 0f }?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(it)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * 一条分类行：**标题靠左** + 真实条数 + 一排海报。
 */
@Composable
fun SectionRow(
    title: String,
    entries: List<WallEntry>,
    progress: Map<String, Float>,
    onOpenDetail: (paths: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth().padding(bottom = ROW_BOTTOM_GAP)) {
        RowHeader(title = title, count = count, onAction = null, actionLabel = "")
        Spacer(Modifier.height(ROW_TITLE_GAP))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.key }) { entry ->
                PosterCard(
                    entry = entry,
                    fraction = wallFraction(entry, progress),
                    onClick = { onOpenDetail(entry.paths()) },
                    modifier = Modifier.width(SECTION_CARD_WIDTH).aspectRatio(2f / 3f),
                )
            }
        }
    }
}

/** 小节标题行：标题靠左（用户要求），条数和右侧动作都是可选的。 */
@Composable
private fun RowHeader(
    title: String,
    count: Int?,
    onAction: (() -> Unit)?,
    actionLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        count?.let {
            Text(
                text = " · $it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        onAction?.let {
            // TextButton 默认拿 primary 当文字色 —— 亮柠檬绿在浅底上看不清，走 tertiary
            TextButton(
                onClick = it,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) { Text(actionLabel) }
        }
    }
}

/**
 * 海报墙（收藏页用；首页的「全部」是内联在 HomeFeed 的网格里的，不走这里）。
 *
 * @param progress 单片看观看位置；剧看「已看完几集 / 共几集」。
 */
@Composable
fun PosterGrid(
    items: List<MediaItemEntity>,
    progress: Map<String, Float>,
    onOpenDetail: (paths: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 分季分集是纯函数，另有单测（WallEntryTest）
    val wall = remember(items) { groupWall(items) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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

/** 一张海报块。网格和横滑行共用，只是尺寸不同。 */
@Composable
internal fun PosterCard(
    entry: WallEntry,
    fraction: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sample = entry.sample
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberHapticTick()
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = { tick(); onClick() },
            ),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val poster = sample.posterPath
            if (poster != null) {
                AsyncImage(
                    model = TmdbClient.posterModel(poster),
                    contentDescription = sample.parsedTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // 还没匹配上：拿解析出来的片名占位，比整串文件名强
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = sample.parsedTitle.ifBlank { sample.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (entry is WallEntry.Series) {
                Surface(
                    color = Color.Black.copy(alpha = 0.66f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Text(
                        text = "${entry.episodes.size} 集",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }

            fraction?.takeIf { it > 0f }?.let {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(it)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * 这一块要显示多满。剧取「已看完集数 / 总集数」——
 * 取最大进度的话，看完第 1 集就显示满格、看着像整部看完了，反而误导。
 */
internal fun wallFraction(entry: WallEntry, progress: Map<String, Float>): Float? = when (entry) {
    is WallEntry.Movie -> progress[entry.sample.path]
    is WallEntry.Series -> entry.episodes
        .count { (progress[it.path] ?: 0f) >= WATCHED_THRESHOLD }
        .let { if (it == 0) 0f else it.toFloat() / entry.episodes.size }
}

/** 点进详情页要给的那一批条目。 */
fun WallEntry.paths(): List<String> = when (this) {
    is WallEntry.Movie -> listOf(sample.path)
    is WallEntry.Series -> episodes.map { it.path }
}

/** 一集看到这个比例就算「看完」了（片尾曲/字幕那段不用看完）。 */
private const val WATCHED_THRESHOLD = 0.92f
