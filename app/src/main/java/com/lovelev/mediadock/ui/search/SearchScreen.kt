package com.lovelev.mediadock.ui.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.ui.library.LibraryViewModel
import com.lovelev.mediadock.ui.library.WallEntry
import com.lovelev.mediadock.ui.library.WallSort
import com.lovelev.mediadock.ui.library.filterLibraryItems
import com.lovelev.mediadock.ui.library.groupWall
import com.lovelev.mediadock.ui.library.paths
import com.lovelev.mediadock.ui.library.sortWall

/**
 * 搜索页：顶部搜索框 + 分类/排序/类型/地区/年份筛选 + 三列海报网格。
 *
 * 过滤逻辑复用 [filterLibraryItems]（关键词）与 [groupWall]（剧集聚合），
 * 本页负责筛选状态与排版。
 */
@Composable
fun SearchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenDetail: (paths: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val query = vm.query
    val allItems by vm.allLibraryItems.collectAsState()
    val details by vm.allLibraryDetails.collectAsState()

    var type by remember { mutableStateOf("全部") }
    var sort by remember { mutableStateOf("最新更新") }
    var genre by remember { mutableStateOf("全部") }
    var region by remember { mutableStateOf("全部") }
    var year by remember { mutableStateOf("全部") }

    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val byPath = remember(details) { details.associateBy { it.path } }

    val genreOptions = remember(details) {
        val counts = details
            .flatMap { it.genres.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()
        listOf("全部") + counts.entries.sortedByDescending { it.value }.map { it.key }.take(10)
    }
    val regionOptions = remember(details) {
        listOf("全部") + details.mapNotNull { it.country?.trim()?.takeIf { c -> c.isNotEmpty() } }
            .distinct().take(8)
    }
    val yearOptions = remember(details, allItems) {
        val years = buildSet {
            details.mapNotNull { it.releaseDate?.take(4)?.toIntOrNull() }.forEach(::add)
            allItems.mapNotNull { it.year }.forEach(::add)
        }.sortedDescending()
        (listOf("全部", "2020年代", "2010年代") +
            years.take(8).map { it.toString() }).distinct()
    }

    val entries = remember(allItems, details, query, type, sort, genre, region, year) {
        val keywordFiltered = if (query.isBlank()) {
            allItems
        } else {
            filterLibraryItems(allItems, details, query)
        }
        val filtered = keywordFiltered.filter { item ->
            val detail = byPath[item.path]
            val genres = detail?.genres?.split(',')?.map { it.trim() }.orEmpty()
            val release = detail?.releaseDate.orEmpty()
            val genreOk = genre == "全部" || genre in genres
            val regionOk = region == "全部" || detail?.country == region
            val yearOk = when {
                year == "全部" -> true
                year.endsWith("年代") -> {
                    val decade = year.removeSuffix("年代").toIntOrNull() ?: return@filter false
                    val y = release.take(4).toIntOrNull() ?: item.year ?: return@filter false
                    y in decade until decade + 10
                }
                else -> release.startsWith(year) || item.year?.toString() == year
            }
            genreOk && regionOk && yearOk
        }
        val wall = groupWall(filtered).filter { entry ->
            when (type) {
                "电影" -> entry is WallEntry.Movie
                "电视剧" -> entry is WallEntry.Series
                "动漫" -> byPath[entry.sample.path]?.genres?.contains("动画") == true
                "综艺" -> byPath[entry.sample.path]?.genres
                    ?.let { it.contains("综艺") || it.contains("真人秀") } == true
                else -> true
            }
        }
        val mode = when (sort) {
            "影片评分" -> WallSort.RATING
            "最新上映" -> WallSort.YEAR
            else -> WallSort.RECENT
        }
        sortWall(wall, mode)
    }

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
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ).let { stroke ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = stroke,
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
                            value = query,
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
                                    if (query.isEmpty()) {
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
                        if (query.isNotEmpty()) {
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
        }

        FilterRow(options = listOf("全部", "电视剧", "电影", "动漫", "综艺"), selected = type) { type = it }
        FilterRow(
            options = listOf("最新更新", "最新上映", "影片评分"),
            selected = sort,
        ) { sort = it }
        FilterRow(label = "类型", options = genreOptions, selected = genre) { genre = it }
        FilterRow(label = "地区", options = regionOptions, selected = region) { region = it }
        FilterRow(label = "年份", options = yearOptions, selected = year) { year = it }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("无结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(entries, key = { it.key }) { entry ->
                    SearchCard(
                        entry = entry,
                        detail = byPath[entry.sample.path],
                        onClick = { onOpenDetail(entry.paths()) },
                    )
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
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
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
            Surface(
                onClick = { onSelect(option) },
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else null,
                    color = if (active) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchCard(
    entry: WallEntry,
    detail: TitleDetail?,
    onClick: () -> Unit,
) {
    val sample: MediaItemEntity = entry.sample
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            sample.posterPath?.let { poster ->
                AsyncImage(
                    model = TmdbClient.posterUrl(poster),
                    contentDescription = sample.parsedTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val typeLabel = when {
                entry is WallEntry.Series -> "剧集"
                detail?.genres?.contains("动画") == true -> "动漫"
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
            sample.rating?.let { rating ->
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
            text = sample.parsedTitle.ifBlank { sample.name },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail?.releaseDate?.takeIf { it.isNotBlank() }
                ?: sample.year?.toString().orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
