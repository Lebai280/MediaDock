package com.lovelev.mediadock.core.data.scan

import com.lovelev.mediadock.core.model.MediaTypes
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.core.model.TitleParser
import com.lovelev.mediadock.data.local.DirectoryCacheEntity
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.ScanMode
import com.lovelev.mediadock.data.local.ScanQueueEntity
import com.lovelev.mediadock.data.local.ScanRunEntity
import com.lovelev.mediadock.data.local.ScanStatus
import com.lovelev.mediadock.data.webdav.WebDavException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** 扫描器看到的远程条目；与具体协议解耦，便于用假实现测试。 */
data class RemoteEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    val lastModified: String?,
    val etag: String? = null,
)

/** 一次目录列举结果；[notModified] 表示缓存命中（304/412）。 */
data class DirectoryListing(
    val entries: List<RemoteEntry> = emptyList(),
    val etag: String? = null,
    val lastModified: String? = null,
    val notModified: Boolean = false,
)

fun interface DirectoryLister {
    suspend fun list(remoteKey: String, etag: String?, lastModified: String?): DirectoryListing
}

data class ScanLimits(
    val maxDirectories: Int = 200_000,
    val maxFiles: Int = 200_000,
    val requestIntervalMs: Long = 120,
)

data class ScanProgress(val directories: Int, val files: Int, val queued: Int)

sealed interface ScanOutcome {
    data class Finished(val directories: Int, val files: Int, val truncated: Boolean) : ScanOutcome

    /** 401/429：源被封锁，**不得重试**。 */
    data class Blocked(val message: String) : ScanOutcome

    data class Failed(val message: String) : ScanOutcome
}

/**
 * Phase 1 增量扫描引擎：持久 BFS 队列 + 目录 ETag 缓存 + 目录级差异写入。
 *
 * - 进程死亡后由 WorkManager 重新入队；本类按 `scan_runs` + `scan_queue` 续跑。
 * - 401/429 立即停止并写 `sources.health=BLOCKED`，绝不重试。
 * - 目录未变化（304/412）直接跳过子树。
 * - FULL 扫描收尾时只把本轮未访问到的文件标缺失。
 */
class ScanEngine(
    private val db: MediaDatabase,
    private val store: MediaLibraryStore,
    private val limits: ScanLimits = ScanLimits(),
) {

    suspend fun run(
        sourceId: SourceId,
        mode: String,
        lister: DirectoryLister,
        /** 源内相对键 → 跨源唯一路径；旧源与本地直接用相对键。 */
        legacyPathFor: (String) -> String = { it },
        onProgress: suspend (ScanProgress) -> Unit = {},
    ): ScanOutcome {
        val dao = db.scanDao()
        var existing = dao.activeRun(sourceId.value)
        if (existing != null && mode == ScanMode.FULL && existing.mode != ScanMode.FULL) {
            dao.finishRun(
                existing.id, ScanStatus.CANCELLED, System.currentTimeMillis(),
                existing.dirs, existing.files, null,
            )
            existing = null
        }
        val scanRun = existing ?: run {
            val id = dao.insertRun(
                ScanRunEntity(
                    sourceId = sourceId.value,
                    mode = mode,
                    status = ScanStatus.RUNNING,
                    startedAt = System.currentTimeMillis(),
                ),
            )
            dao.clearQueue(sourceId.value)
            if (mode == ScanMode.FULL) dao.clearDirectories(sourceId.value)
            dao.enqueue(ScanQueueEntity(sourceId.value, id, "", 0))
            dao.run(id)!!
        }

        var dirs = scanRun.dirs
        var files = scanRun.files
        try {
            while (true) {
                val next = dao.nextQueued(sourceId.value, scanRun.id) ?: break
                if (dirs >= limits.maxDirectories || files >= limits.maxFiles) {
                    finish(dao, scanRun, ScanStatus.TRUNCATED, dirs, files, null)
                    return ScanOutcome.Finished(dirs, files, truncated = true)
                }

                val cached = dao.directory(sourceId.value, next.remoteKey)
                val useCache = scanRun.mode == ScanMode.INCREMENTAL && cached != null &&
                    (!cached.etag.isNullOrBlank() || !cached.lastModified.isNullOrBlank())
                val listing = try {
                    lister.list(
                        next.remoteKey,
                        if (useCache) cached?.etag else null,
                        if (useCache) cached?.lastModified else null,
                    )
                } catch (e: WebDavException.NotFound) {
                    // 扫描途中目录被改名/删除：跳过；根目录缺失则整次失败。
                    if (next.remoteKey.isEmpty()) throw e
                    dao.dequeue(sourceId.value, next.remoteKey)
                    dirs++
                    continue
                } catch (e: WebDavException.Unauthorized) {
                    dao.setSourceHealth(sourceId.value, "BLOCKED")
                    finish(dao, scanRun, ScanStatus.BLOCKED, dirs, files, e.userMessage)
                    return ScanOutcome.Blocked(e.userMessage)
                } catch (e: WebDavException.Locked) {
                    dao.setSourceHealth(sourceId.value, "BLOCKED")
                    finish(dao, scanRun, ScanStatus.BLOCKED, dirs, files, e.userMessage)
                    return ScanOutcome.Blocked(e.userMessage)
                } catch (e: WebDavException.Network) {
                    throw e // Worker 以退避重试；队列与 run 保留，下次续跑
                } catch (e: WebDavException.Http) {
                    finish(dao, scanRun, ScanStatus.FAILED, dirs, files, e.userMessage)
                    return ScanOutcome.Failed(e.userMessage)
                }

                if (listing.notModified) {
                    dao.dequeue(sourceId.value, next.remoteKey)
                    dirs++
                    maybeProgress(dao, scanRun.id, dirs, files, onProgress)
                    continue
                }

                // OpenList 不返回集合 ETag、也不支持 304；用 Last-Modified + 子项数判断
                // 目录未变化，跳过子树（架构 3.4 的目录缓存语义）。
                val unchanged = useCache && cached != null &&
                    cached.childCount == listing.entries.size &&
                    (
                        (!cached.etag.isNullOrBlank() && cached.etag == listing.etag) ||
                            (!cached.lastModified.isNullOrBlank() &&
                                cached.lastModified == listing.lastModified)
                        )
                if (unchanged) {
                    dao.upsertDirectory(
                        cached!!.copy(lastScanRunId = scanRun.id),
                    )
                    dao.dequeue(sourceId.value, next.remoteKey)
                    dirs++
                    maybeProgress(dao, scanRun.id, dirs, files, onProgress)
                    if (limits.requestIntervalMs > 0) delay(limits.requestIntervalMs)
                    continue
                }

                val videoFiles = listing.entries.asSequence()
                    .filter { !it.isDirectory && !isIgnoredDirectory(it.name) }
                    .filter { MediaTypes.isVideoFile(it.name) }
                    .filter { (it.sizeBytes ?: 0L) > 0L }
                    .map { entry ->
                        val file = entry.toScannedFile()
                        file.copy(legacyPath = legacyPathFor(file.path))
                    }
                    .toList()
                store.applyDirectoryListing(sourceId, next.remoteKey, videoFiles, scanRun.id)

                listing.entries.asSequence()
                    .filter { it.isDirectory && !isIgnoredDirectory(it.name) }
                    .forEach {
                        dao.enqueue(
                            ScanQueueEntity(
                                sourceId = sourceId.value,
                                scanRunId = scanRun.id,
                                remoteKey = it.path,
                                depth = next.depth + 1,
                            ),
                        )
                    }

                dao.upsertDirectory(
                    DirectoryCacheEntity(
                        sourceId = sourceId.value,
                        remoteKey = next.remoteKey,
                        etag = listing.etag,
                        lastModified = listing.lastModified,
                        lastScanRunId = scanRun.id,
                        childCount = listing.entries.size,
                    ),
                )
                dao.dequeue(sourceId.value, next.remoteKey)
                dirs++
                files += videoFiles.size
                maybeProgress(dao, scanRun.id, dirs, files, onProgress)
                if (limits.requestIntervalMs > 0) delay(limits.requestIntervalMs)
            }

            if (scanRun.mode == ScanMode.FULL) store.finishFullScan(sourceId, scanRun.id)
            finish(dao, scanRun, ScanStatus.DONE, dirs, files, null)
            dao.updateSourceHealth(sourceId.value, "OK", System.currentTimeMillis())
            return ScanOutcome.Finished(dirs, files, truncated = false)
        } catch (e: CancellationException) {
            // 保留 RUNNING + 队列，WorkManager 重启后继续。
            throw e
        }
    }

    private suspend fun maybeProgress(
        dao: com.lovelev.mediadock.data.local.ScanDao,
        runId: Long,
        dirs: Int,
        files: Int,
        onProgress: suspend (ScanProgress) -> Unit,
    ) {
        if (dirs % 5 == 0) {
            dao.updateProgress(runId, dirs, files)
            onProgress(ScanProgress(dirs, files, queued = 0))
        }
    }

    private suspend fun finish(
        dao: com.lovelev.mediadock.data.local.ScanDao,
        run: ScanRunEntity,
        status: String,
        dirs: Int,
        files: Int,
        error: String?,
    ) {
        dao.finishRun(run.id, status, System.currentTimeMillis(), dirs, files, error)
    }

    private fun RemoteEntry.toScannedFile(): MediaLibraryStore.ScannedFile {
        val folderName = path.substringBeforeLast('/', "").substringAfterLast('/')
        val seasonFromPath = TitleParser.seasonOfPath(path)
        val parsed = TitleParser.parse(
            fileName = name,
            folderName = folderName,
            inSeasonDir = seasonFromPath != null,
        )
        return MediaLibraryStore.ScannedFile(
            path = path,
            name = name,
            sizeBytes = sizeBytes,
            mtime = lastModified,
            title = parsed.title,
            year = parsed.year,
            season = parsed.season ?: seasonFromPath,
            episode = parsed.episode,
        )
    }
}

/** 默认忽略的目录/文件：隐藏目录、群晖缩略图、回收站、零字节文件（在引擎里判定大小）。 */
internal fun isIgnoredDirectory(name: String): Boolean {
    val lower = name.lowercase()
    return name.startsWith(".") ||
        lower == "@eadir" ||
        lower == "#recycle" ||
        lower == "lost+found" ||
        lower == ".trash"
}
