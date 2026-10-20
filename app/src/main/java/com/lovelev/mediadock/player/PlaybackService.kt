package com.lovelev.mediadock.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lovelev.mediadock.MainActivity
import com.lovelev.mediadock.MediaDockApp
import com.lovelev.mediadock.data.repo.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 后台播放的载体。播放器本身住在服务里，界面只是通过 MediaController 挂上去 ——
 * 所以**旋转屏幕 / 退出播放页都不会打断播放**，通知栏里也能继续控制。
 *
 * 进度也是在这里落的：界面走了、锁屏了，播放还在继续，进度就得继续记。
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ticker: Job? = null
    private lateinit var progress: ProgressRepository

    override fun onCreate() {
        super.onCreate()
        val container = (application as MediaDockApp).container
        progress = container.progress

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(container.playbackDataSourceFactory)
                    // 默认策略会把 401/429 当作可重试错误（点播默认重试 3 次）——
                    // 那会绕过「401/429 绝不自动重试」的硬约束，一次播放变 4 个失败请求。
                    // 见 WebDavLoadErrorPolicy。
                    .setLoadErrorHandlingPolicy(WebDavLoadErrorPolicy()),
            )
            // 同级目录的字幕是「没有语言标记」的字幕轨，media3 默认**不会**自动选中它，
            // 表现就是「明明有字幕却不显示」。打开这个开关让这类轨自动上。
            // 内嵌轨（带语言标记）不自动选，交给播放器控件里的字幕按钮手动切。
            .setTrackSelector(
                DefaultTrackSelector(this).apply {
                    parameters = buildUponParameters()
                        .setSelectUndeterminedTextLanguage(true)
                        .build()
                },
            )
            // 缓冲：**只动「卡顿后重新起播」这一档**（默认 5s → 8s）。
            // 源是夸克经 VPS 中转（约 3MB/s），带宽一抖就会 stall/resume 来回切，
            // 多等一点再恢复，体感比反复卡一下顺。
            // 其余三档保持默认：调大 maxBufferMs 是白吃内存
            // （60s @ 3MB/s ≈ 180MB），无实测依据时不得修改。
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                        DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                        8_000,
                    )
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 播放中就每 5 秒记一次位置；一暂停立刻补记一次并把计时器停掉（不播放时不空转）
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) startTicker() else stopTickerAndSave()
            }
        })
        exo.addAnalyticsListener(
            com.lovelev.mediadock.player.diagnostics.DiagnosticsAnalyticsListener(
                container.playbackDiagnostics,
            ),
        )

        // sessionActivity 一设，通知点一下就能回到 App，而且从最近任务里划掉时
        // 服务不会被顺带停掉（后台播放要的就是这个）。
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        player = exo
        session = MediaSession.Builder(this, exo)
            .setSessionActivity(openApp)
            .build()
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                saveProgress()
            }
        }
    }

    private fun stopTickerAndSave() {
        ticker?.cancel()
        ticker = null
        saveProgress()
    }

    private fun saveProgress() {
        val current = player ?: return
        val path = current.currentMediaItem?.mediaId ?: return
        val duration = current.duration
        // 时长未知（直播 / 还没解析出来）就不记 —— 没有分母，进度条和续播都没意义
        if (duration <= 0L) return
        val position = current.currentPosition
        scope.launch { progress.save(path, position, duration) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        // 先取一次位置再放掉播放器 —— release() 之后就问不出来了
        saveProgress()
        session?.release()
        session = null
        player?.release()
        player = null
        // **只停计时器，不 cancel 整个 scope。**
        // saveProgress() 是把「真正的库写入」launch 到 scope 上异步做的；这里紧跟着
        // scope.cancel() 会把它一起取消掉，于是退场时最后一段进度就丢了 ——
        // 进度是用户数据，宁可让这个 scope 随服务实例一起被回收。
        ticker?.cancel()
        ticker = null
        super.onDestroy()
    }

    private companion object {
        /** 节流：播放中每 5 秒落一次，不得每帧写入数据库。 */
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
