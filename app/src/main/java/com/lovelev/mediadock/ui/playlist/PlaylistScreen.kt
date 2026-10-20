package com.lovelev.mediadock.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.di.AppContainer
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 片单列表：新建、打开、删除。 */
@Composable
fun PlaylistsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val playlists by container.playlists.playlists().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "片单",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新建片单")
            }
        }
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无片单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(playlist.id) }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { pendingDelete = playlist.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除片单")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建片单") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        creating = false
                        val n = name.trim()
                        name = ""
                        if (n.isNotEmpty()) {
                            scope.launch { onOpen(container.playlists.create(n)) }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("取消") } },
        )
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除片单") },
            text = { Text("删除片单，媒体文件不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { container.playlists.delete(id) }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } },
        )
    }
}

/** 片单内容：条目列表，点击播放；剧集自动展开为整季分集。 */
@Composable
fun PlaylistDetailScreen(
    container: AppContainer,
    id: Long,
    onBack: () -> Unit,
    onPlay: (path: String, queue: List<String>) -> Unit,
) {
    val playlists by container.playlists.playlists().collectAsState(initial = emptyList())
    val playlist = playlists.firstOrNull { it.id == id }
    val rows by container.playlists.items(id).collectAsState(initial = emptyList())
    // 只查片单里这些 path，避免为了海报把整个媒体库读进内存
    val paths = remember(rows) { rows.map { it.path } }
    val items by remember(paths) {
        container.library.observeByPaths(paths)
    }.collectAsState(initial = emptyList())
    val byPath = remember(items) { items.associateBy { it.path } }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var sort by remember { mutableStateOf(PlaylistSort.DEFAULT) }
    var sortOpen by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    val sortedRows = remember(rows, byPath, sort) {
        when (sort) {
            PlaylistSort.DEFAULT -> rows
            PlaylistSort.TITLE -> rows.sortedBy {
                byPath[it.path]?.parsedTitle?.lowercase() ?: it.path.lowercase()
            }
            PlaylistSort.YEAR -> rows.sortedByDescending { byPath[it.path]?.year ?: 0 }
        }
    }

    fun play(path: String) {
        hint = null
        scope.launch {
            onPlay(path, container.playlists.playQueue(path))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = playlist?.name ?: "片单",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                }
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    PlaylistSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == sort) "● ${option.label}" else option.label) },
                            onClick = { sort = option; sortOpen = false },
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    sortedRows.firstOrNull()?.let { play(it.path) }
                },
                enabled = rows.isNotEmpty(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
            }
            IconButton(
                onClick = {
                    val text = buildString {
                        appendLine("片单：${playlist?.name ?: "片单"}")
                        sortedRows.forEachIndexed { index, row ->
                            val item = byPath[row.path]
                            val title = item?.parsedTitle?.takeIf { it.isNotBlank() }
                                ?: row.path.substringAfterLast('/')
                            val year = item?.year?.let { " ($it)" }.orEmpty()
                            appendLine("${index + 1}. $title$year — ${row.path}")
                        }
                    }
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    manager.setPrimaryClip(ClipData.newPlainText("MediaDock 片单", text))
                    hint = "片单已复制"
                },
                enabled = rows.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Share, contentDescription = "分享片单")
            }
        }
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("片单为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(sortedRows, key = { it.path }) { row ->
                    val item = byPath[row.path]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { play(row.path) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val poster = item?.posterPath
                        if (poster != null) {
                            AsyncImage(
                                model = TmdbClient.posterModel(poster, width = 92),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(40.dp)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item?.parsedTitle?.takeIf { it.isNotBlank() }
                                    ?: row.path.substringAfterLast('/'),
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            item?.let {
                                Text(
                                    text = listOfNotNull(it.year?.toString(), it.name)
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { scope.launch { container.playlists.remove(id, row.path) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "移出片单")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private enum class PlaylistSort(val label: String) {
    DEFAULT("默认顺序"),
    TITLE("按名称"),
    YEAR("按年份"),
}
