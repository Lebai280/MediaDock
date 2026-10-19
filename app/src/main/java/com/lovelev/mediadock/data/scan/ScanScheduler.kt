package com.lovelev.mediadock.data.scan

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.data.local.ScanMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 扫描调度：唯一工作名 `scan:<sourceId>`；重复点击扫描使用 KEEP，不会并发跑两份。 */
class ScanScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun start(sourceId: SourceId, mode: String = ScanMode.INCREMENTAL) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(
                workDataOf(
                    ScanWorker.KEY_SOURCE_ID to sourceId.value,
                    ScanWorker.KEY_MODE to mode,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(workName(sourceId))
            .build()
        workManager.enqueueUniqueWork(workName(sourceId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(sourceId: SourceId) {
        workManager.cancelUniqueWork(workName(sourceId))
    }

    fun observe(sourceId: SourceId): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(sourceId))
            .map { infos -> infos.maxByOrNull { it.id } }

    private fun workName(sourceId: SourceId): String = "scan:${sourceId.value}"
}
