package com.lovelev.mediadock.ui.history

import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.PlaybackProgress
import com.lovelev.mediadock.data.tmdb.TmdbClient
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.ui.formatClock

/**
 * 播放历史 —— 按「最近看过」倒序，带进度和「接着看」。
 *
 * 数据直接来自 `playback_progress`（它本来就有 `updatedAt`，是播放进度的副产物，
 * **不需要新建表**）。名字和海报从媒体库那份里查；查不到（从浏览页直接播、没进过库的）
 * 就退回文件名 —— 那种条目一样能点开接着看，因为进度就是按 path 记的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onPlay: (path: String, queue: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val recent by container.progress.observeRecent().collectAsState(initial = emptyList())
    val items by container.library.items.collectAsState(initial = emptyList())
    val byPath: Map<String, MediaItemEntity> = items.associateBy { it.path }

    // 媒体库查不到的条目（历史比库老、或库里已经重扫过）要有个像样的标题。
    // `content://media/.../12345` 的最后一段是**数字 id**，直接拿来当标题就是一行乱码般的数字 ——
    // 所以这里一次性把它们的显示名从 ContentResolver 查出来（不在每行 composition 里查）。
    val context = androidx.compose.ui.platform.LocalContext.current
    var resolvedNames by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Map<String, String>>(emptyMap()) }
    androidx.compose.runtime.LaunchedEffect(recent) {
        val missing = recent.map { it.path }
            .filter { it.startsWith("content://") && it !in byPath }
            .distinct()
        if (missing.isEmpty()) {
            resolvedNames = emptyMap()
            return@LaunchedEffect
        }
        resolvedNames = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            missing.mapNotNull { p ->
                val name = runCatching {
                    context.contentResolver.query(
                        android.net.Uri.parse(p),
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null, null, null,
                    )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull()
                name?.let { p to it }
            }.toMap()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("播放历史") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { insets ->
        if (recent.isEmpty()) {
            Box(Modifier.padding(insets).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没看过什么",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.padding(insets).fillMaxSize()) {
            items(recent, key = { it.path }) { entry ->
                HistoryRow(
                    entry = entry,
                    item = byPath[entry.path],
                    fallbackName = resolvedNames[entry.path],
                    onClick = {
                        // 同目录的视频当队列 —— 和从浏览页点进来的行为一致（自动下一集）
                        val folder = entry.path.substringBeforeLast('/', "")
                        val queue = items
                            .filter { it.path.substringBeforeLast('/', "") == folder }
                            .map { it.path }
                            .ifEmpty { listOf(entry.path) }
                        onPlay(entry.path, queue)
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: PlaybackProgress,
    item: MediaItemEntity?,
    fallbackName: String?,
    onClick: () -> Unit,
) {
    val title = item?.parsedTitle?.takeIf { it.isNotBlank() }
        ?: fallbackName
        ?: entry.path.substringAfterLast('/')
    val fraction = if (entry.durationMs > 0) {
        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Poster(item?.posterPath, title)

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(formatClock(entry.positionMs))
                    if (entry.durationMs > 0) append(" / ${formatClock(entry.durationMs)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (fraction > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "接着看",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 有海报用海报，没有就用片名首字当占位（和海报墙的做法一致，不编图）。 */
@Composable
private fun Poster(posterPath: String?, title: String) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 62.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (posterPath != null) {
            AsyncImage(
                model = TmdbClient.posterUrl(posterPath, width = 92),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.take(1),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
