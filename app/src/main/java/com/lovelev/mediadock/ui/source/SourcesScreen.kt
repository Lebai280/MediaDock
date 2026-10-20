package com.lovelev.mediadock.ui.source

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lovelev.mediadock.core.model.SourceType

/** 数据源管理：列出全部源，启用/停用、编辑、分享、删除（含影响条目数）。 */
@Composable
fun SourcesScreen(
    vm: SourcesViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "数据源",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "添加数据源")
            }
        }

        state.hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (state.sources.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                Text("暂无数据源", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.sources, key = { it.id }) { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.showDetail(source) }
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = listOfNotNull(
                                    typeLabel(source.type),
                                    source.url.substringAfter("://").substringBefore('/'),
                                    if (source.credentialOk) null else "凭据损坏",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = source.enabled,
                            onCheckedChange = { enabled ->
                                vm.setEnabled(source.id, enabled)
                            },
                        )
                        IconButton(onClick = { vm.requestDelete(source) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除数据源")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    state.detail?.let { source ->
        AlertDialog(
            onDismissRequest = vm::dismissDetail,
            title = { Text(source.name) },
            text = {
                Column {
                    Text(typeLabel(source.type), style = MaterialTheme.typography.bodyMedium)
                    if (source.url.isNotBlank()) Text(source.url, style = MaterialTheme.typography.bodySmall)
                    if (!source.credentialOk) {
                        Text(
                            "凭据无法解密，请编辑并重新填写",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissDetail()
                    onEdit(source.id)
                }) { Text("编辑") }
            },
            dismissButton = {
                Row {
                    if (source.type == SourceType.WEBDAV) {
                        TextButton(onClick = {
                            vm.dismissDetail()
                            vm.shareText(source)?.let { text ->
                                copyToClipboard(context, text)
                                vm.showHint("分享配置已复制")
                            }
                        }) { Text("分享") }
                    }
                    TextButton(onClick = vm::dismissDetail) { Text("关闭") }
                }
            },
        )
    }

    state.pendingDelete?.let { source ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text("删除数据源") },
            text = {
                Text(
                    buildString {
                        append("将删除「${source.name}」的凭据与该源的扫描文件。")
                        state.impact?.let { append("影响 $it 个文件。") }
                        append("作品与播放进度会保留。")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = vm::deleteConfirmed) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = vm::cancelDelete) { Text("取消") }
            },
        )
    }
}

private fun typeLabel(type: SourceType): String = when (type) {
    SourceType.WEBDAV -> "WebDAV"
    SourceType.LOCAL -> "本地"
    SourceType.SMB -> "SMB"
    SourceType.EMBY -> "Emby"
    SourceType.JELLYFIN -> "Jellyfin"
}

/** 分享文本由 ViewModel 拼装，这里只负责写入剪贴板。 */
private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("MediaDock 数据源", text))
}
