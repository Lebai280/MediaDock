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

@Composable
fun AddSourceScreen(
    vm: SourceViewModel,
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = vm.state is TestState.Running
    val blocked = (vm.state as? TestState.Failure)?.retryBlocked == true
    val context = LocalContext.current

    // **自动识别、自动填入**：进这个页面时看一眼剪贴板，像分享配置就直接填好。
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
        Text("添加 WebDAV 数据源", style = MaterialTheme.typography.headlineSmall)

        // 分享配置的提示 + 手动粘贴入口（自动识别没赶上时可以再点一下）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = vm.sharedHint ?: "朋友发来的配置可以直接粘贴进来",
                style = MaterialTheme.typography.bodySmall,
                color = if (vm.sharedHint != null) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { vm.pasteShared(clipboardText(context)) }) {
                Text("粘贴分享配置")
            }
        }

        OutlinedTextField(
            value = vm.url,
            onValueChange = vm::editUrl,
            label = { Text("服务器地址") },
            placeholder = { Text("https://你的服务器地址/dav") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

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

        OutlinedTextField(
            value = vm.rootPath,
            onValueChange = vm::editRootPath,
            label = { Text("根路径（可选）") },
            placeholder = { Text("留空则用地址里的路径") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

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
                    Text("进入浏览")
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
