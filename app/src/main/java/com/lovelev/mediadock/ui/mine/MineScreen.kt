package com.lovelev.mediadock.ui.mine

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.lovelev.mediadock.data.repo.DetailFillState
import com.lovelev.mediadock.data.repo.ScanState
import com.lovelev.mediadock.data.repo.ScrapeState
import com.lovelev.mediadock.ui.library.LibraryViewModel

/**
 * 「我的」页 —— 照 NagoMusic 的 `profile_page.dart` 那套：右上角一个齿轮，下面按
 * 资源库 / 媒体库 / 应用 分组，每组是「图标 + 标题 + 副标题 + ›」的行。
 *
 * 它把原来散在三处的东西收拢了：
 *  - 媒体库页右上角的 ⋯ 菜单（扫描 / 刮削 / 补齐）
 *  - 底部导航第三个 Tab（浏览）
 *  - 底部导航第四个 Tab（设置）
 *
 * 动作直接复用 [LibraryViewModel] 的方法：仓库是**应用作用域**的，所以这里新建一个 VM
 * 拿到的是同一份扫描/刮削状态，不会出现「两个页面各扫各的」。
 */
@Composable
fun MineScreen(
    container: com.lovelev.mediadock.di.AppContainer,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBrowse: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: LibraryViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = LibraryViewModel.factory(container))
    val scan by vm.scan.collectAsState()
    val scrape by vm.scrape.collectAsState()
    val fill by vm.fill.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 顶部：标题 + 齿轮（照 NagoMusic，齿轮直接去设置）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "设置")
            }
        }

        Section("资源库") {
            MineRow(Icons.Filled.Favorite, "收藏", null, onOpenFavorites)
            HorizontalDivider()
            MineRow(Icons.Filled.DateRange, "播放历史", null, onOpenHistory)
            HorizontalDivider()
            MineRow(Icons.AutoMirrored.Filled.List, "浏览", null, onOpenBrowse)
        }

        Section("媒体库") {
            MineRow(
                icon = Icons.Filled.Refresh,
                title = if (scan is ScanState.Running) "取消扫描" else "扫描",
                subtitle = when (val s = scan) {
                    is ScanState.Running -> "已扫 ${s.directories} 个目录 · ${s.files} 个视频"
                    is ScanState.Done -> "共 ${s.files} 个视频" + if (s.truncated) "（达到上限）" else ""
                    is ScanState.Failed -> s.message
                    ScanState.Idle -> null
                },
                onClick = vm::toggleScan,
            )
            HorizontalDivider()
            MineRow(
                icon = Icons.Filled.Star,
                title = if (scrape is ScrapeState.Running) "取消刮削" else "刮削",
                subtitle = when (val s = scrape) {
                    is ScrapeState.Running -> "${s.done}/${s.total}"
                    is ScrapeState.Done -> "匹配 ${s.matched} 条 · ${s.failed} 条没匹配上"
                    is ScrapeState.Failed -> s.message
                    ScrapeState.Idle -> null
                },
                onClick = vm::toggleScrape,
            )
            HorizontalDivider()
            MineRow(
                icon = Icons.Filled.Build,
                title = if (fill is DetailFillState.Running) {
                    "取消补齐（${(fill as DetailFillState.Running).done}/${(fill as DetailFillState.Running).total}）"
                } else {
                    "补齐影片信息"
                },
                subtitle = when (val s = fill) {
                    is DetailFillState.Running -> "正在拉类型、国家、演职人员"
                    is DetailFillState.Done -> "本次补齐 ${s.filled} 条" + if (s.failed > 0) " · ${s.failed} 条失败" else ""
                    is DetailFillState.Failed -> s.message
                    DetailFillState.Idle -> null
                },
                onClick = vm::toggleFillDetails,
            )
        }

        Section("应用") {
            MineRow(Icons.Filled.Settings, "设置", null, onOpenSettings)
            HorizontalDivider()
            MineRow(Icons.Filled.Info, "版本与日志", null, onOpenLogs)
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

/** 图标 + 标题 + 副标题 + ›，照 NagoMusic 的设置行。 */
@Composable
private fun MineRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
