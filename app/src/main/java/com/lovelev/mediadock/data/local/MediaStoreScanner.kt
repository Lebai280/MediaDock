package com.lovelev.mediadock.data.local

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 扫系统媒体库（MediaStore）。
 *
 * 为什么它比「让用户选文件夹」好用：
 *
 *  - **不用找文件夹**。系统选择器只让用户从存储根目录一层层点，对「我片子在哪儿」没概念的人
 *    是纯粹的负担；这里一次权限弹窗就够。
 *  - **一次查询**。整库一遍 `query` 就回来了，不像 SAF 要逐目录发请求
 *    （遍历相对慢很多，见 [LocalScanner] 的注释）。
 *  - 拿到的是 `content://media/...` URI，**跨重启稳定**，可以直接当播放地址存，
 *    不用像 SAF 那样存「树 + 相对路径」再现拼。
 *
 * 代价：**只看得见系统媒体扫描器已索引的文件**。少数情况会漏（`nomedia` 目录、
 * 被用户从媒体库里排除的文件夹、极新的文件还没被扫到）。那时用「指定文件夹」回退。
 */
class MediaStoreScanner(private val context: Context) {

    suspend fun scan(
        maxFiles: Int = MAX_FILES,
        onProgress: (files: Int) -> Unit = {},
    ): ScanOutcome = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()

        // Android 10+ 用 VOLUME_EXTERNAL；再往下只有 EXTERNAL_CONTENT_URI
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val hasRelativePath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            if (hasRelativePath) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()

        val found = ArrayList<LocalFile>()
        var truncated = false

        val cursor = runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
            )
        }.getOrNull() ?: return@withContext ScanOutcome(emptyList(), truncated = false, readable = false)

        cursor.use { c ->
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                if (found.size >= maxFiles) {
                    truncated = true
                    break
                }
                val id = c.getLong(0)
                val name = if (c.isNull(1)) null else c.getString(1)
                if (name.isNullOrBlank() || !name.substringAfterLast('.', "").isNotEmpty()) continue
                val uri = ContentUris.withAppendedId(collection, id).toString()
                // 目录名留给 TitleParser 用（用户的片子很多是「目录带中文片名、文件只有英文名」）
                val folder = if (hasRelativePath && !c.isNull(4)) {
                    c.getString(4)?.trimEnd('/')?.substringAfterLast('/').orEmpty()
                } else {
                    ""
                }
                found += LocalFile(
                    key = uri,
                    name = name,
                    sizeBytes = if (c.isNull(2)) 0L else c.getLong(2),
                    lastModified = if (c.isNull(3)) 0L else c.getLong(3) * 1000, // MediaStore 给的是秒
                    folderName = folder,
                )
            }
        }
        onProgress(found.size)
        ScanOutcome(found, truncated = truncated, readable = true)
    }

    /** @param readable false = 查询失败（多半是没给 `READ_MEDIA_VIDEO` 权限）。 */
    data class ScanOutcome(
        val files: List<LocalFile>,
        val truncated: Boolean,
        val readable: Boolean,
    )

    private companion object {
        const val MAX_FILES = 20000
    }
}
