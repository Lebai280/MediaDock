package com.lovelev.mediadock.ui.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
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
import androidx.compose.ui.draw.alpha
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
import com.lovelev.mediadock.core.model.AudioMode
import com.lovelev.mediadock.core.model.DeviceCapabilities
import com.lovelev.mediadock.core.model.HdrFormat
import com.lovelev.mediadock.core.model.HdrMode
import com.lovelev.mediadock.ui.common.rememberHapticTick
import com.lovelev.mediadock.core.model.PlaybackPlanner
import com.lovelev.mediadock.player.diagnostics.PlaybackDiagnostics
import com.lovelev.mediadock.player.probe.planInputFrom
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
            // 横屏 / 小屏时内容会超出对话框高度 —— 必须能滚，否则下面的行直接看不见（用户反馈）
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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

/** 只读播放诊断：当前格式、实际解码器、丢帧与设备显示能力。 */
@Composable
internal fun DiagnosticsDialog(
    title: String,
    path: String,
    tracks: Tracks?,
    diagnostics: PlaybackDiagnostics,
    device: DeviceCapabilities?,
    refreshMatching: Boolean,
    maxAudioChannels: Int,
    onDismiss: () -> Unit,
) {
    val videoGroup = remember(tracks) {
        tracks?.groups?.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
    }
    val audioGroup = remember(tracks) {
        tracks?.groups?.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            ?: tracks?.groups?.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
    }
    val videoFormat = videoGroup?.getTrackFormat(0)
    val audioFormat = audioGroup?.getTrackFormat(0)
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val currentRefreshRate = remember {
        context.findActivity()?.windowManager?.defaultDisplay?.refreshRate
    }
    val plan = device?.let {
        PlaybackPlanner.plan(
            input = planInputFrom(videoFormat, audioFormat),
            device = it,
            refreshMatching = refreshMatching,
            maxAudioChannels = maxAudioChannels,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放诊断") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoRow("文件", title.ifBlank { path.substringAfterLast('/') })
                plan?.let {
                    InfoRow("计划 HDR", "${hdrModeLabel(it.hdrMode)} · ${hdrLabel(it.hdrFormat)}")
                    InfoRow("计划音频", audioModeLabel(it.audioMode))
                    InfoRow(
                        "解码",
                        when (it.hardwareVideoDecoder) {
                            true -> "硬件"
                            false -> "软件"
                            null -> "未知"
                        },
                    )
                    InfoRow(
                        "帧率",
                        listOfNotNull(
                            it.frameRate?.let { fps -> "${fps}fps" },
                            if (it.refreshMatching) "匹配开" else "匹配关",
                        ).joinToString(" · ").ifBlank { "未知" },
                    )
                    it.notes.takeIf { notes -> notes.isNotEmpty() }?.let { notes ->
                        InfoRow("说明", notes.joinToString("；"))
                    }
                }
                InfoRow("当前刷新率", currentRefreshRate?.let { "${it}Hz" } ?: "未知")
                InfoRow("视频格式", formatSummary(videoFormat))
                InfoRow("视频支持", supportLabel(videoGroup))
                InfoRow("视频解码", diagnostics.videoDecoder ?: "未记录")
                InfoRow("音频格式", formatSummary(audioFormat))
                InfoRow("音频支持", supportLabel(audioGroup))
                InfoRow("音频解码", diagnostics.audioDecoder ?: "未记录")
                InfoRow("丢帧", diagnostics.droppedFrames.toString())
                InfoRow("首帧", if (diagnostics.firstFrameRendered) "已渲染" else "未渲染")
                device?.let {
                    InfoRow(
                        "显示 HDR",
                        it.display.hdrFormats.joinToString(", ") { hdr -> hdrLabel(hdr) }.ifBlank { "无" },
                    )
                }
                diagnostics.errorCode?.let { InfoRow("错误", it) }
                Text(
                    text = "只读诊断；不含账号密码或 URL。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun formatSummary(format: Format?): String {
    if (format == null) return "未记录"
    return buildList {
        format.sampleMimeType?.let(::add)
        format.codecs?.let(::add)
        if (format.width > 0 && format.height > 0) add("${format.width}×${format.height}")
        if (format.frameRate > 0f) add("${format.frameRate.toInt()}fps")
        format.colorInfo?.let { color ->
            add("color=${color.colorTransfer}/${color.colorSpace}")
        }
    }.joinToString(" ").ifBlank { "未记录" }
}

private fun supportLabel(group: Tracks.Group?): String = when {
    group == null -> "无"
    group.isSupported -> "支持"
    else -> "无解码器"
}

private fun hdrLabel(hdr: HdrFormat): String = when (hdr) {
    HdrFormat.HDR10 -> "HDR10"
    HdrFormat.HDR10_PLUS -> "HDR10+"
    HdrFormat.HLG -> "HLG"
    HdrFormat.DOLBY_VISION -> "DV"
    HdrFormat.SDR -> "SDR"
    HdrFormat.UNKNOWN -> "未知"
}

private fun hdrModeLabel(mode: HdrMode): String = when (mode) {
    HdrMode.DIRECT -> "直出"
    HdrMode.SDR -> "SDR"
    HdrMode.UNSUPPORTED -> "不支持"
    HdrMode.UNKNOWN -> "未知"
}

private fun audioModeLabel(mode: AudioMode): String = when (mode) {
    AudioMode.PASSTHROUGH -> "直通"
    AudioMode.DECODE -> "解码"
    AudioMode.NO_DECODER -> "无解码器"
    AudioMode.UNKNOWN -> "未知"
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
    val tick = rememberHapticTick()
    // 拖动时轨道加粗、端点出现圆点：给「正在拖」一个明确的手感
    val barHeight by animateDpAsState(
        targetValue = if (dragging) 6.dp else 3.dp,
        animationSpec = tween(120),
        label = "seekBarHeight",
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(120),
        label = "seekThumbAlpha",
    )

    Box(
        modifier = modifier
            .height(24.dp)
            .pointerInput(duration) {
                if (duration <= 0L) return@pointerInput
                // 吸附到整秒：松手不会停在 12:34.847 这种小数位置
                val at = { x: Float ->
                    val raw = (x / size.width * duration).toLong().coerceIn(0L, duration)
                    (raw / 1000L) * 1000L
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    // 吃掉按下事件：否则父层的手势也会跟着启动，在进度条上拖动会被误判成「点了一下」
                    down.consume()
                    tick()
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
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.30f), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(barHeight)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        )
        // 拖动端点的圆点
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(12.dp)
                    .alpha(thumbAlpha)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
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
        color = Color.Black.copy(alpha = 0.78f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        modifier = Modifier.align(Alignment.Center),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
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

/**
 * 选中音轨无解码器时的常驻提示。
 *
 * 不放进 [PlayerControls]：控件 4 秒后自动隐藏，而「没有声音」必须一直可见，
 * 否则用户只会以为片子坏了。提示非交互，不拦触摸。
 */
@Composable
internal fun BoxScope.AudioWarningBanner(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 60.dp, start = 16.dp, end = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
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
