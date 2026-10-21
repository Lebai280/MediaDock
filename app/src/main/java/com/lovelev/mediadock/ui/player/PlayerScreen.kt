package com.lovelev.mediadock.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.ui.nav.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TAG = "MediaDock"

/** 画中画参数：16:9；Android 12+ 额外支持「按 Home 自动进入」。 */
private fun pipParams(autoEnter: Boolean): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
    }
    return builder.build()
}

/** 控件显示后多久自动隐藏（播放中且没锁定时）。 */
private const val CONTROLS_HIDE_MS = 4_000L

/** 锁上后解锁按钮停留多久自动隐藏。 */
private const val LOCK_HINT_MS = 3_000L

/** 亮度 / 音量 / 挂载结果提示停留多久。太短来不及读（用户反馈）。 */
private const val HINT_LINGER_MS = 2_200L

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
 * 记录音轨的解码能力：TrueHD/DTS 在目标机上无解码器，只会静音。
 * 真机排查时靠这条日志判断提示为什么出现（或没出现）。
 */
private fun logAudioTracks(stage: String, tracks: Tracks) {
    tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEachIndexed { index, group ->
        val format = group.getTrackFormat(0)
        AppLog.d(
            "player",
            "audio $stage #$index mime=${format.sampleMimeType} codecs=${format.codecs} " +
                "selected=${group.isSelected} supported=${group.isSupported}",
        )
    }
}

/** 字幕轨类型决定字号能否套用：ASS/SSA 不碰，普通文本才调。 */
private fun logTextTracks(stage: String, tracks: Tracks) {
    tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEachIndexed { index, group ->
        val format = group.getTrackFormat(0)
        AppLog.d(
            "player",
            "text $stage #$index mime=${format.sampleMimeType} codecs=${format.codecs} " +
                "label=${format.label} lang=${format.language} " +
                "selected=${group.isSelected} supported=${group.isSupported}",
        )
    }
}

/**
 * 播放画面 + **自绘控件层**（不用 PlayerView 的原生控件）。
 *
 * ## 触摸分发（本页关键约束，修改时必须保持）
 *
 * 结构：铺满的**父 `Box`** 上挂**一个** `pointerInput`（手写手势循环，同时管「点一下显示/隐藏」
 * 和左右半屏上下滑调亮度/音量），**按钮、进度条是它的子节点**。Compose 里子节点先于父拿到事件，
 * 所以点按钮、拖进度条不会被父抢走，只有点空白才落到父上。
 *
 * 两个问题（均已处理，不得重复）：
 *  1. **不得将「点击切换」层与按钮设为兄弟节点**：点击层位于上层会拦截全部触摸事件（上一版本即为此问题）。
 *  2. **不得在同一节点挂载两个并行 `pointerInput`**（一个 `detectTapGestures`、一个
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
    val locked = vm.locked
    var unlockHintVisible by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var tracks by remember { mutableStateOf<Tracks?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    val infoOpen = vm.infoOpen
    val searchOpen = vm.searchOpen
    val diagnosticsOpen = vm.diagnosticsOpen
    val playbackDiagnostics by vm.playbackDiagnostics.collectAsState()
    val deviceCapabilities = vm.deviceCapabilities
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val scope = rememberCoroutineScope()

    val activity = context.findActivity() as? ComponentActivity
    val subtitleSize = vm.subtitleSize
    // ASS/SSA 通常带特效排版：这类字幕不套用字号，原样渲染。
    // Media3 会把字幕轨的 sampleMimeType 统一成 application/x-media3-cues，
    // 原始格式只保留在 codecs 里（实测：ASS 为 text/x-ssa，SRT 为 application/x-subrip）。
    val effectSubtitle = remember(tracks) {
        val format = tracks?.groups
            ?.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
            ?.getTrackFormat(0)
        format?.sampleMimeType == MimeTypes.TEXT_SSA || format?.codecs == MimeTypes.TEXT_SSA
    }
    var inPip by remember { mutableStateOf(activity?.isInPictureInPictureMode == true) }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            AppLog.d("player", "pip mode -> ${info.isInPictureInPictureMode}")
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    // Android 12+：播放中按 Home 直接缩成小窗。离开播放页时必须关掉，
    // 否则从其它页面回桌面也会进画中画。
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        DisposableEffect(activity, playing) {
            if (activity == null) return@DisposableEffect onDispose { }
            activity.setPictureInPictureParams(pipParams(autoEnter = playing))
            onDispose { activity.setPictureInPictureParams(pipParams(autoEnter = false)) }
        }
    }

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
    val audioWarningNow = remember(tracks) {
        unsupportedAudioWarning(audioTrackStatuses(tracks?.groups.orEmpty()))
    }
    // 起播瞬间轨道是分批上报的（先只有一条轨道），直接跟状态走会闪一下提示；
    // 连续 1.5 秒成立才展示，消失则立即清除。
    var audioWarning by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(audioWarningNow) {
        if (audioWarningNow == null) {
            audioWarning = null
        } else {
            delay(1_500)
            audioWarning = audioWarningNow
            // 没有解码器时播放器只会静音，日志里留下证据，便于真机排查
            AppLog.d("player", "audio unsupported: $audioWarningNow")
        }
    }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        // 先主动读一次当前轨道。`onTracksChanged` **只在轨道变化时**回调 ——
        // 如果监听器挂上去时播放器早就 prepare 完了（续播、旋转屏、从迷你条进来都会这样），
        // 那个回调永远不会补发，tracks 就一直是 null：音轨菜单和字幕菜单会整块消失。
        tracks = c.currentTracks
        logAudioTracks("attached", c.currentTracks)
        logTextTracks("attached", c.currentTracks)
        AppLog.d("player", "tracks attached: ${c.currentTracks.groups.size} groups")
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // 多源合并后同一内容可能有多个版本：非认证类错误按选源顺序自动换源。
                // 401/429 绝不重试，也不切换（OpenList 按来源 IP 计锁定）。
                val mediaId = c.currentMediaItem?.mediaId
                if (!isAuthOrLockedError(error)) {
                    val fallback = vm.takeFallback(mediaId)
                    val index = c.currentMediaItemIndex
                    val item = c.currentMediaItem
                    if (fallback != null && item != null && index >= 0) {
                        AppLog.d("player", "failover -> $fallback")
                        c.replaceMediaItem(index, item.buildUpon().setUri(fallback).build())
                        c.prepare()
                        c.play()
                        message = null
                        return
                    }
                }
                message = describePlaybackError(error)
                // 带上原始异常 —— 给用户看的是人话，落到日志里的是能查的堆栈
                AppLog.e("player", "播放出错: $message", error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onTracksChanged(tracks2: Tracks) {
                logAudioTracks("changed", tracks2)
                logTextTracks("changed", tracks2)
                AppLog.d("player", "tracks changed: ${tracks2.groups.size} groups")
                tracks = tracks2
            }
        }
        c.addListener(listener)
        onDispose { c.removeListener(listener) }
    }

    LaunchedEffect(controller, vm.queueSignature) {
        val c = controller ?: return@LaunchedEffect
        // 队列和控制器上挂着的那批一样、且这次选择已经应用过，就不再 set ——
        // 旋屏重建走的就是这条路，重设会把播放拽回开头。
        // 但「同一队列里改了选中条目」必须按新选中项切换，令牌就是用来区分这两种情况的。
        if (!c.holdsQueue(vm.queueSignature) || vm.needsSelectionApply) {
            AppLog.d(
                "player",
                "apply selection idx=${ready.startIndex} of ${ready.items.size} " +
                    "(sameQueue=${c.holdsQueue(vm.queueSignature)})",
            )
            c.setMediaItems(
                ready.items.map { it.toMediaItem() },
                ready.startIndex,
                ready.startPositionMs,
            )
            c.prepare()
            vm.markSelectionApplied()
        }
        c.play()
        AppLog.d("player", "player: play() called on ${c.mediaItemCount} items")
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
            AppLog.d("player", "controls auto-hide")
        }
    }

    LaunchedEffect(locked) {
        if (locked) unlockHintVisible = true
    }

    LaunchedEffect(locked, unlockHintVisible) {
        if (locked && unlockHintVisible) {
            delay(LOCK_HINT_MS)
            unlockHintVisible = false
            AppLog.d("player", "unlock button auto-hide")
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
                    applySubtitleSize(subtitleSize, effectSubtitle)
                    playerView = this
                }
            },
            update = {
                it.player = controller
                it.applySubtitleSize(subtitleSize, effectSubtitle)
            },
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
                                // 用**累计**位移算：每次事件的增量只有一点点，直接拿去加起始值
                                // 只会原地抖、几乎不变化（上一版就是这么坏的）。
                                val fraction =
                                    -travelledY / size.height * SWIPE_FULL_RANGE_PER_SCREEN
                                if (startX < size.width / 2f) {
                                    val next = (startBrightness + fraction).coerceIn(0.02f, 1f)
                                    applyBrightness(context, next)
                                    hint = "亮度 ${(next * 100).toInt()}%"
                                    AppLog.d("player", "brightness -> $next (total=$travelledY)")
                                } else {
                                    val next = (startVolume + fraction * maxVolume)
                                        .toInt().coerceIn(0, maxVolume)
                                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                    hint = "音量 ${next * 100 / maxVolume}%"
                                    AppLog.d("player", "volume -> $next (total=$travelledY)")
                                }
                                change.consume()
                            }
                        }

                        // 没成拖拽就当成「点了一下」
                        if (!dragging) {
                            if (locked) {
                                AppLog.d("player", "tap while locked -> show unlock")
                                unlockHintVisible = true
                            } else {
                                AppLog.d("player", "overlay tap -> controls ${if (controlsVisible) "hide" else "show"}")
                                controlsVisible = !controlsVisible
                            }
                        }
                    }
                },
        ) {
            if (controller == null) return@Box
            // 画中画里只留画面：小窗容不下自绘控件，系统也会提供自己的控件
            if (inPip) return@Box

            // 锁定按钮**固定在左侧中间**。解锁时跟着控件显隐；锁上后显示 3 秒自动隐藏，
            // 点一下再出现 —— 位置不变，找得着就行。
            val showLockButton = if (locked) unlockHintVisible else controlsVisible
            if (showLockButton) {
                LockButton(
                    locked = locked,
                    onClick = {
                        if (locked) {
                            AppLog.d("player", "unlock tapped")
                            vm.updateLocked(false)
                            controlsVisible = true
                        } else {
                            AppLog.d("player", "lock tapped")
                            vm.updateLocked(true)
                            controlsVisible = false
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                )
            }

            // 锁定后其余控件一律不画（手势也在上面按 locked 关掉了）
            if (locked) return@Box

            // 控件淡入淡出，不得硬切（产品要求动画平滑）
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                PlayerControls(
                    controller = controller,
                    tracks = tracks,
                    title = title,
                    path = path,
                    position = position,
                    duration = duration,
                    playing = playing,
                    onSeek = { target ->
                        AppLog.d("player", "seek -> $target")
                        controller.seekTo(target)
                        position = target
                    },
                    onInfo = {
                        AppLog.d("player", "info tapped")
                        vm.updateInfoOpen(true)
                    },
                    onDiagnostics = {
                        AppLog.d("player", "diagnostics tapped")
                        vm.updateDiagnosticsOpen(true)
                    },
                    onEnterPip = {
                        AppLog.d("player", "pip tapped")
                        val ok = runCatching {
                            activity?.enterPictureInPictureMode(pipParams(autoEnter = true))
                        }.getOrNull() == true
                        if (!ok) hint = "此设备不支持画中画"
                    },
                    onScreenshot = {
                        AppLog.d("player", "screenshot tapped")
                        captureFrame(context, playerView) { result -> hint = result }
                    },
                    onLoadLocalSubtitle = {
                        AppLog.d("player", "load local subtitle tapped")
                        pickSubtitle.launch(arrayOf("*/*"))
                    },
                    onSearchSubtitles = {
                        AppLog.d("player", "search subtitles tapped")
                        vm.updateSearchOpen(true)
                    },
                    onBack = {
                        AppLog.d("player", "back tapped")
                        backDispatcher?.onBackPressed()
                    },
                )
            }

            hint?.let { OverlayHint(it) }
        }

        // 常驻提示：选中音轨无解码器时一直可见（含控件隐藏、锁屏状态），非交互不拦触摸
        audioWarning?.let { AudioWarningBanner(it) }

        if (infoOpen) {
            InfoDialog(
                title = title,
                path = path,
                position = position,
                duration = duration,
                tracks = tracks,
                onDismiss = { vm.updateInfoOpen(false) },
            )
        }

        if (diagnosticsOpen) {
            DiagnosticsDialog(
                title = title,
                path = path,
                tracks = tracks,
                diagnostics = playbackDiagnostics,
                device = deviceCapabilities,
                refreshMatching = vm.refreshMatching,
                maxAudioChannels = vm.maxAudioChannels,
                onDismiss = { vm.updateDiagnosticsOpen(false) },
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
                    vm.updateSearchOpen(false)
                },
                onDismiss = { vm.updateSearchOpen(false) },
            )
        }

        val shown = message ?: "连接播放服务失败".takeIf { vm.connectFailed }
        shown?.let { OverlayMessage(it) }
    }
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
