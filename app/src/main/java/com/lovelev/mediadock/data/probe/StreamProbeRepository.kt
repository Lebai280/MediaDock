package com.lovelev.mediadock.data.probe

import android.content.Context
import android.net.Uri
import com.lovelev.mediadock.core.model.ProbeFailure
import com.lovelev.mediadock.core.model.StreamInfo
import com.lovelev.mediadock.data.local.StreamInfoDao
import com.lovelev.mediadock.data.local.StreamInfoEntity
import com.lovelev.mediadock.data.log.AppLog
import com.lovelev.mediadock.data.source.SourceRepository
import com.lovelev.mediadock.player.PlayableUrlResolver
import com.lovelev.mediadock.player.probe.ExtractOutcome
import com.lovelev.mediadock.player.probe.StreamExtractor
import com.lovelev.mediadock.player.probe.StreamInfoCodec
import com.lovelev.mediadock.player.probe.StreamInfoMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 按需探测文件容器头，并把结果缓存在 Room。
 *
 * - 只有详情页/诊断页主动请求才探测，扫描与启动期一律不碰。
 * - 失败返回 UNKNOWN，从不向播放路径抛异常。
 * - 并发上限 2 + single-flight，防止一个页面同时打开多个版本时打爆远程源。
 */
class StreamProbeRepository(
    context: Context,
    private val sources: SourceRepository,
    private val resolver: PlayableUrlResolver,
    private val dao: StreamInfoDao,
    factory: androidx.media3.datasource.DataSource.Factory,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val extractor = StreamExtractor(context, factory)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val semaphore = Semaphore(2)
    private val inFlight = ConcurrentHashMap<String, Deferred<StreamInfo>>()
    private val negative = ConcurrentHashMap<String, Negative>()

    private data class Negative(val until: Long, val info: StreamInfo)

    suspend fun cached(path: String, knownSize: Long?): StreamInfo? {
        val entity = dao.byPath(path) ?: return null
        if (!StreamCachePolicy.isFresh(entity, knownSize, clock())) return null
        val info = StreamInfoCodec.decode(entity.payloadJson) ?: run {
            dao.delete(path)
            return null
        }
        if (!StreamCachePolicy.cacheable(info.status)) {
            dao.delete(path)
            return null
        }
        return info
    }

    suspend fun probe(path: String, knownSize: Long? = null): StreamInfo {
        cached(path, knownSize)?.let { return it }
        val key = cacheKey(path, knownSize)
        negative[key]?.takeIf { clock() < it.until }?.let { return it.info }

        val deferred = inFlight.getOrPut(key) {
            scope.async { doProbe(path, knownSize, key) }
        }
        return try {
            deferred.await()
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    suspend fun clear() {
        dao.clear()
        negative.clear()
    }

    private suspend fun doProbe(path: String, knownSize: Long?, key: String): StreamInfo =
        semaphore.withPermit {
            val now = clock()
            try {
                val all = sources.refresh().filter { it.enabled && it.credentialOk }
                val byId = all.associateBy { it.id }
                val url = resolver.resolve(path, byId, byId.keys)
                if (url == null) {
                    return@withPermit rememberFailure(path, knownSize, ProbeFailure.UNRESOLVED, now, key)
                }
                when (val outcome = withContext(Dispatchers.Main.immediate) {
                    extractor.extract(Uri.parse(url), knownSize)
                }) {
                    is ExtractOutcome.Ok -> {
                        val info = StreamInfoMapper.fromRaw(
                            path = path,
                            sizeBytes = knownSize,
                            durationMs = outcome.durationMs,
                            tracks = outcome.tracks,
                            now = now,
                        )
                        persist(info, now)
                        log(info)
                        info
                    }
                    is ExtractOutcome.Failed -> {
                        rememberFailure(path, knownSize, outcome.failure, now, key)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = (e as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)
                    ?.responseCode
                AppLog.d(
                    "probe",
                    "leaf=${path.substringAfterLast('/')} error=${e.javaClass.simpleName}" +
                        (code?.let { " code=$it" } ?: ""),
                )
                rememberFailure(path, knownSize, ProbeFailure.IO, now, key)
            }
        }

    private suspend fun persist(info: StreamInfo, now: Long) {
        if (!StreamCachePolicy.cacheable(info.status)) return
        runCatching {
            dao.upsert(
                StreamInfoEntity(
                    path = info.path,
                    sizeBytes = info.sizeBytes,
                    schemaVersion = StreamCachePolicy.SCHEMA_VERSION,
                    probedAt = now,
                    payloadJson = StreamInfoCodec.encode(info),
                ),
            )
        }
    }

    private fun rememberFailure(
        path: String,
        knownSize: Long?,
        failure: ProbeFailure,
        now: Long,
        key: String,
    ): StreamInfo {
        val info = StreamInfoMapper.unknown(path, knownSize, failure, now)
        negative[key] = Negative(now + StreamCachePolicy.NEGATIVE_TTL_MS, info)
        log(info)
        return info
    }

    private fun log(info: StreamInfo) {
        // 只记叶子文件名，不记 URL、查询串或凭据。
        val leaf = info.path.substringAfterLast('/')
        val hdr = info.video.firstOrNull()?.hdr
        AppLog.d(
            "probe",
            "leaf=$leaf status=${info.status} hdr=$hdr failure=${info.failure} " +
                "video=${info.video.size} audio=${info.audio.size}",
        )
    }

    private fun cacheKey(path: String, size: Long?): String = "$path|${size ?: -1}"
}
