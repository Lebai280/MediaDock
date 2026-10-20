package com.lovelev.mediadock.data.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lovelev.mediadock.MediaDockApp
import com.lovelev.mediadock.R
import com.lovelev.mediadock.core.data.scan.ScanEngine
import com.lovelev.mediadock.core.data.scan.ScanOutcome
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.ScanMode
import com.lovelev.mediadock.data.webdav.WebDavException
import kotlinx.coroutines.CancellationException

/**
 * 媒体库扫描 Worker：唯一工作名 `scan:<sourceId>`，dataSync 前台类型。
 *
 * 扫描状态与队列都在数据库里，进程被杀后 WorkManager 会重新入队并按 `scan_queue` 续跑。
 * 401/429 以失败结束（不重试）；网络故障交给 WorkManager 退避重试。
 */
class ScanWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return Result.failure()
        val mode = inputData.getString(KEY_MODE) ?: ScanMode.INCREMENTAL
        val app = applicationContext as MediaDockApp
        val container = app.container

        runCatching { setForeground(foregroundInfo(0, 0)) }

        val source = container.sources.get(sourceId)
        if (source == null || !source.credentialOk) {
            return Result.failure(
                workDataOf(KEY_BLOCKED to false, KEY_MESSAGE to "数据源不可用，请重新添加"),
            )
        }
        if (source.isCatalogSource) {
            return runCatalogSync(container, source)
        }

        val lister: com.lovelev.mediadock.core.data.scan.DirectoryLister = when (source.type) {
            com.lovelev.mediadock.core.model.SourceType.WEBDAV ->
                WebDavDirectoryLister(container.webDavClient) { source }
            com.lovelev.mediadock.core.model.SourceType.SMB ->
                com.lovelev.mediadock.data.smb.SmbDirectoryLister { source }
            else -> return Result.failure(
                workDataOf(KEY_BLOCKED to false, KEY_MESSAGE to "该数据源类型暂不支持扫描"),
            )
        }
        val engine = ScanEngine(container.database, container.libraryStore)

        return try {
            val outcome = engine.run(
                sourceId = SourceId(sourceId),
                mode = mode,
                lister = lister,
                legacyPathFor = { key ->
                    com.lovelev.mediadock.core.model.SourcePaths.qualify(SourceId(sourceId), key)
                },
            ) { progress ->
                setProgress(
                    workDataOf(
                        KEY_DIRS to progress.directories,
                        KEY_FILES to progress.files,
                    ),
                )
                runCatching {
                    setForeground(foregroundInfo(progress.directories, progress.files))
                }
            }
            when (outcome) {
                is ScanOutcome.Finished -> Result.success(
                    workDataOf(
                        KEY_DIRS to outcome.directories,
                        KEY_FILES to outcome.files,
                        KEY_TRUNCATED to outcome.truncated,
                    ),
                )
                is ScanOutcome.Blocked -> Result.failure(
                    workDataOf(KEY_BLOCKED to true, KEY_MESSAGE to outcome.message),
                )
                is ScanOutcome.Failed -> Result.failure(
                    workDataOf(KEY_BLOCKED to false, KEY_MESSAGE to outcome.message),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: WebDavException.Network) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_BLOCKED to false, KEY_MESSAGE to (e.message ?: "扫描失败")))
        } finally {
            (lister as? java.io.Closeable)?.close()
        }
    }

    /** Emby / Jellyfin：分页同步条目，进度按条目数上报。 */
    private suspend fun runCatalogSync(
        container: com.lovelev.mediadock.di.AppContainer,
        source: com.lovelev.mediadock.data.source.SourceConfig,
    ): Result {
        val engine = CatalogSyncEngine(
            container.libraryStore,
            com.lovelev.mediadock.data.emby.EmbyClient(container.mediaOkHttpClient),
        )
        return try {
            val count = engine.run(source) { files ->
                setProgress(workDataOf(KEY_DIRS to 0, KEY_FILES to files))
                runCatching { setForeground(foregroundInfo(0, files)) }
            }
            Result.success(
                workDataOf(KEY_DIRS to 0, KEY_FILES to count, KEY_TRUNCATED to false),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.lovelev.mediadock.data.emby.EmbyClient.Failure.Auth) {
            Result.failure(workDataOf(KEY_BLOCKED to true, KEY_MESSAGE to (e.message ?: "认证失败")))
        } catch (e: com.lovelev.mediadock.data.emby.EmbyClient.Failure.Network) {
            Result.retry()
        } catch (e: Exception) {
            Result.failure(workDataOf(KEY_BLOCKED to false, KEY_MESSAGE to (e.message ?: "同步失败")))
        }
    }

    private fun foregroundInfo(dirs: Int, files: Int): ForegroundInfo {
        val context = applicationContext
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "媒体库扫描",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "扫描数据源时显示进度" },
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("正在扫描媒体库")
            .setContentText("已扫描 $dirs 个目录，$files 个视频")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_MODE = "mode"
        const val KEY_DIRS = "dirs"
        const val KEY_FILES = "files"
        const val KEY_TRUNCATED = "truncated"
        const val KEY_BLOCKED = "blocked"
        const val KEY_MESSAGE = "message"

        private const val CHANNEL_ID = "media_scan"
        private const val NOTIFICATION_ID = 4201
    }
}
