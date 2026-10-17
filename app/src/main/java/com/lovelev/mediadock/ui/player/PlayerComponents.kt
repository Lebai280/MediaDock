package com.lovelev.mediadock.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Tracks
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.ui.formatClock
import com.lovelev.mediadock.ui.formatSize
import com.lovelev.mediadock.ui.nav.findActivity

/** 窗口级亮度（0..1），只影响本 Activity，退出播放页自动回到系统默认。 */
internal fun applyBrightness(context: Context, value: Float) {
    val activity = context.findActivity() ?: return
    activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
}

/** 详细信息：文件 + 当前播放的技术信息。 */
@Composable
internal fun InfoDialog(
    title: String,
    path: String,
    position: Long,
    duration: Long,
    tracks: Tracks?,
    onDismiss: () -> Unit,
) {
    val video: Format? = remember(tracks) {
        tracks?.groups?.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }?.getTrackFormat(0)
    }
    val subtitleCount = tracks?.groups?.count { it.type == C.TRACK_TYPE_TEXT } ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("详细信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoRow("名称", title)
                InfoRow("路径", path)
                InfoRow("时长", "${formatClock(duration)}（${formatClock(position)} 已播）")
                video?.let {
                    val resolution = if (it.width > 0 && it.height > 0) "${it.width}×${it.height}" else ""
                    val fps = if (it.frameRate > 0f) "${it.frameRate.toInt()}fps" else ""
                    if (resolution.isNotEmpty() || fps.isNotEmpty()) {
                        InfoRow("画面", listOf(resolution, fps).filter { s -> s.isNotEmpty() }.joinToString(" · "))
                    }
                    it.codecs?.let { c -> InfoRow("编码", c) }
                    if (it.bitrate > 0) InfoRow("码率", "${formatSize(it.bitrate / 8L)}/s")
                }
                if (subtitleCount > 0) InfoRow("字幕轨", "$subtitleCount 条")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 细进度条（自绘，不用 M3 Slider —— 它轨道太粗）。
 * 点一下跳转，按住左右拖调位置；两种手势都由它自己消费，不会漏给父层。
 */
@Composable
internal fun ThinSeekBar(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableLongStateOf(0L) }
    val shown = if (dragging) dragValue else position
    val fraction = if (duration > 0L) (shown.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(24.dp)
            .pointerInput(duration) {
                if (duration <= 0L) return@pointerInput
                val at = { x: Float -> (x / size.width * duration).toLong().coerceIn(0L, duration) }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 吃掉按下事件：否则父层的手势也会跟着启动，在进度条上拖动会被误判成「点了一下」
                    down.consume()
                    val startAt = at(down.position.x)
                    dragging = true
                    dragValue = startAt
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dragValue = at(change.position.x)
                        moved = true
                        change.consume()
                    }
                    dragging = false
                    AppLog.d("player", "seekbar ${if (moved) "drag" else "tap"} -> $dragValue")
                    onSeek(dragValue)
                }
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.30f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(3.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
    }
}

/** 锁定 / 解锁按钮。位置固定在左侧中间。 */
@Composable
internal fun LockButton(locked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
            contentDescription = if (locked) "解锁" else "锁定",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 亮度 / 音量的调节提示。 */
@Composable
internal fun BoxScope.OverlayHint(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.align(Alignment.Center),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun BoxScope.OverlayMessage(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.align(Alignment.Center).padding(32.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}

@Composable
internal fun RequestNotificationPermission() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
