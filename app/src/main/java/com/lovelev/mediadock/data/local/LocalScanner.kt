package com.lovelev.mediadock.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.lovelev.mediadock.data.MediaTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 本地扫到的一个视频文件。 */
data class LocalFile(
    val relativePath: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * 遍历用户选中的那棵目录树，挑出视频文件。
 *
 * 与 WebDAV 那个扫描器的两点不同：
 *
 *  - **不用控速**。那个的「顺序、一个目录一个请求、绝不并发」是硬约束（OpenList 的登录失败
 *    会按来源 IP 计数，高频请求会连累所有人）。本地遍历没有这回事，所以快得多。
 *  - 走的是 SAF 的 [DocumentFile]。拿到的是「相对目录树根的路径」——
 *    **不是** `content://` URI，原因见 [LocalMedia] 的注释。
 */
class LocalScanner(private val context: Context) {

    suspend fun scan(
        treeUri: Uri,
        maxDirectories: Int = MAX_DIRECTORIES,
        maxFiles: Int = MAX_FILES,
        onProgress: (directories: Int, files: Int) -> Unit = { _, _ -> },
    ): ScanOutcome = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return@withContext ScanOutcome(files = emptyList(), truncated = false, readable = false)

        val found = ArrayList<LocalFile>()
        // 广度优先。队列里放 (目录, 它相对树根的路径)
        val queue = ArrayDeque<Pair<DocumentFile, String>>()
        queue.add(root to "")
        var directories = 0
        var truncated = false

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (directories >= maxDirectories) {
                truncated = true
                break
            }
            val (dir, dirPath) = queue.removeFirst()
            directories++

            val children = try {
                dir.listFiles()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 单个目录读不动（权限被收回、目录被删）不该让整次扫描失败
                continue
            }

            for (child in children) {
                val name = child.name ?: continue
                val childPath = if (dirPath.isEmpty()) name else "$dirPath/$name"
                when {
                    child.isDirectory -> queue.add(child to childPath)
                    child.isFile && MediaTypes.isVideoFile(name) -> {
                        if (found.size < maxFiles) {
                            found += LocalFile(
                                relativePath = childPath,
                                name = name,
                                sizeBytes = child.length(),
                                lastModified = child.lastModified(),
                            )
                        }
                    }
                }
            }
            onProgress(directories, found.size)
        }

        if (found.size >= maxFiles) truncated = true
        ScanOutcome(files = found, truncated = truncated, readable = true)
    }

    /**
     * @param readable false = 那棵树打不开了（授权被撤销、目录被删）。
     *   和「扫到 0 个文件」是两回事，界面要分开说。
     */
    data class ScanOutcome(
        val files: List<LocalFile>,
        val truncated: Boolean,
        val readable: Boolean,
    )

    private companion object {
        // 本地遍历快，上限给得比 WebDAV 那边宽松
        const val MAX_DIRECTORIES = 4000
        const val MAX_FILES = 20000
    }
}
