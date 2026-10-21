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
import androidx.compose.material.icons.filled.BookmarkBorder
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.lovelev.mediadock.ui.common.EmptyState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lovelev.mediadock.data.tmdb.TmdbClient
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** 片单列表：新建、打开、删除。 */
@Composable
fun PlaylistsScreen(
    vm: PlaylistsViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val state by vm.state.collectAsState()
    val playlists = state.playlists
    val scope = rememberCoroutineScope()

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
            IconButton(onClick = vm::startCreate) {
                Icon(Icons.Filled.Add, contentDescription = "新建片单")
            }
        }
        if (playlists.isEmpty()) {
            EmptyState(Icons.Filled.BookmarkBorder, "暂无片单")
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
                        IconButton(onClick = { vm.requestDelete(playlist.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除片单")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (state.creating) {
        AlertDialog(
            onDismissRequest = vm::cancelCreate,
            title = { Text("新建片单") },
            text = {
                OutlinedTextField(
                    value = state.draftName,
                    onValueChange = vm::updateDraft,
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { vm.create()?.let(onOpen) }
                    },
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = vm::cancelCreate) { Text("取消") } },
        )
    }

    state.pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("删除片单") },
            text = { Text("删除片单，媒体文件不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { vm.delete(id) }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text("取消") } },
        )
    }
}

/** 片单内容：条目列表，点击播放；剧集自动展开为整季分集。 */
@Composable
fun PlaylistDetailScreen(
    vm: PlaylistDetailViewModel,
    onBack: () -> Unit,
    onPlay: (path: String, queue: List<String>) -> Unit,
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun play(path: String) {
        vm.clearHint()
        scope.launch {
            onPlay(path, vm.playQueue(path))
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
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = vm::openSortMenu) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序")
                }
                DropdownMenu(expanded = vm.sortOpen, onDismissRequest = vm::closeSortMenu) {
                    PlaylistSort.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(if (option == state.sort) "● ${option.label}" else option.label) },
                            onClick = { vm.setSort(option) },
                        )
                    }
                }
            }
            IconButton(
                onClick = {
                    state.sortedRows.firstOrNull()?.let { play(it.path) }
                },
                enabled = state.rows.isNotEmpty(),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
            }
            IconButton(
                onClick = {
                    val text = vm.shareText()
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    manager.setPrimaryClip(ClipData.newPlainText("Mooring 片单", text))
                    vm.showHint("片单已复制")
                },
                enabled = state.rows.isNotEmpty(),
            ) {
                Icon(Icons.Filled.Share, contentDescription = "分享片单")
            }
        }
        vm.hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (state.rows.isEmpty()) {
            EmptyState(Icons.Filled.BookmarkBorder, "片单为空")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.sortedRows, key = { it.path }) { row ->
                    val item = state.byPath[row.path]
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
                        IconButton(onClick = { scope.launch { vm.remove(row.path) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "移出片单")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
