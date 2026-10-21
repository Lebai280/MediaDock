package com.lovelev.mediadock.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import com.lovelev.mediadock.ui.common.pressScale
import com.lovelev.mediadock.ui.common.rememberHapticTick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.lovelev.mediadock.core.data.WorkCard
import com.lovelev.mediadock.data.tmdb.TmdbClient

/**
 * 搜索页：FTS4 + SQL 过滤/排序 + Paging 3。
 * 顶部搜索框 + 分类/排序/类型/地区/年份筛选 + 三列海报网格。
 */
@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onBack: () -> Unit,
    onOpenDetail: (paths: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by vm.state.collectAsState()
    val options = vm.options
    val results = vm.results.collectAsLazyPagingItems()

    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            val borderColor by animateColorAsState(
                targetValue = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                animationSpec = tween(180),
                label = "searchBorder",
            )
            val fieldColor by animateColorAsState(
                targetValue = if (focused) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = tween(180),
                label = "searchField",
            )
            Surface(
                shape = CircleShape,
                color = fieldColor,
                border = BorderStroke(width = 1.dp, color = borderColor),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 14.dp).size(20.dp),
                    )
                    BasicTextField(
                        value = state.query,
                        onValueChange = vm::editQuery,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                            .focusRequester(focusRequester)
                            .onFocusChanged { focused = it.isFocused },
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (state.query.isEmpty()) {
                                    Text(
                                        text = "输入影片名称搜索",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.editQuery("") }, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }

        val refreshing = results.loadState.refresh is LoadState.Loading
        val empty = results.itemCount == 0 && results.loadState.refresh is LoadState.NotLoading

        val skeletonTransition = rememberInfiniteTransition(label = "searchSkeleton")
        val skeletonAlpha by skeletonTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.75f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "searchSkeletonAlpha",
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 筛选行跟着内容一起上滑：搜索框固定，标签把空间让给结果
            item(span = { GridItemSpan(maxLineSpan) }, key = "filters") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    FilterRow(
                        options = listOf("全部", "电视剧", "电影", "动漫", "综艺"),
                        selected = state.type,
                        onSelect = vm::setType,
                        contentPadding = PaddingValues(0.dp),
                    )
                    FilterRow(
                        options = listOf("最新更新", "最新上映", "影片评分"),
                        selected = state.sort,
                        onSelect = vm::setSort,
                        contentPadding = PaddingValues(0.dp),
                    )
                    FilterRow(
                        label = "类型",
                        options = options.genres,
                        selected = state.genre,
                        onSelect = vm::setGenre,
                        contentPadding = PaddingValues(0.dp),
                    )
                    FilterRow(
                        label = "地区",
                        options = options.regions,
                        selected = state.region,
                        onSelect = vm::setRegion,
                        contentPadding = PaddingValues(0.dp),
                    )
                    FilterRow(
                        label = "年份",
                        options = options.years,
                        selected = state.year,
                        onSelect = vm::setYear,
                        contentPadding = PaddingValues(0.dp),
                    )
                }
            }

            when {
                empty -> item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("无结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                refreshing && results.itemCount == 0 -> items(count = 9) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = skeletonAlpha),
                            ),
                    )
                }

                results.loadState.refresh is LoadState.Error && results.itemCount == 0 ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "error") {
                        Box(
                            Modifier.fillMaxWidth().height(220.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TextButton(onClick = { results.retry() }) { Text("加载失败，点击重试") }
                        }
                    }

                else -> {
                    items(
                        count = results.itemCount,
                        key = results.itemKey { it.id },
                    ) { index ->
                        val card = results[index] ?: return@items
                        SearchCard(card = card, onClick = { onOpenDetail(card.paths) })
                    }
                    if (results.loadState.append is LoadState.Loading) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.3f))
                            }
                        }
                    }
                    if (results.loadState.append is LoadState.Error) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TextButton(onClick = { results.retry() }) { Text("加载失败，点击重试") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    options: List<String>,
    selected: String,
    label: String? = null,
    onSelect: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
) {
    LazyRow(
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (label != null) {
            item {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(options) { option ->
            val active = option == selected
            val tick = rememberHapticTick()
            val container by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = tween(180),
                label = "chipContainer",
            )
            val contentColor by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                animationSpec = tween(180),
                label = "chipContent",
            )
            val scale by animateFloatAsState(
                targetValue = if (active) 1f else 0.97f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
                label = "chipScale",
            )
            Surface(
                onClick = {
                    if (!active) {
                        tick()
                        onSelect(option)
                    }
                },
                shape = CircleShape,
                color = container,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else null,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchCard(card: WorkCard, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val tick = rememberHapticTick()
    Column(
        modifier = Modifier
            .pressScale(interaction)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                onClick = { tick(); onClick() },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            card.posterPath?.let { poster ->
                AsyncImage(
                    model = TmdbClient.posterModel(poster),
                    contentDescription = card.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val typeLabel = when {
                card.episodeCount > 1 -> "剧集"
                card.genres.contains("动画") -> "动漫"
                card.genres.any { it.contains("综艺") || it.contains("真人秀") } -> "综艺"
                else -> "电影"
            }
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            card.rating?.let { rating ->
                Text(
                    text = String.format(java.util.Locale.US, "%.1f", rating),
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), 4f),
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = card.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = card.releaseDate?.takeIf { it.isNotBlank() }
                ?: card.year?.toString().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
