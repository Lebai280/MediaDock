package com.lovelev.mediadock.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovelev.mediadock.core.model.MediaTypes
import com.lovelev.mediadock.ui.formatSize
import com.lovelev.mediadock.data.webdav.WebDavEntry

@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    onPlay: (path: String, queue: List<String>) -> Unit,
    onRelogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 至少不在根目录时，系统返回键用于回上一级
    BackHandler(enabled = vm.canGoUp) { vm.goUp() }

    Column(modifier.fillMaxSize()) {
        Header(vm)
        HorizontalDivider()

        when (val s = vm.state) {
            is BrowseState.Loading -> Center { CircularProgressIndicator() }

            is BrowseState.Ready ->
                if (s.entries.isEmpty()) {
                    Center {
                        Text("空目录", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    EntryList(s.entries, onOpen = vm::open, onPlay = onPlay, qualify = vm::qualify)
                }

            is BrowseState.Failed -> Center { Notice(s.message, "重试", vm::retry) }

            is BrowseState.Blocked -> Center { Notice(s.message, "重新登录", onRelogin) }
        }
    }
}

/** 面包屑 + 刷新。媒体库走底部 Tab，不在这里。 */
@Composable
private fun Header(vm: BrowserViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (vm.sources.size > 1) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) {
                        Text("源：${vm.rootLabel}", maxLines = 1)
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        vm.sources.forEach { source ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (source.id == vm.selectedSourceId) {
                                            "● ${source.name}"
                                        } else {
                                            source.name
                                        },
                                    )
                                },
                                onClick = {
                                    expanded = false
                                    vm.selectSource(source.id)
                                },
                            )
                        }
                    }
                }
            }
            val crumbs = vm.crumbs()
            crumbs.forEachIndexed { i, c ->
                if (i > 0) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
                val current = i == crumbs.lastIndex
                Text(
                    text = c.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    modifier = Modifier
                        .clickable(enabled = !current) { vm.open(c.path) }
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                )
            }
        }

        TextButton(
            onClick = vm::refresh,
            enabled = vm.state is BrowseState.Ready,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.tertiary,
            ),
        ) {
            Text("刷新")
        }
    }
}

@Composable
private fun EntryList(
    entries: List<WebDavEntry>,
    onOpen: (String) -> Unit,
    onPlay: (path: String, queue: List<String>) -> Unit,
    qualify: (String) -> String,
) {
    // 同目录的视频就是播放队列 —— 有了它才有「自动播放下一集」；路径按源限定
    val videos = entries
        .filter { !it.isDirectory && MediaTypes.isVideoFile(it.name) }
        .map { qualify(it.path) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(entries, key = { it.path }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (entry.isDirectory) {
                            onOpen(entry.path)
                        } else {
                            onPlay(qualify(entry.path), videos)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (entry.isDirectory) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val size = formatSize(entry.size)
                    if (size.isNotEmpty()) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
