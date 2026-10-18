package com.lovelev.mediadock.ui.player

import android.content.ComponentName
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.lovelev.mediadock.di.AppContainer
import com.lovelev.mediadock.player.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 迷你播放条的数据源：外壳底部常驻的那一条「正在播放」。
 *
 * **它是独立于播放页的第二个 MediaController，这是刻意的。**
 * 播放页的 [PlayerViewModel] 用 `onCleared()` 表达「离开播放页」并据此暂停 ——
 * 把那个控制器挪到外壳来复用，就会毁掉这条已经真机验过的行为（用户明确要的）。
 * 两个控制器连的是同一个 MediaSession，所以互不干扰：谁按暂停都真的暂停。
 *
 * 这个 ViewModel 挂在**外壳**（活动级）上，不会因为进出播放页而被清掉。
 */
class NowPlayingViewModel(private val container: AppContainer) : ViewModel() {

    var controller by mutableStateOf<MediaController?>(null)
        private set

    /** 当前在播的文件名。没有就不显示这条（外壳据此决定要不要渲染）。 */
    var title by mutableStateOf<String?>(null)
        private set

    var playing by mutableStateOf(false)
        private set

    var position by mutableStateOf(0L)
        private set

    var duration by mutableStateOf(0L)
        private set

    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refresh(mediaItem)
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            refresh(controller?.currentMediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            playing = controller?.isPlaying == true
            refresh(controller?.currentMediaItem)
        }
    }

    init {
        connect()
        // 位置只在播放时按秒刷新 —— 暂停时不用白跑（而且控制器的 position 会自己稳住）
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val c = controller ?: continue
                if (c.isPlaying) {
                    position = c.currentPosition.coerceAtLeast(0)
                    duration = c.duration.takeIf { it > 0 } ?: duration
                }
            }
        }
    }

    /**
     * mediaId 就是文件路径（见 [PlayerViewModel.Playable]），所以标题直接从这里取 ——
     * 比依赖 MediaMetadata 可靠（本项目未给每个 item 写 metadata）。
     */
    private fun refresh(item: MediaItem?) {
        title = item?.mediaId?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        val c = controller
        if (c != null) {
            position = c.currentPosition.coerceAtLeast(0)
            duration = c.duration.takeIf { it > 0 } ?: 0L
        }
    }

    private fun connect() {
        val context = container.appContext
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val result = runCatching { future.get() }
                val c = result.getOrNull()
                controller = c
                c?.addListener(listener)
                refresh(c?.currentMediaItem)
                playing = c?.isPlaying == true
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    /**
     * 进播放页之前，把控制器上**当前的队列与选中项**写回 [com.lovelev.mediadock.player.PlaybackSelection]。
     *
     * 为什么需要：`playbackSelection` 只活在内存里。进程被杀之后再点迷你条进来，
     * 播放页本来会看到「没有选中要播放的文件」—— 而播放器其实还在服务里跑着。
     * 现在从控制器把队列读回来，这条路就通了。
     */
    fun syncSelection() {
        val c = controller ?: return
        val current = c.currentMediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val paths = (0 until c.mediaItemCount)
            .mapNotNull { c.getMediaItemAt(it).mediaId.takeIf(String::isNotBlank) }
        container.playbackSelection.set(current, paths)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    /** 关掉这条：停播并清空队列，通知也一起收掉。 */
    fun stop() {
        val c = controller ?: return
        c.stop()
        c.clearMediaItems()
        title = null
        playing = false
        position = 0L
        duration = 0L
    }

    override fun onCleared() {
        // **只放开控制器，不暂停** —— 这里被清掉意味着整个界面要没了，
        // 而「退到后台继续放」是用户要的功能（服务还在，通知栏接着控制）。
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { NowPlayingViewModel(container) }
        }
    }
}
