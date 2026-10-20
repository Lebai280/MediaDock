package com.lovelev.mediadock.data.prefs

import android.content.Context

enum class RefreshMatchingMode { AUTO, OFF }

enum class AudioChannelCap(val max: Int) {
    AUTO(Int.MAX_VALUE),
    SURROUND(6),
    STEREO(2),
}

/**
 * 播放质量设置。用 SharedPreferences 是为了让 [PlaybackService] 能同步读取；
 * 设置变更在下一次播放/服务重建时生效。
 */
class PlaybackSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("playback_settings", Context.MODE_PRIVATE)

    fun refreshMatching(): RefreshMatchingMode =
        parseRefreshMatching(prefs.getString(KEY_REFRESH, null))

    fun audioChannelCap(): AudioChannelCap =
        parseAudioChannelCap(prefs.getString(KEY_CHANNELS, null))

    fun setRefreshMatching(mode: RefreshMatchingMode) {
        prefs.edit().putString(KEY_REFRESH, mode.name).apply()
    }

    fun setAudioChannelCap(cap: AudioChannelCap) {
        prefs.edit().putString(KEY_CHANNELS, cap.name).apply()
    }

    private companion object {
        const val KEY_REFRESH = "refresh_matching"
        const val KEY_CHANNELS = "audio_channel_cap"
    }
}

internal fun parseRefreshMatching(value: String?): RefreshMatchingMode =
    RefreshMatchingMode.entries.firstOrNull { it.name == value } ?: RefreshMatchingMode.AUTO

internal fun parseAudioChannelCap(value: String?): AudioChannelCap =
    AudioChannelCap.entries.firstOrNull { it.name == value } ?: AudioChannelCap.AUTO
