package com.lovelev.mediadock.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.lovelev.mediadock.core.data.WorkCard
import com.lovelev.mediadock.data.tmdb.TmdbClient

/** 分类行的一块（works 版本）。 */
data class GenreCardRow(val genre: String, val cards: List<WorkCard>)

/** 有界分类行：按出现次数取前 [maxGenres] 个类型，每行最多 [perRow] 部。 */
fun genreCardRows(
    cards: List<WorkCard>,
    maxGenres: Int = 6,
    perRow: Int = 20,
): List<GenreCardRow> {
    val byGenre = LinkedHashMap<String, MutableList<WorkCard>>()
    cards.forEach { card ->
        card.genres.forEach { genre ->
            byGenre.getOrPut(genre) { mutableListOf() }.add(card)
        }
    }
    return byGenre.entries
        .sortedByDescending { it.value.size }
        .take(maxGenres)
        .map { GenreCardRow(it.key, it.value.take(perRow)) }
}

@Composable
fun WorkFeaturedRow(
    cards: List<WorkCard>,
    reasons: Map<String, String>,
    onOpenDetail: (List<String>) -> Unit,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    val pagerState = rememberPagerState { cards.size }
    Column(modifier = modifier.fillMaxWidth()) {
        WorkRowHeader("随机推荐", null, onAction, "换一批")
        Spacer(Modifier.height(8.dp))
        HorizontalPager(state = pagerState, pageSpacing = 10.dp) { page ->
            val card = cards[page]
            WorkFeaturedCard(
                card = card,
                reason = reasons[card.id].orEmpty(),
                onClick = { onOpenDetail(card.paths) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (cards.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(cards.size) { index ->
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

@Composable
private fun WorkFeaturedCard(
    card: WorkCard,
    reason: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backdrop = card.backdropPath
    val art = backdrop ?: card.posterPath
    val title = card.title
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
            Box(
                modifier = Modifier.fillMaxSize().background(
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
                    card.rating?.let { add(String.format(java.util.Locale.US, "★%.1f", it)) }
                    card.year?.let { add(it.toString()) }
                    if (card.episodeCount > 1) add("共 ${card.episodeCount} 集")
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
            card.progressFraction.takeIf { it > 0f }?.let {
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

@Composable
fun WorkSectionRow(
    title: String,
    cards: List<WorkCard>,
    onOpenDetail: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    if (cards.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        WorkRowHeader(title, count, null, "")
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cards, key = { it.id }) { card ->
                WorkPosterCard(
                    card = card,
                    onClick = { onOpenDetail(card.paths) },
                    modifier = Modifier.width(SECTION_CARD_WIDTH).aspectRatio(2f / 3f),
                )
            }
        }
    }
}

@Composable
private fun WorkRowHeader(
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
            TextButton(
                onClick = it,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) { Text(actionLabel) }
        }
    }
}

/** 海报墙的一张（works 版本）。 */
@Composable
internal fun WorkPosterCard(
    card: WorkCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val poster = card.posterPath
            if (poster != null) {
                AsyncImage(
                    model = TmdbClient.posterModel(poster),
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (card.episodeCount > 1) {
                Surface(
                    color = Color.Black.copy(alpha = 0.66f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                ) {
                    Text(
                        text = "${card.episodeCount} 集",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            card.progressFraction.takeIf { it > 0f }?.let {
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
