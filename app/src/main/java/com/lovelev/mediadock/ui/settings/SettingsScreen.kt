package com.lovelev.mediadock.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import com.lovelev.mediadock.R
import com.lovelev.mediadock.ui.theme.ThemeMode
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lovelev.mediadock.ui.formatSize
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.data.prefs.AudioChannelCap
import com.lovelev.mediadock.data.prefs.RefreshMatchingMode
import com.lovelev.mediadock.BuildConfig
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onOpenSources: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenCapabilities: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        vm.load()
        vm.loadSubtitles()
        // 启动时静默查一次更新（失败不吭声，命中了才在「关于」那行留个标记）
        vm.checkUpdate(manual = false)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = vm.tmdbSecret.isNotBlank()
    val host = vm.sourceHost
    val itemCount by vm.itemCount.collectAsState()
    val sourceCount by vm.sourceCount.collectAsState()

    // 该步骤无法自动化验证：系统选择器不接受 adb 注入点击（WORKLOG 问题 56），需要用户手动操作。
    val pickLocalFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(vm::onLocalFolderPicked) }

    // 扫系统媒体库要的运行时权限（13+ / 12- 是两个不同的权限名，见 vm.mediaPermission）
    val askMediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) vm.scanMediaStore() else vm.noteMediaPermissionDenied() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ---- 外观 ----
        Section("外观") {
            ThemeModeRow(current = vm.themeMode, onPick = vm::pickThemeMode)
        }

        // ---- 播放 ----
        Section("播放") {
            PlaybackChoiceRow(
                label = "帧率匹配",
                options = listOf(
                    "自动" to (vm.refreshMatchingMode == RefreshMatchingMode.AUTO),
                    "关闭" to (vm.refreshMatchingMode == RefreshMatchingMode.OFF),
                ),
                onPick = { index ->
                    vm.pickRefreshMatching(
                        if (index == 0) RefreshMatchingMode.AUTO else RefreshMatchingMode.OFF,
                    )
                },
            )
            HorizontalDivider()
            PlaybackChoiceRow(
                label = "音频声道上限",
                options = listOf(
                    "自动" to (vm.audioChannelCap == AudioChannelCap.AUTO),
                    "5.1" to (vm.audioChannelCap == AudioChannelCap.SURROUND),
                    "立体声" to (vm.audioChannelCap == AudioChannelCap.STEREO),
                ),
                onPick = { index -> vm.pickAudioChannelCap(AudioChannelCap.entries[index]) },
            )
            Text(
                text = "下次播放生效；声道上限只限制轨道选择，实际下混由系统/解码器处理。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        // ---- 数据源 ----
        Section("数据源") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSources)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("数据源", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = "$sourceCount 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = " ›",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            // 本地媒体。**推荐使用系统媒体库**：仅需一次权限授权，整库通过一次查询返回；
            // SAF 逐目录遍历较慢。「指定文件夹」用于系统媒体库无法覆盖的场景。
            Text(
                text = "扫描系统媒体库",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (vm.hasMediaPermission()) {
                            vm.scanMediaStore()
                        } else {
                            askMediaPermission.launch(vm.mediaPermission())
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            HorizontalDivider()
            Text(
                text = if (vm.localFolderLabel.isBlank()) {
                    "指定文件夹…"
                } else {
                    "文件夹 · ${vm.localFolderLabel}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (vm.localFolderLabel.isBlank()) pickLocalFolder.launch(null) else vm.scanLocal()
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            vm.localHint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }
            if (vm.localFolderLabel.isNotBlank()) {
                HorizontalDivider()
                Text(
                    text = "移除本地文件夹",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.removeLocal() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
            HorizontalDivider()
        }

        // ---- 刮削 ----
        Section("刮削") {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = vm.tmdbSecret,
                    onValueChange = vm::editTmdbSecret,
                    label = { Text("TMDB 凭据") },
                    placeholder = { Text("API Read Access Token 或 API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = vm::save,
                    enabled = vm.tmdbSecret.isNotBlank() && !vm.saved,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.saved) "已保存" else if (saved) "更新" else "保存")
                }
            }
        }

        // ---- 字幕源 ----
        Section("字幕源") {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedTextField(
                    value = vm.subtitleKey,
                    onValueChange = vm::editSubtitleKey,
                    label = { Text("OpenSubtitles API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.subtitleUser,
                    onValueChange = vm::editSubtitleUser,
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.subtitlePass,
                    onValueChange = vm::editSubtitlePass,
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = vm::saveSubtitles,
                    enabled = vm.subtitleKey.isNotBlank() && vm.subtitleUser.isNotBlank() &&
                        vm.subtitlePass.isNotBlank() && !vm.subtitleSaved,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (vm.subtitleSaved) "已保存" else "保存")
                }
            }
        }

        // ---- 媒体库 ----
        Section("媒体库") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("影片数", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = itemCount.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
            DestructiveRow("清空媒体库") { vm.requestConfirm(Confirmable.ClearLibrary) }
        }

        // ---- 关于 ----
        Section("关于") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("版本", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            HorizontalDivider()
            // 运行日志：出问题时让用户自己看、自己复制，不用连电脑
            ValueRow(
                label = "运行日志",
                value = formatSize(AppLog.fileSizeBytes()),
                onClick = onOpenLogs,
            )
            HorizontalDivider()
            ValueRow(
                label = "播放能力 / 诊断",
                value = "设备、解码器与最近播放",
                onClick = onOpenCapabilities,
            )
            HorizontalDivider()

            // 检查更新：命中新版本就展开成一条「下载新版本」；其余状态就一行字，点一下重新查
            when (val state = vm.update) {
                is UpdateUiState.Available -> Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = "发现新版本 ${state.manifest.versionName}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    state.manifest.notes?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { openUrl(context, state.manifest.url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("下载新版本") }
                }

                else -> ValueRow(
                    label = "检查更新",
                    value = when (state) {
                        UpdateUiState.Checking -> "检查中…"
                        UpdateUiState.UpToDate -> "已是最新版本"
                        is UpdateUiState.Failed -> state.message
                        else -> ""
                    },
                    valueIsError = state is UpdateUiState.Failed,
                    onClick = { vm.checkUpdate(manual = true) },
                )
            }

            HorizontalDivider()

            // 用户的网站（站点品牌叫「星屿」）
            ValueRow(
                label = SITE_LABEL,
                value = SITE_HOST,
                onClick = { openUrl(context, SITE_URL) },
            )

            HorizontalDivider()
            // 申请 TMDB key 时承诺过要在应用里显示署名，这里就是那一处 —— 不得删除
            Text(
                text = "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    vm.confirming?.let { action ->
        AlertDialog(
            onDismissRequest = vm::dismissConfirm,
            title = { Text(action.title) },
            text = { Text(action.detail) },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.dismissConfirm()
                        when (action) {
                            Confirmable.ClearLibrary -> vm.clearLibrary()
                            Confirmable.Logout -> {
                                vm.logout()
                                onLoggedOut()
                            }
                        }
                    },
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissConfirm) { Text("取消") }
            },
        )
    }
}

/**
 * 主题三态选择器 —— 三个并排的选项（手机 / 太阳 / 月亮），照 NagoMusic 的 设置→外观 那套。
 *
 * 图标是手画的三个矢量（`res/drawable/ic_theme_*.xml`）：Material 核心图标里没有太阳月亮，
 * 而为了三个图标引 `material-icons-extended` 会把包撑大二十多 MB，不值。
 */
@Composable
private fun PlaybackChoiceRow(
    label: String,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEachIndexed { index, (text, selected) ->
                TextButton(onClick = { onPick(index) }) {
                    Text(
                        text = if (selected) "● $text" else text,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeModeRow(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val selected = mode == current
            Surface(
                onClick = { onPick(mode) },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.weight(1f),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 10.dp),
                ) {
                    Icon(
                        painter = painterResource(mode.iconRes()),
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

private fun ThemeMode.iconRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.drawable.ic_theme_system
    ThemeMode.LIGHT -> R.drawable.ic_theme_light
    ThemeMode.DARK -> R.drawable.ic_theme_dark
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            // 用 tertiary 不用 primary：亮柠檬绿当文字在浅底上看不清
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun DestructiveRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/** 把分享文本放进剪贴板（用平台 API，不得使用已废弃的 Compose 剪贴板包装）。 */
internal fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("Mooring 数据源", text))
}

/** 用系统浏览器 / 下载器打开一个 http(s) 链接。 */
private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** 左标题右值的一行；给了 [onClick] 才可点。 */
@Composable
private fun ValueRow(
    label: String,
    value: String,
    valueIsError: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = if (valueIsError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 用户站点（发版信息也放这儿：`mediadock/version.json`）。 */
private const val SITE_URL = "https://lovelev.eu.cc"
private const val SITE_LABEL = "星屿 · 我的网站"
private const val SITE_HOST = "lovelev.eu.cc"
