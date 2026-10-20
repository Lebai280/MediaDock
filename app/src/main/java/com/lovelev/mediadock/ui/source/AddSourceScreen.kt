package com.lovelev.mediadock.ui.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CLIPBOARD_SERVICE
import com.lovelev.mediadock.core.model.SourceType

@Composable
fun AddSourceScreen(
    vm: SourceViewModel,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = vm.state is TestState.Running
    val blocked = (vm.state as? TestState.Failure)?.retryBlocked == true
    val context = LocalContext.current

    // **自动识别、自动填入**：进这个页面时读取剪贴板，像分享配置就直接填好。
    // 认不出来什么都不做（不会乱动用户的输入）。
    LaunchedEffect(Unit) {
        vm.applyShared(clipboardText(context))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (vm.editingId != null) {
                "编辑 ${typeLabel(vm.type)} 数据源"
            } else {
                "添加 ${typeLabel(vm.type)} 数据源"
            },
            style = MaterialTheme.typography.headlineSmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceType.entries
                .filter { it != SourceType.LOCAL }
                .forEach { type ->
                    FilterChip(
                        selected = vm.type == type,
                        onClick = { vm.selectType(type) },
                        label = { Text(typeLabel(type)) },
                    )
                }
        }

        if (vm.type == SourceType.WEBDAV) {
            // 分享配置的提示 + 手动粘贴入口（自动识别没赶上时可再次点击）
            Row(verticalAlignment = Alignment.CenterVertically) {
                vm.sharedHint?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                } ?: Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.pasteShared(clipboardText(context)) }) {
                    Text("粘贴分享配置")
                }
            }
        }

        OutlinedTextField(
            value = vm.name,
            onValueChange = vm::editName,
            label = { Text("名称（用于区分多个源）") },
            placeholder = { Text("默认使用服务器域名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = vm.url,
            onValueChange = vm::editUrl,
            label = {
                Text(
                    when (vm.type) {
                        SourceType.SMB -> "主机地址"
                        SourceType.EMBY, SourceType.JELLYFIN -> "服务器地址"
                        else -> "服务器地址"
                    },
                )
            },
            placeholder = {
                Text(
                    when (vm.type) {
                        SourceType.SMB -> "192.168.1.10 或 host:445"
                        SourceType.EMBY, SourceType.JELLYFIN -> "http://192.168.1.10:8096"
                        else -> "https://example.com/dav"
                    },
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (vm.type == SourceType.SMB) {
            OutlinedTextField(
                value = vm.share,
                onValueChange = vm::editShare,
                label = { Text("共享名") },
                placeholder = { Text("movies") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = vm.username,
            onValueChange = vm::editUsername,
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = vm.password,
            onValueChange = vm::editPassword,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (vm.type == SourceType.WEBDAV) {
            OutlinedTextField(
                value = vm.rootPath,
                onValueChange = vm::editRootPath,
                label = { Text("根路径（可选）") },
                placeholder = { Text("默认使用地址中的路径") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (vm.type == SourceType.EMBY || vm.type == SourceType.JELLYFIN) {
            OutlinedTextField(
                value = vm.apiKey,
                onValueChange = vm::editApiKey,
                label = { Text("API Key（可选，替代账号密码）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = vm::test,
            enabled = !running && !blocked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (running) "连接中…" else "测试连接")
            }
        }

        when (val s = vm.state) {
            is TestState.Success -> {
                Text(
                    "已连接，${s.count} 个条目",
                    style = MaterialTheme.typography.titleMedium,
                    // primary 是亮柠檬绿，当文字在浅底上看不清
                    color = MaterialTheme.colorScheme.tertiary,
                )
                s.preview.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onConnected, modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.editingId != null) "完成" else "进入浏览")
                }
            }

            is TestState.Failure -> {
                Text(
                    s.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                if (s.retryBlocked) {
                    Text(
                        "已暂停重试。修改上面的输入后才可以再次尝试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> Unit
        }
    }
}

/**
 * 读系统剪贴板的纯文本。用平台 API 而不是 Compose 的 `LocalClipboardManager`（那个在新版已废弃）。
 * Android 10+ 只允许**当前有焦点的 App** 读剪贴板 —— 这个页面在前台，所以能读到。
 */
private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return runCatching { clip.getItemAt(0).coerceToText(context).toString() }.getOrNull()
}

private fun typeLabel(type: SourceType): String = when (type) {
    SourceType.WEBDAV -> "WebDAV"
    SourceType.LOCAL -> "本地"
    SourceType.SMB -> "SMB"
    SourceType.EMBY -> "Emby"
    SourceType.JELLYFIN -> "Jellyfin"
}
