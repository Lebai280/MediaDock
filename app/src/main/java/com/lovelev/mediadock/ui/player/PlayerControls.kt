package com.lovelev.mediadock.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import com.lovelev.mediadock.R
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.ui.formatClock
import java.util.Locale

/**
 * 自绘控件。
 *
 * 布局照主流播放器：**顶部** 返回 / 标题 / 右上角「⋯」菜单（字幕、详细信息、截图）；
 * **中间** 大播放键；**底部** 细进度条 + 左右时间，**右下角** 倍速。
 */
@Composable
internal fun PlayerControls(
    controller: MediaController,
    tracks: Tracks?,
    title: String,
    path: String,
    position: Long,
    duration: Long,
    playing: Boolean,
    onSeek: (Long) -> Unit,
    onInfo: () -> Unit,
    onScreenshot: () -> Unit,
    onLoadLocalSubtitle: () -> Unit,
    onSearchSubtitles: () -> Unit,
    onBack: () -> Unit,
) {
    var moreExpanded by remember { mutableStateOf(false) }
    var subtitleExpanded by remember { mutableStateOf(false) }
    var audioExpanded by remember { mutableStateOf(false) }
    var speedExpanded by remember { mutableStateOf(false) }
    var speed by remember(controller) { mutableStateOf(controller.playbackParameters.speed) }

    val textGroups = remember(tracks) {
        tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT }.orEmpty()
    }
    val subtitlesOn = tracks?.groups?.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected } == true

    // 音轨。用户的片子里有「国粤日三音轨」这种，不给他入口就只能听播放器自己挑的那条。
    val audioGroups = remember(tracks) {
        tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- 顶/底各一条渐变压暗 ----
        // 控件文字是白色的，压在**亮画面**上会直接消失（实测放到「秒速五厘米」的白底标题卡时，
        // 返回箭头和标题完全看不见）。这两条只压在顶栏和底栏范围内，画面中间不受影响。
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(96.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(124.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.70f)),
                    ),
                ),
        )

        // ---- 顶部：返回 / 标题 / ⋯ ----
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )

            Box {
                IconButton(onClick = { moreExpanded = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.MoreHoriz, "更多", tint = Color.White)
                }
                // 更多：字幕 / 音轨 / 详细信息 / 截图（都在这里，顶部就不挤了）
                DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (subtitlesOn) "字幕（开）" else "字幕") },
                        onClick = { moreExpanded = false; subtitleExpanded = true },
                    )
                    // 只有一条音轨就不显示这项 —— 一个选项的菜单没有意义
                    if (audioGroups.size > 1) {
                        DropdownMenuItem(
                            text = { Text("音轨") },
                            onClick = { moreExpanded = false; audioExpanded = true },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("详细信息") },
                        onClick = { moreExpanded = false; onInfo() },
                    )
                    DropdownMenuItem(
                        text = { Text("截图") },
                        onClick = { moreExpanded = false; onScreenshot() },
                    )
                }

                // 音轨面板：列出现有音轨，点一条切过去
                DropdownMenu(expanded = audioExpanded, onDismissRequest = { audioExpanded = false }) {
                    audioGroups.forEachIndexed { index, group ->
                        val label = audioTrackLabel(group.getTrackFormat(0), index)
                        DropdownMenuItem(
                            text = { Text(if (group.isSelected) "● $label" else label) },
                            onClick = {
                                AppLog.d("player", "audio -> $label")
                                controller.trackSelectionParameters = controller.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                    .build()
                                audioExpanded = false
                            },
                        )
                    }
                }

                // 字幕面板：关掉 / 选轨 / 本地文件 / 在线搜索
                DropdownMenu(
                    expanded = subtitleExpanded,
                    onDismissRequest = { subtitleExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("关闭字幕") },
                        onClick = {
                            AppLog.d("player", "subtitle -> off")
                            controller.trackSelectionParameters = controller.trackSelectionParameters
                                .buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                            subtitleExpanded = false
                        },
                    )
                    textGroups.forEach { group ->
                        val format = group.getTrackFormat(0)
                        val label = format.label
                            ?: format.language?.let { Locale.forLanguageTag(it).displayLanguage }
                            ?: "字幕轨"
                        DropdownMenuItem(
                            text = { Text(if (group.isSelected) "● $label" else label) },
                            onClick = {
                                AppLog.d("player", "subtitle -> $label")
                                controller.trackSelectionParameters = controller.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                    .build()
                                subtitleExpanded = false
                            },
                        )
                    }
                    if (textGroups.isNotEmpty()) HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("从本地文件加载…") },
                        onClick = { subtitleExpanded = false; onLoadLocalSubtitle() },
                    )
                    DropdownMenuItem(
                        text = { Text("在线搜索…") },
                        onClick = { subtitleExpanded = false; onSearchSubtitles() },
                    )
                }
            }
        }

        // ---- 中间：上一集 / 大播放暂停键 / 下一集 ----
        // 队列就是「同目录的视频」（点文件时传下来的），所以这里的上/下一集天然覆盖
        // S01E01、EP01 这类同名分集 —— 没有的按钮置灰，别让人点了没反应。
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasPrev = controller.hasPreviousMediaItem()
            IconButton(
                onClick = {
                    AppLog.d("player", "previous tapped")
                    controller.seekToPreviousMediaItem()
                },
                enabled = hasPrev,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_previous),
                    contentDescription = "上一集",
                    tint = Color.White.copy(alpha = if (hasPrev) 1f else 0.3f),
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(
                onClick = {
                    AppLog.d("player", "center play/pause tapped (isPlaying=${controller.isPlaying})")
                    if (controller.isPlaying) controller.pause() else controller.play()
                },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }

            val hasNext = controller.hasNextMediaItem()
            IconButton(
                onClick = {
                    AppLog.d("player", "next tapped")
                    controller.seekToNextMediaItem()
                },
                enabled = hasNext,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_skip_next),
                    contentDescription = "下一集",
                    tint = Color.White.copy(alpha = if (hasNext) 1f else 0.3f),
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        // ---- 底部：细进度条 + 左时间 / 右时长 / 最右下角倍速 ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
        ) {
            ThinSeekBar(
                position = position,
                duration = duration,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatClock(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                // 时长还没读出来（刚起播那一两秒）显示 --:-- ，别显示 00:00 让人以为片子是空的
                Text(
                    text = if (duration > 0L) formatClock(duration) else "--:--",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Box {
                    TextButton(onClick = {
                        AppLog.d("player", "speed tapped")
                        speedExpanded = true
                    }) {
                        Text(text = speedLabel(speed), color = Color.White)
                    }
                    DropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                        SPEEDS.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(value)) },
                                onClick = {
                                    AppLog.d("player", "speed -> $value")
                                    controller.setPlaybackSpeed(value)
                                    speed = value
                                    speedExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
