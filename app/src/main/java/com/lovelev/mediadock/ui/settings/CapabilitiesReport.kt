package com.lovelev.mediadock.ui.settings

import com.lovelev.mediadock.core.model.DeviceCapabilities
import com.lovelev.mediadock.player.diagnostics.PlaybackDiagnostics

/** 只输出脱敏字段：没有 URL、账号、token 或文件内容。 */
object CapabilitiesReport {

    fun build(device: DeviceCapabilities?, playback: PlaybackDiagnostics): String = buildString {
        appendLine("Mooring 播放能力 / 诊断")
        appendLine()
        if (device == null) {
            appendLine("设备能力：尚未读取")
        } else {
            appendLine("== 设备 ==")
            appendLine("${device.identity.manufacturer} ${device.identity.model}")
            appendLine("Android ${device.identity.release} (SDK ${device.identity.sdkInt})")
            appendLine("ABI ${device.identity.abis.joinToString(", ")}")
            appendLine()
            appendLine("== 显示 ==")
            device.display.currentMode?.let {
                appendLine("当前 ${it.width}x${it.height}@${it.refreshRate}Hz")
            } ?: appendLine("当前模式：未知")
            appendLine("HDR：${device.display.hdrFormats.joinToString(", ") { it.name }.ifBlank { "无" }}")
            device.display.maxLuminance?.let { appendLine("最大亮度 ${it} nits") }
            device.display.maxAverageLuminance?.let { appendLine("平均亮度 ${it} nits") }
            appendLine("支持模式 ${device.display.supportedModes.size} 个")
            appendLine()
            appendLine("== 视频解码 ==")
            device.videoDecoders.forEach {
                val profiles = it.profiles.joinToString(",") { p -> "${p.profile}/${p.level}" }
                val size = listOfNotNull(it.maxWidth, it.maxHeight).joinToString("x")
                appendLine(
                    "${it.mime} · ${it.decoderName} · " +
                        if (it.hardware) "硬件" else "软件" +
                        (if (profiles.isNotBlank()) " · profile=$profiles" else "") +
                        (if (size.isNotBlank()) " · max=$size" else "") +
                        (it.maxFrameRate?.let { fps -> " · max=${fps}fps" } ?: ""),
                )
            }
            appendLine()
            appendLine("== 音频解码 ==")
            device.audioDecoders.forEach {
                appendLine(
                    "${it.mime} · ${it.decoderName} · " +
                        if (it.hardware) "硬件" else "软件" +
                        (it.maxChannels?.let { ch -> " · max=${ch}ch" } ?: ""),
                )
            }
            appendLine()
            appendLine("== 音频输出 ==")
            device.audioOutputs.forEach {
                appendLine("type=${it.type} ${it.name} · ${it.channelCounts.joinToString("/")}ch")
            }
            device.audioOutputCapabilities.maxChannelCount?.let {
                appendLine("Media3 最大声道 $it")
            }
        }
        appendLine()
        appendLine("== 最近播放 ==")
        playback.mediaId?.let { appendLine("文件 ${it.substringAfterLast('/')}") }
        appendLine("视频解码器 ${playback.videoDecoder ?: "未记录"}")
        appendLine("音频解码器 ${playback.audioDecoder ?: "未记录"}")
        appendLine("视频格式 ${playback.videoFormat ?: "未记录"}")
        appendLine("音频格式 ${playback.audioFormat ?: "未记录"}")
        appendLine("丢帧 ${playback.droppedFrames} · 首帧 ${if (playback.firstFrameRendered) "已渲染" else "未渲染"}")
        appendLine("错误 ${playback.errorCode ?: "无"}")
        appendLine()
        appendLine("说明：本报告不含账号密码、URL 或文件内容。")
    }
}
