package com.lovelev.mediadock.core.data.scrape

import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.ScrapeTaskEntity
import com.lovelev.mediadock.data.local.ScrapeTaskState
import com.lovelev.mediadock.data.tmdb.TmdbAuthException
import com.lovelev.mediadock.data.tmdb.TmdbException
import com.lovelev.mediadock.data.tmdb.TmdbMatch
import com.lovelev.mediadock.data.tmdb.TmdbSecret
import kotlinx.coroutines.delay

/** TMDB 匹配能力；用假实现可测 Worker 逻辑。 */
fun interface ScrapeMatcher {
    suspend fun match(title: String, year: Int?, isTv: Boolean, secret: TmdbSecret): TmdbMatch?
}

/** 每日预算；返回 false 表示已达上限，Worker 应暂停。 */
fun interface ScrapeBudget {
    suspend fun tryConsume(): Boolean
}

data class ScrapeLimits(
    val batchSize: Int = 4,
    val requestIntervalMs: Long = 250,
    val maxTasksPerRun: Int = 500,
    val maxBackoffMs: Long = 24 * 60 * 60 * 1000L,
)

data class ScrapeProgress(val done: Int, val total: Int, val matched: Int)

sealed interface ScrapeOutcome {
    data class Finished(val processed: Int, val matched: Int, val failed: Int) : ScrapeOutcome

    /** 凭据无效：整轮暂停，剩余任务保持 PENDING。 */
    data class NeedsSecret(val message: String) : ScrapeOutcome

    data class BudgetExhausted(val processed: Int, val matched: Int, val failed: Int) : ScrapeOutcome
}

/**
 * 持久刮削引擎（Phase 1 E1.2）：领取到期任务 → TMDB 匹配 → 写回作品。
 *
 * - 失败只影响单条，按 `next_attempt_at` 指数退避；凭据错误整轮停止且不重试。
 * - 每条任务先扣减当日预算；预算耗尽暂停，剩余任务保留。
 * - 任务幂等：进程死亡后仍为 PENDING，下次重新执行（可能重复一次 TMDB 请求）。
 */
class ScrapeEngine(
    private val db: MediaDatabase,
    private val store: MediaLibraryStore,
    private val matcher: ScrapeMatcher,
    private val budget: ScrapeBudget,
    private val limits: ScrapeLimits = ScrapeLimits(),
) {

    suspend fun run(
        secret: TmdbSecret,
        onProgress: suspend (ScrapeProgress) -> Unit = {},
    ): ScrapeOutcome {
        val dao = db.scrapeTaskDao()
        dao.resetRunning()

        val now = System.currentTimeMillis()
        val total = dao.countDue(now)
        if (total == 0) return ScrapeOutcome.Finished(0, 0, 0)

        // 每个任务每轮只处理一次：失败后即使退避到期也不在同轮重复请求。
        val attempted = HashSet<String>()
        val outcomeById = HashMap<String, Boolean>()
        var matched = 0
        var failed = 0
        onProgress(ScrapeProgress(0, total, 0))

        while (attempted.size < limits.maxTasksPerRun) {
            val batch = dao.dueTasks(System.currentTimeMillis(), limits.batchSize)
                .filter { it.id !in attempted }
            if (batch.isEmpty()) break

            for (task in batch) {
                if (attempted.size >= limits.maxTasksPerRun) break
                attempted += task.id
                val done = attempted.size
                if (!budget.tryConsume()) {
                    return ScrapeOutcome.BudgetExhausted(done - 1, matched, failed)
                }
                val work = db.writeDao().workById(task.contentId)
                if (work == null) {
                    dao.deleteForWork(task.contentId)
                    onProgress(ScrapeProgress(done, total, matched))
                    continue
                }

                val match = try {
                    matcher.match(work.title, work.year, work.type == "tv", secret)
                } catch (e: TmdbAuthException) {
                    return ScrapeOutcome.NeedsSecret(e.message ?: "TMDB 凭据无效")
                } catch (e: TmdbException) {
                    markRetry(dao, task, e.message ?: "刮削失败")
                    if (outcomeById[task.id] == null) failed++
                    outcomeById[task.id] = false
                    onProgress(ScrapeProgress(done, total, matched))
                    delay(limits.requestIntervalMs)
                    continue
                }

                val now2 = System.currentTimeMillis()
                if (match == null) {
                    store.applyScrapeToWork(
                        workId = work.id,
                        tmdbId = null,
                        title = null,
                        year = null,
                        posterPath = null,
                        backdropPath = null,
                        overview = null,
                        rating = null,
                        matched = false,
                        scrapedAt = now2,
                    )
                    markRetry(dao, task, "未匹配")
                    if (outcomeById[task.id] == null) failed++
                    outcomeById[task.id] = false
                } else {
                    store.applyScrapeToWork(
                        workId = work.id,
                        tmdbId = match.tmdbId,
                        title = match.title,
                        year = match.year,
                        posterPath = match.posterPath,
                        backdropPath = match.backdropPath,
                        overview = match.overview,
                        rating = match.rating,
                        matched = true,
                        scrapedAt = now2,
                    )
                    dao.update(
                        task.copy(
                            state = ScrapeTaskState.DONE,
                            attempts = 0,
                            nextAttemptAt = 0,
                            lastError = null,
                            updatedAt = now2,
                        ),
                    )
                    if (outcomeById[task.id] == false) failed--
                    matched++
                    outcomeById[task.id] = true
                }
                onProgress(ScrapeProgress(attempted.size, total, matched))
                delay(limits.requestIntervalMs)
            }
        }
        return ScrapeOutcome.Finished(attempted.size, matched, failed)
    }

    private suspend fun markRetry(
        dao: com.lovelev.mediadock.data.local.ScrapeTaskDao,
        task: ScrapeTaskEntity,
        error: String,
    ) {
        val attempts = task.attempts + 1
        val backoff = (60_000L shl (attempts - 1).coerceIn(0, 10))
            .coerceAtMost(limits.maxBackoffMs)
        dao.update(
            task.copy(
                state = ScrapeTaskState.PENDING,
                attempts = attempts,
                nextAttemptAt = System.currentTimeMillis() + backoff,
                lastError = error,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
