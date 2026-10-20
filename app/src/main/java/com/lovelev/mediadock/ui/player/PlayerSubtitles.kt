package com.lovelev.mediadock.ui.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaController
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.data.repo.LocalSubtitle
import com.lovelev.mediadock.data.subtitle.SubtitleHit
import com.lovelev.mediadock.data.subtitle.subtitleLanguageLabel
import com.lovelev.mediadock.core.model.TitleParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 把字幕挂到当前播放上。
 *
 * 做法是**重建 MediaItem 再重设**（不改播放器内部状态）：给当前条目加上这条 SubtitleConfiguration，
 * 用 `setMediaItem(新条目, 当前进度)` —— 进度不丢，只是重开一下解码。App 侧加载的字幕没有语言标记，
 * 播放服务里开了 `setSelectUndeterminedTextLanguage(true)`，所以会自动被选上。
 */
internal fun mountSubtitle(controller: MediaController?, local: LocalSubtitle) {
    val c = controller ?: return
    val index = c.currentMediaItemIndex
    if (index == C.INDEX_UNSET || c.mediaItemCount == 0) return
    val current = c.currentMediaItem ?: return
    val configuration = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(local.file))
        .setMimeType(local.mimeType)
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
    val existing = current.localConfiguration?.subtitleConfigurations.orEmpty()
        .filterNot { it.uri == configuration.uri }
    val updated = current.buildUpon().setSubtitleConfigurations(existing + configuration).build()

    // 不能用 `setMediaItem(单个)`：Media3 的语义是**把整个播放列表换成这一条**，
    // 队列会被缩成 1 条，自动下一集直接失效（问题 72）。要保留队列，只替换当前这一条。
    val items = (0 until c.mediaItemCount).map { i ->
        if (i == index) updated else c.getMediaItemAt(i)
    }
    val position = c.currentPosition
    c.setMediaItems(items, index, position)
    c.prepare()
    c.play()
    AppLog.d("player", "subtitle mounted: ${local.file.name} @${position}ms (queue=${items.size})")
}

/** 从 SAF 的 content:// 里问出显示名（带扩展名，好判断字幕类型）。 */
internal fun queryDisplayName(context: Context, uri: Uri): String {
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

/** ExoPlayer 要按 MIME 选解析器，扩展名对不上就解不出字幕。 */
internal fun subtitleMimeType(url: String): String =
    when (url.substringAfterLast('.', "").lowercase()) {
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "vtt" -> MimeTypes.TEXT_VTT
        else -> MimeTypes.APPLICATION_SUBRIP
    }

/**
 * 在线搜索字幕。片名/年份**从当前文件名解析出来预填**（复用 TitleParser，和刮削用同一套规则）；
 * 结果按下载量排序；点一条就下载 + 挂载。
 */
@Composable
internal fun SubtitleSearchDialog(
    vm: PlayerViewModel,
    path: String,
    onDownloaded: (LocalSubtitle) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val query = vm.subtitleQuery
    val languages = vm.subtitleLanguages
    val busy = vm.subtitleBusy
    val results = vm.subtitleResults
    val notice = vm.subtitleNotice
    val configured = vm.subtitlesConfigured

    LaunchedEffect(path) {
        vm.prepareSubtitleSearch(path)
        vm.loadSubtitlesConfigured()
    }

    // 用自定义 Dialog 而不是 AlertDialog：后者的 text 槽在横屏会被压得很矮，
    // 搜到的字幕列表只能露出半行（用户反馈「详细看搜到哪些字幕的时候有问题」）。
    // 这里让结果列表占满剩余高度并可滚动，标题/输入/关闭固定。
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 520.dp)
                .heightIn(max = 640.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.92f),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                // 标题 + 小药丸「关闭」：不再单占一整行（用户反馈关闭板块太大）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "在线搜索 · ${vm.subtitleProviderName}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (!configured) {
                    Text("未配置字幕源")
                    Spacer(Modifier.weight(1f))
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = vm::updateSubtitleQuery,
                        label = { Text("片名") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LANGUAGES.forEach { (label, value) ->
                            TextButton(
                                onClick = { vm.updateSubtitleLanguages(value) },
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
                            onClick = { vm.searchSubtitlesNow(path) },
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
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(results, key = { it.id }) { hit ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !busy) {
                                            scope.launch {
                                                vm.updateSubtitleBusy(true)
                                                vm.showSubtitleNotice(null)
                                                try {
                                                    onDownloaded(vm.downloadSubtitle(hit))
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    vm.showSubtitleNotice(e.message ?: "下载失败")
                                                } finally {
                                                    vm.updateSubtitleBusy(false)
                                                }
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                ) {
                                    Text(
                                        text = hit.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
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
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 搜字幕时的语言选项（显示名 to OpenSubtitles 用的语言标记）。 */
private val LANGUAGES = listOf("中文" to "zh-cn", "英文" to "en", "全部" to "zh-cn,en")
