package com.lovelev.mediadock.data.scrape

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lovelev.mediadock.MediaDockApp
import com.lovelev.mediadock.core.data.scrape.ScrapeEngine
import com.lovelev.mediadock.core.data.scrape.ScrapeMatcher
import com.lovelev.mediadock.core.data.scrape.ScrapeOutcome
import com.lovelev.mediadock.data.tmdb.TmdbSecret
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 持久刮削 Worker：唯一工作名 `scrape`，从 `scrape_tasks` 领取到期任务。
 *
 * 单条失败只影响自己（PENDING + 指数退避）；凭据错误整轮停止并交给界面引导设置。
 */
class ScrapeWorker(
    appContext: Context,
    params: androidx.work.WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as MediaDockApp
        val container = app.container
        val secret = container.credentials.loadTmdbSecret()?.let(::TmdbSecret)
            ?: return Result.failure(
                workDataOf(
                    KEY_NEEDS_SECRET to true,
                    KEY_MESSAGE to "还没填写 TMDB 凭据",
                ),
            )

        val engine = ScrapeEngine(
            db = container.database,
            store = container.libraryStore,
            matcher = ScrapeMatcher { title, year, isTv, s ->
                container.metadataScraper.match(title, year, isTv, s)
            },
            budget = container.scrapeBudget,
        )

        return try {
            when (val outcome = engine.run(secret) { progress ->
                setProgress(
                    workDataOf(
                        KEY_DONE to progress.done,
                        KEY_TOTAL to progress.total,
                        KEY_MATCHED to progress.matched,
                    ),
                )
            }) {
                is ScrapeOutcome.Finished -> Result.success(
                    workDataOf(
                        KEY_PROCESSED to outcome.processed,
                        KEY_MATCHED to outcome.matched,
                        KEY_FAILED to outcome.failed,
                    ),
                )
                is ScrapeOutcome.NeedsSecret -> Result.failure(
                    workDataOf(KEY_NEEDS_SECRET to true, KEY_MESSAGE to outcome.message),
                )
                is ScrapeOutcome.BudgetExhausted -> Result.failure(
                    workDataOf(
                        KEY_NEEDS_SECRET to false,
                        KEY_MESSAGE to "今日刮削配额已用完，明天会自动恢复",
                        KEY_PROCESSED to outcome.processed,
                        KEY_MATCHED to outcome.matched,
                        KEY_FAILED to outcome.failed,
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                workDataOf(KEY_NEEDS_SECRET to false, KEY_MESSAGE to (e.message ?: "刮削失败")),
            )
        }
    }

    companion object {
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_MATCHED = "matched"
        const val KEY_FAILED = "failed"
        const val KEY_PROCESSED = "processed"
        const val KEY_NEEDS_SECRET = "needs_secret"
        const val KEY_MESSAGE = "message"
    }
}

/** 刮削调度：唯一工作名 `scrape`，重复点击使用 KEEP。 */
class ScrapeScheduler(context: Context) {

    private val workManager = WorkManager.getInstance(context)

    fun start() {
        val request = OneTimeWorkRequestBuilder<ScrapeWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(WORK_NAME)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    fun observe(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME)
            .map { infos -> infos.maxByOrNull { it.id } }

    private companion object {
        const val WORK_NAME = "scrape"
    }
}
