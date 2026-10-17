package com.lovelev.mediadock.data.repo

import android.content.Context
import androidx.media3.common.MimeTypes
import com.lovelev.mediadock.data.subtitle.SubtitleException
import com.lovelev.mediadock.data.subtitle.SubtitleFile
import com.lovelev.mediadock.data.subtitle.SubtitleHit
import com.lovelev.mediadock.data.subtitle.SubtitleProvider
import java.io.File
import kotlinx.coroutines.CancellationException

/** 落到本地的字幕文件（挂载时要用它和它的 MIME）。 */
data class LocalSubtitle(val file: File, val mimeType: String)

/**
 * 字幕：搜索 / 下载 / 落盘。
 *
 * **字幕源是可切换的**（见 [SubtitleProvider]）—— 现在只接了 OpenSubtitles，界面只认这个接口，
 * 以后加 Assrt 之类的只要往 [providers] 里再塞一个实现。
 *
 * 下载和本地选的字幕都**统一落盘**到 `filesDir/subtitles/`，挂载时一律走本地文件 ——
 * 这样不用管内容提供者的临时 URI 会不会失效（SAF 选来的 `content://` 授权活不长）。
 */
class SubtitleRepository(
    private val context: Context,
    private val providers: List<SubtitleProvider>,
) {

    /** 当前用的源（暂时只有第一个；要切换就在设置里选，接口已经留好）。 */
    val active: SubtitleProvider get() = providers.first()

    suspend fun isConfigured(): Boolean = active.isConfigured()

    suspend fun search(
        query: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        languages: String,
    ): List<SubtitleHit> = active.search(query, year, season, episode, languages)

    /** 下载一条并落盘。 */
    suspend fun download(hit: SubtitleHit): LocalSubtitle {
        val file = try {
            active.download(hit)
        } catch (e: CancellationException) {
            throw e   // 取消要原样抛出，别当成业务失败吞掉
        } catch (e: Exception) {
            throw e
        }
        return save(file)
    }

    /** 把一段字幕字节落盘。 */
    fun save(file: SubtitleFile): LocalSubtitle = save(file.fileName, file.bytes)

    /** 把一段字幕字节落盘（本地选的文件也走这里）。 */
    fun save(name: String, bytes: ByteArray): LocalSubtitle {
        val directory = File(context.filesDir, "subtitles").apply { mkdirs() }
        // 未知扩展名**不能默默当 srt**：选中 .torrent / .mp4 时会被 SubripParser
        // 当文本硬解，日志里全是二进制乱码、界面上什么也不显示（坑 73）。
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext !in SUBTITLE_EXTENSIONS) {
            throw SubtitleException(
                "这不是字幕文件（.${ext.ifBlank { "无扩展名" }}）。请选 .srt / .ass / .ssa / .vtt。",
            )
        }
        val stem = name.substringBeforeLast('.', name)
            .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .take(60)
            .ifBlank { "subtitle" }
        val target = File(directory, "$stem.$ext")
        try {
            target.writeBytes(bytes)
        } catch (e: CancellationException) {
            throw e   // 取消要原样抛出，别当成业务失败吞掉
        } catch (e: Exception) {
            throw SubtitleException("字幕存不下来：${e.message}", e)
        }
        return LocalSubtitle(target, mimeOf(ext))
    }

    private fun mimeOf(ext: String): String = when (ext) {
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        else -> MimeTypes.APPLICATION_SUBRIP
    }

    private companion object {
        val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub")
    }
}
