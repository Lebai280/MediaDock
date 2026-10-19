package com.lovelev.mediadock.data.local

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.lovelev.mediadock.core.model.MediaTypes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 本地扫到的一个视频文件。 */
data class LocalFile(
    /** SAF 是相对目录树根的路径；MediaStore 直接是它自己那个 content:// URI。 */
    val key: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    /** 所在目录名。用户的片子很多是「目录带中文片名、文件只有英文名」，片名解析要用它。 */
    val folderName: String = "",
)

/**
 * 遍历用户选中的那棵 SAF 目录树，挑出视频文件。
 *
 * **别改回 `DocumentFile`。**
 * `DocumentFile.listFiles()` 返回的对象，每个属性访问（`isDirectory` / `name` / `length()` /
 * `lastModified()`）**都是一次独立的 ContentResolver 查询** —— 一个目录 100 个文件就是
 * 约 400 次 IPC。初始版本就是这么写的，用户实测「扫描很久」。
 * 现在每个目录**只发一次**查询，把需要的列一次取回来。
 *
 * 与 WebDAV 那个扫描器的另一点不同：**不用控速**。那条「顺序、绝不并发」的硬约束是给
 * OpenList 的（登录失败会按来源 IP 计数，高频请求连累所有人），本地没这回事。
 */
class LocalScanner(private val context: Context) {

    suspend fun scan(
        treeUri: Uri,
        maxDirectories: Int = MAX_DIRECTORIES,
        maxFiles: Int = MAX_FILES,
        onProgress: (directories: Int, files: Int) -> Unit = { _, _ -> },
    ): ScanOutcome = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val treeDocumentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }
            .getOrNull()
            ?: return@withContext ScanOutcome(emptyList(), truncated = false, readable = false)

        val found = ArrayList<LocalFile>()
        val queue = ArrayDeque<String>().apply { add(treeDocumentId) }
        var directories = 0
        var truncated = false
        var anyQueryFailed = false

        while (queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (directories >= maxDirectories) {
                truncated = true
                break
            }
            val documentId = queue.removeFirst()
            directories++

            val children = runCatching {
                resolver.query(
                    DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId),
                    PROJECTION,
                    null,
                    null,
                    null,
                )
            }.getOrElse {
                // 读不动某个目录（授权被收回一部分、目录刚被删）。**记下来** ——
                // 直接当「空目录」跳过的话，界面会显示成「这个文件夹是空的」，
                // 那就把「没权限」说成了「没有文件」，用户会一直找不到原因。
                anyQueryFailed = true
                null
            }
            if (children == null) continue

            children.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.stringAt(COLUMN_ID) ?: continue
                    val name = cursor.stringAt(COLUMN_NAME) ?: continue
                    when {
                        cursor.stringAt(COLUMN_MIME) == DocumentsContract.Document.MIME_TYPE_DIR ->
                            queue.add(id)

                        MediaTypes.isVideoFile(name) -> if (found.size < maxFiles) {
                            found += LocalFile(
                                // 子项的 document id 是「树根 id + '/' + 相对路径」，
                                // 所以相对路径就是去掉前缀 —— 与 LocalMedia.childDocumentId 互为逆运算
                                key = id.removePrefix("$treeDocumentId/"),
                                name = name,
                                folderName = id.removePrefix("$treeDocumentId/")
                                    .substringBeforeLast('/', "")
                                    .substringAfterLast('/'),
                                sizeBytes = cursor.longAt(COLUMN_SIZE),
                                lastModified = cursor.longAt(COLUMN_MODIFIED),
                            )
                        }
                    }
                }
            }
            onProgress(directories, found.size)
        }

        if (found.size >= maxFiles) truncated = true
        // 一个都没扫到、而且有目录读不动 → 当「读不了」处理，别说成空文件夹
        if (found.isEmpty() && anyQueryFailed) {
            return@withContext ScanOutcome(emptyList(), truncated = false, readable = false)
        }
        ScanOutcome(found, truncated = truncated, readable = true)
    }

    /**
     * @param readable false = 目录树无法访问（授权被撤销或目录被删除）。
     *   与「扫描到 0 个文件」语义不同，界面应分别提示。
     */
    data class ScanOutcome(
        val files: List<LocalFile>,
        val truncated: Boolean,
        val readable: Boolean,
    )

    private fun Cursor.stringAt(index: Int): String? =
        if (isNull(index)) null else getString(index)

    private fun Cursor.longAt(index: Int): Long =
        if (isNull(index)) 0L else getLong(index)

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        const val COLUMN_ID = 0
        const val COLUMN_NAME = 1
        const val COLUMN_MIME = 2
        const val COLUMN_SIZE = 3
        const val COLUMN_MODIFIED = 4

        // 本地遍历快，上限给得比 WebDAV 那边宽松
        const val MAX_DIRECTORIES = 4000
        const val MAX_FILES = 20000
    }
}
