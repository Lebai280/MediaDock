package com.lovelev.mediadock.ui.player

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.lovelev.mediadock.data.repo.LocalSubtitle
import com.lovelev.mediadock.data.subtitle.SubtitleHit
import com.lovelev.mediadock.data.subtitle.subtitleLanguageLabel
import com.lovelev.mediadock.data.tmdb.TitleParser
import com.lovelev.mediadock.ui.formatClock
import com.lovelev.mediadock.ui.formatSize
import com.lovelev.mediadock.ui.nav.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private const val TAG = "MediaDock"

/** 控件显示后多久自动隐藏（播放中且没锁定时）。 */
private const val CONTROLS_HIDE_MS = 4_000L

/** 锁上后解锁按钮停留多久自动隐藏。 */
private const val LOCK_HINT_MS = 3_000L

/** 亮度 / 音量调节提示停留多久。 */
private const val HINT_LINGER_MS = 900L

/** 整屏高度对应多少调节行程：1 = 从屏幕顶部划到底 = 满量程。 */
private const val SWIPE_FULL_RANGE_PER_SCREEN = 1.0f

@Composable
fun PlayerScreen(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    // 没有通知权限时前台服务转不起来，播放等于起不来
    RequestNotificationPermission()

    when (val s = vm.state) {
        is PlayerState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is PlayerState.Unavailable -> Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = s.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        is PlayerState.Ready -> PlayerSurface(vm, s, modifier)
    }
}

/**
 * 播放画面 + **自绘控件层**（不用 PlayerView 的原生控件）。
 *
 * ## 触摸分发（这一页最要紧的一条，别再改坏）
 *
 * 结构：铺满的**父 `Box`** 上挂**一个** `pointerInput`（手写手势循环，同时管「点一下显示/隐藏」
 * 和左右半屏上下滑调亮度/音量），**按钮、进度条是它的子节点**。Compose 里子节点先于父拿到事件，
 * 所以点按钮、拖进度条不会被父抢走，只有点空白才落到父上。
 *
 * 两个坑（都是踩过的，别重犯）：
 *  1. **别把「点击切换」层和按钮做成兄弟节点** —— 点击层压在上面会把触摸全截胡（上一版就是）。
 *  2. **别在同一个节点挂两个并行 `pointerInput`**（一个 `detectTapGestures`、一个
 *     `detectVerticalDragGestures`）—— 前者会把事件流吞掉大半，拖拽只吃到末尾一小段，
 *     表现就是「又抖又不灵敏」。所以这里把手势合并成一个循环自己写。
 */
@Composable
private fun PlayerSurface(
    vm: PlayerViewModel,
    ready: PlayerState.Ready,
    modifier: Modifier = Modifier,
) {
    val controller = vm.controller
    val context = LocalContext.current
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    var message by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var unlockHintVisible by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var infoOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val scope = rememberCoroutineScope()

    // 本地字幕：挑一个文件 → 读出来 → 存进应用目录 → 挂到当前播放上
    val pickSubtitle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = queryDisplayName(context, uri)
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                    .getOrNull()
            }
            if (bytes == null) {
                hint = "读不了这个文件"
                return@launch
            }
            try {
                mountSubtitle(controller, vm.saveSubtitle(name, bytes))
                hint = "已挂载字幕"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hint = e.message ?: "挂载字幕失败"
            }
        }
    }
    var title by remember {
        mutableStateOf(ready.items.getOrNull(ready.startIndex)?.path?.substringAfterLast('/').orEmpty())
    }

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    val path = controller?.currentMediaItem?.mediaId.orEmpty()

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        // ⚠️ 先主动读一次当前轨道。`onTracksChanged` **只在轨道变化时**回调 ——
        // 如果监听器挂上去时播放器早就 prepare 完了（续播、旋转屏、从迷你条进来都会这样），
        // 那个回调永远不会补发，tracks 就一直是 null：音轨菜单和字幕菜单会整块消失。
        tracks = c.currentTracks
        Log.d(TAG, "tracks attached: ${c.currentTracks.groups.size} groups")
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                message = describePlaybackError(error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onTracksChanged(tracks2: Tracks) {
                Log.d(TAG, "tracks changed: ${tracks2.groups.size} groups")
                tracks = tracks2
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    LaunchedEffect(controller, vm.queueSignature) {
        val c = controller ?: return@LaunchedEffect
        // 队列和控制器上挂着的那批一样就不再 set —— 旋屏重建走的就是这条路，
        // 重设会把播放拽回开头，「旋转屏幕不中断」就废了。
        if (!c.holdsQueue(vm.queueSignature)) {
            c.setMediaItems(
                ready.items.map { it.toMediaItem() },
                ready.startIndex,
                ready.startPositionMs,
            )
            c.prepare()
        }
        c.play()
        Log.d(TAG, "player: play() called on ${c.mediaItemCount} items")
    }

    // 位置 / 时长 / 标题轮询。控制器上报的位置是**隔一阵才来一次**的，直接显示秒数会卡着跳；
    // 所以记下它上次变化的时刻，超过一段时间没更新就按本地时钟补上（若控制器本身在连续外推，
    // 这个分支不会触发，不会重复计时）。
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        var lastRaw = Long.MIN_VALUE
        var lastRawAt = 0L
        while (true) {
            val raw = c.currentPosition
            val now = SystemClock.elapsedRealtime()
            if (raw != lastRaw) {
                lastRaw = raw
                lastRawAt = now
            }
            val stale = now - lastRawAt
            val shown = if (c.isPlaying && stale > 1_200L) raw + stale.coerceAtMost(2_000L) else raw
            duration = c.duration.coerceAtLeast(0L)
            position = if (duration > 0L) shown.coerceIn(0L, duration) else shown
            playing = c.isPlaying
            c.mediaMetadata.title?.let { title = it.toString() }
            delay(200)
        }
    }

    LaunchedEffect(controlsVisible, playing, locked) {
        if (controlsVisible && playing && !locked) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
            Log.d(TAG, "controls auto-hide")
        }
    }

    LaunchedEffect(locked) {
        if (locked) unlockHintVisible = true
    }

    LaunchedEffect(locked, unlockHintVisible) {
        if (locked && unlockHintVisible) {
            delay(LOCK_HINT_MS)
            unlockHintVisible = false
            Log.d(TAG, "unlock button auto-hide")
        }
    }

    LaunchedEffect(hint) {
        if (hint != null) {
            delay(HINT_LINGER_MS)
            hint = null
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // 自绘控件：关掉原生控件，画面之外的东西全由下面的 Compose 层画
                    useController = false
                    playerView = this
                }
            },
            update = { it.player = controller },
            onRelease = { it.player = null },
        )

        // 自绘控件层：**一个** pointerInput 同时管点击与上下滑；控件是它的子节点
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked, duration) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startX = down.position.x
                        var travelledY = 0f
                        var dragging = false
                        var startVolume = 0
                        var startBrightness = 0.5f
                        startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        startBrightness = context.findActivity()
                            ?.window?.attributes?.screenBrightness
                            ?.takeIf { it >= 0f } ?: 0.5f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break

                            travelledY += change.position.y - change.previousPosition.y
                            if (!dragging && abs(travelledY) > viewConfiguration.touchSlop) dragging = true

                            if (dragging && !locked) {
                                // ⚠️ 用**累计**位移算：每次事件的增量只有一点点，直接拿去加起始值
                                // 只会原地抖、几乎不变化（上一版就是这么坏的）。
                                val fraction =
                                    -travelledY / size.height * SWIPE_FULL_RANGE_PER_SCREEN
                                if (startX < size.width / 2f) {
                                    val next = (startBrightness + fraction).coerceIn(0.02f, 1f)
                                    applyBrightness(context, next)
                                    hint = "亮度 ${(next * 100).toInt()}%"
                                    Log.d(TAG, "brightness -> $next (total=$travelledY)")
                                } else {
                                    val next = (startVolume + fraction * maxVolume)
                                        .toInt().coerceIn(0, maxVolume)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    hint = "音量 ${next * 100 / maxVolume}%"
                                    Log.d(TAG, "volume -> $next (total=$travelledY)")
                                }
                                change.consume()
                            }
                        }

                        // 没成拖拽就当成「点了一下」
                        if (!dragging) {
                            if (locked) {
                                Log.d(TAG, "tap while locked -> show unlock")
                                unlockHintVisible = true
                            } else {
                                Log.d(TAG, "overlay tap -> controls ${if (controlsVisible) "hide" else "show"}")
                                controlsVisible = !controlsVisible
                            }
                        }
                    }
                },
        ) {
            if (controller == null) return@Box

            // 锁定按钮**固定在左侧中间**。解锁时跟着控件显隐；锁上后显示 3 秒自动隐藏，
            // 点一下再出现 —— 位置不变，找得着就行。
            val showLockButton = if (locked) unlockHintVisible else controlsVisible
            if (showLockButton) {
                LockButton(
                    locked = locked,
                    onClick = {
                        if (locked) {
                            Log.d(TAG, "unlock tapped")
                            locked = false
                            controlsVisible = true
                        } else {
                            Log.d(TAG, "lock tapped")
                            locked = true
                            controlsVisible = false
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                )
            }

            // 锁定后其余控件一律不画（手势也在上面按 locked 关掉了）
            if (locked) return@Box

            if (controlsVisible) {
                PlayerControls(
                    controller = controller,
                    tracks = tracks,
                    title = title,
                    path = path,
                    position = position,
                    duration = duration,
                    playing = playing,
                    onSeek = { target ->
                        Log.d(TAG, "seek -> $target")
                        controller.seekTo(target)
                        position = target
                    },
                    onInfo = {
                        Log.d(TAG, "info tapped")
                        infoOpen = true
                    },
                    onScreenshot = {
                        Log.d(TAG, "screenshot tapped")
                        captureFrame(context, playerView) { result -> hint = result }
                    },
                    onLoadLocalSubtitle = {
                        Log.d(TAG, "load local subtitle tapped")
                        pickSubtitle.launch(arrayOf("*/*"))
                    },
                    onSearchSubtitles = {
                        Log.d(TAG, "search subtitles tapped")
                        searchOpen = true
                    },
                    onBack = {
                        Log.d(TAG, "back tapped")
                        backDispatcher?.onBackPressed()
                    },
                )
            }

            hint?.let { OverlayHint(it) }
        }

        if (infoOpen) {
            InfoDialog(
                title = title,
                path = path,
                position = position,
                duration = duration,
                tracks = tracks,
                onDismiss = { infoOpen = false },
            )
        }

        if (searchOpen) {
            SubtitleSearchDialog(
                vm = vm,
                path = path,
                onDownloaded = { local ->
                    try {
                        mountSubtitle(controller, local)
                        hint = "已挂载字幕"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        hint = e.message ?: "挂载字幕失败"
                    }
                    searchOpen = false
                },
                onDismiss = { searchOpen = false },
            )
        }

        val shown = message ?: "连接播放服务失败".takeIf { vm.connectFailed }
        shown?.let { OverlayMessage(it) }
    }
}

/** 窗口级亮度（0..1），只影响本 Activity，退出播放页自动回到系统默认。 */
private fun applyBrightness(context: Context, value: Float) {
    val activity = context.findActivity() ?: return
    activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
}

/**
 * 自绘控件。
 *
 * 布局照主流播放器：**顶部** 返回 / 标题 / 右上角「⋯」菜单（字幕、详细信息、截图）；
 * **中间** 大播放键；**底部** 细进度条 + 左右时间，**右下角** 倍速。
 */
@Composable
private fun PlayerControls(
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
                                Log.d(TAG, "audio -> $label")
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
                            Log.d(TAG, "subtitle -> off")
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
                                Log.d(TAG, "subtitle -> $label")
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

        // ---- 中间：大播放/暂停键 ----
        IconButton(
            onClick = {
                Log.d(TAG, "center play/pause tapped (isPlaying=${controller.isPlaying})")
                if (controller.isPlaying) controller.pause() else controller.play()
            },
            modifier = Modifier.align(Alignment.Center).size(72.dp),
        ) {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
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
                        Log.d(TAG, "speed tapped")
                        speedExpanded = true
                    }) {
                        Text(text = speedLabel(speed), color = Color.White)
                    }
                    DropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                        SPEEDS.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(speedLabel(value)) },
                                onClick = {
                                    Log.d(TAG, "speed -> $value")
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

/** 详细信息：文件 + 当前播放的技术信息。 */
@Composable
private fun InfoDialog(
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
private fun ThinSeekBar(
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
                    Log.d(TAG, "seekbar ${if (moved) "drag" else "tap"} -> $dragValue")
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
private fun LockButton(locked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
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

/**
 * 截图：`PixelCopy` 从画面那层取一帧，存进相册（Pictures/MediaDock）。
 * 回来一句话（成功 / 失败）给界面提示。
 */
private fun captureFrame(context: Context, root: View?, onDone: (String) -> Unit) {
    val surfaceView = root?.findSurfaceView()
    if (surfaceView == null || surfaceView.width <= 0 || surfaceView.height <= 0) {
        onDone("截图失败：拿不到画面")
        return
    }
    val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            surfaceView,
            bitmap,
            { result ->
                if (result != PixelCopy.SUCCESS) {
                    Log.d(TAG, "PixelCopy failed: $result")
                    onDone("截图失败")
                    return@request
                }
                onDone(saveToGallery(context, bitmap))
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (e: Exception) {
        Log.d(TAG, "PixelCopy threw: ${e.message}")
        onDone("截图失败：${e.message}")
    }
}

/** 存进相册。API 29+ 走 MediaStore 的 scoped storage，不需要存储权限。 */
private fun saveToGallery(context: Context, bitmap: Bitmap): String {
    val name = "MediaDock_${System.currentTimeMillis()}.png"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MediaDock")
        }
    }
    return try {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return "截图保存失败"
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        "已保存到相册"
    } catch (e: Exception) {
        Log.d(TAG, "saveToGallery failed: ${e.message}")
        "截图保存失败：${e.message}"
    } finally {
        bitmap.recycle()
    }
}

/** 从 View 树里找出承载视频的那层 SurfaceView。 */
private fun View.findSurfaceView(): SurfaceView? {
    if (this is SurfaceView) return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findSurfaceView()?.let { return it }
        }
    }
    return null
}

/** 亮度 / 音量的调节提示。 */
@Composable
private fun BoxScope.OverlayHint(text: String) {
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
private fun BoxScope.OverlayMessage(text: String) {
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
 * 把字幕挂到当前播放上。
 *
 * 做法是**重建 MediaItem 再重设**（不改播放器内部状态）：给当前条目加上这条 SubtitleConfiguration，
 * 用 `setMediaItem(新条目, 当前进度)` —— 进度不丢，只是重开一下解码。App 侧加载的字幕没有语言标记，
 * 播放服务里开了 `setSelectUndeterminedTextLanguage(true)`，所以会自动被选上。
 */
private fun mountSubtitle(controller: MediaController?, local: LocalSubtitle) {
    val c = controller ?: return
    val current = c.currentMediaItem ?: return
    val configuration = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(local.file))
        .setMimeType(local.mimeType)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
    val existing = current.localConfiguration?.subtitleConfigurations.orEmpty()
        .filterNot { it.uri == configuration.uri }
    val position = c.currentPosition
    c.setMediaItem(
        current.buildUpon().setSubtitleConfigurations(existing + configuration).build(),
        position,
    )
    c.prepare()
    c.play()
    Log.d(TAG, "subtitle mounted: ${local.file.name} @${position}ms")
}

/** 从 SAF 的 content:// 里问出显示名（带扩展名，好判断字幕类型）。 */
private fun queryDisplayName(context: Context, uri: Uri): String {
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.let { return it }
                }
            }
    }
    return uri.lastPathSegment ?: "subtitle.srt"
}

/**
 * 在线搜索字幕。片名/年份**从当前文件名解析出来预填**（复用 TitleParser，和刮削用同一套规则）；
 * 结果按下载量排序；点一条就下载 + 挂载。
 */
@Composable
private fun SubtitleSearchDialog(
    vm: PlayerViewModel,
    path: String,
    onDownloaded: (LocalSubtitle) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val parsed = remember(path) {
        TitleParser.parse(
            fileName = path.substringAfterLast('/'),
            folderName = path.substringBeforeLast('/', "").substringAfterLast('/'),
        )
    }
    var query by remember { mutableStateOf(parsed.title) }
    var languages by remember { mutableStateOf("zh-cn,en") }
    var busy by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SubtitleHit>>(emptyList()) }
    var notice by remember { mutableStateOf<String?>(null) }
    var configured by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { configured = vm.subtitlesConfigured() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("在线搜索 · ${vm.subtitleProviderName}") },
        text = {
            Column {
                if (!configured) {
                    Text("还没填字幕源凭据。去「设置 → 字幕源」把 API Key 和账号填上，回来就能搜。")
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("片名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LANGUAGES.forEach { (label, value) ->
                            TextButton(
                                onClick = { languages = value },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (languages == value) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
                            ) { Text(label) }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = {
                                val keyword = query.trim()
                                if (keyword.isEmpty()) return@TextButton
                                scope.launch {
                                    busy = true
                                    notice = null
                                    results = emptyList()
                                    try {
                                        results = vm.searchSubtitles(
                                            query = keyword,
                                            year = parsed.year,
                                            season = parsed.season,
                                            episode = parsed.episode,
                                            languages = languages,
                                        )
                                        if (results.isEmpty()) notice = "没找到，换个片名试试"
                                        Log.d(TAG, "subtitle search '$keyword' -> ${results.size}")
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        notice = e.message ?: "搜索失败"
                                    } finally {
                                        busy = false
                                    }
                                }
                            },
                            enabled = !busy,
                        ) { Text(if (busy) "…" else "搜索") }
                    }
                    notice?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (results.isNotEmpty()) {
                        HorizontalDivider()
                        LazyColumn(modifier = Modifier.fillMaxWidth().height(240.dp)) {
                            items(results, key = { it.id }) { hit ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !busy) {
                                            scope.launch {
                                                busy = true
                                                notice = null
                                                try {
                                                    onDownloaded(vm.downloadSubtitle(hit))
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    notice = e.message ?: "下载失败"
                                                } finally {
                                                    busy = false
                                                }
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                ) {
                                    Text(
                                        text = hit.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            subtitleLanguageLabel(hit.language).takeIf { it.isNotBlank() },
                                            hit.downloads?.let { "下载 $it" },
                                            hit.release?.takeIf { it.isNotBlank() },
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

private fun Playable.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setMediaId(path)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(path.substringAfterLast('/'))
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )

    if (subtitleUrls.isNotEmpty()) {
        builder.setSubtitleConfigurations(
            subtitleUrls.map { subtitleUrl ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                    .setMimeType(subtitleMimeType(subtitleUrl))
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            },
        )
    }
    return builder.build()
}

/** ExoPlayer 要按 MIME 选解析器，扩展名对不上就解不出字幕。 */
private fun subtitleMimeType(url: String): String =
    when (url.substringAfterLast('.', "").lowercase()) {
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        else -> MimeTypes.APPLICATION_SUBRIP
    }

/**
 * 音轨在菜单里显示成什么。
 *
 * media3 报的 `language` 是容器里的原样标记，MKV 里通常是 **ISO 639-2/T 的三字母码**
 * （`jpn` / `chi` / `yue`…），而 `Locale.forLanguageTag` 只认 BCP-47 的两字母形式，
 * 直接喂进去会原样返回 "jpn"。所以常用语言这里自己映射一张小表。
 * 顺带带上声道数 —— 「国粤日三音轨」那种片子，光看语言名不一定分得清哪条是 5.1。
 */
private fun audioTrackLabel(format: Format, index: Int): String {
    val name = format.label?.takeIf { it.isNotBlank() }
        ?: LANGUAGE_NAMES[format.language?.lowercase()]
        ?: "音轨 ${index + 1}"
    val channels = when (format.channelCount) {
        1 -> "单声道"
        2 -> "立体声"
        6 -> "5.1"
        8 -> "7.1"
        else -> null
    }
    return if (channels != null) "$name · $channels" else name
}

private val LANGUAGE_NAMES = mapOf(
    "chi" to "中文", "zho" to "中文", "chs" to "中文（简）", "cht" to "中文（繁）",
    "cmn" to "国语", "yue" to "粤语", "eng" to "英语",
    "jpn" to "日语", "kor" to "韩语", "fra" to "法语", "deu" to "德语", "spa" to "西班牙语",
    "rus" to "俄语", "ita" to "意大利语", "por" to "葡萄牙语", "tha" to "泰语",
)

/** 控制器上挂的是不是就是这一批（mediaId 用的就是路径，所以能直接比）。 */
private fun MediaController.holdsQueue(signature: String): Boolean {
    if (mediaItemCount != signature.split('\u0000').size) return false
    return (0 until mediaItemCount).joinToString("\u0000") { getMediaItemAt(it).mediaId } == signature
}

/** 把 ExoPlayer 的错误翻成能照着做事的一句话。 */
private fun describePlaybackError(error: PlaybackException): String {
    // 状态码未必就在最外层的 cause 上，顺着因果链找一层
    val httpCode = generateSequence(error.cause) { it.cause }
        .take(10)
        .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode
    return when (httpCode) {
        401 -> "账号或密码不正确，请回数据源页重新登录"
        429 -> "服务器已暂时锁定登录，请稍后再试 —— 不要反复重试"
        404 -> "文件不存在或路径已失效"
        null -> "播放失败：${error.message ?: "未知错误"}"
        else -> "播放失败：服务器返回 HTTP $httpCode"
    }
}

private fun speedLabel(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()}×" else "$value×"

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

/** 搜字幕时的语言选项（显示名 to OpenSubtitles 用的语言标记）。 */
private val LANGUAGES = listOf("中文" to "zh-cn", "英文" to "en", "全部" to "zh-cn,en")

@Composable
private fun RequestNotificationPermission() {
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
