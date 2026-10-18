package com.lovelev.mediadock.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * 站点上的版本信息（用户站点根：`/var/www/portal/mediadock/version.json`）。
 *
 * ```json
 * { "versionCode": 2, "versionName": "0.2.0",
 *   "url": "https://lovelev.eu.cc/mediadock/MediaDock-0.2.0.apk",
 *   "notes": "本次更新：…" }
 * ```
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int = 0,
    val versionName: String = "",
    val url: String = "",
    val notes: String? = null,
)

sealed interface UpdateResult {
    data object UpToDate : UpdateResult

    data class Available(val manifest: UpdateManifest) : UpdateResult

    /** [noManifest] = 站点尚未提供版本文件（404），和「连不上」分开说 —— 原因不同，不得合并为同一提示。 */
    data class Failed(val message: String, val noManifest: Boolean) : UpdateResult
}

/**
 * 在线版本检查：GET 一次站点上的静态 JSON，和本机 `BuildConfig.VERSION_CODE` 比。
 *
 * **不重试**（和 WebDAV 那套规矩一致：不做无脑重试）；失败一律返回 [UpdateResult.Failed] 带着原因，
 * 绝不把「查不到」说成「已是最新」。
 */
class UpdateChecker(private val client: OkHttpClient) {

    suspend fun check(currentVersionCode: Int): UpdateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(UPDATE_URL)
            .header("Accept", "application/json")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            return@withContext UpdateResult.Failed("暂时连不上更新地址：${e.message}", noManifest = false)
        }

        response.use { resp ->
            when {
                resp.code == 404 -> UpdateResult.Failed("还没发布过更新信息", noManifest = true)
                !resp.isSuccessful -> UpdateResult.Failed("更新地址返回 HTTP ${resp.code}", noManifest = false)
                else -> {
                    val manifest = parseManifest(resp.body.string())
                        ?: return@withContext UpdateResult.Failed("更新信息看不懂", noManifest = false)
                    when {
                        manifest.versionCode <= 0 || manifest.url.isBlank() ->
                            UpdateResult.Failed("更新信息不完整", noManifest = false)
                        isNewer(currentVersionCode, manifest.versionCode) -> UpdateResult.Available(manifest)
                        else -> UpdateResult.UpToDate
                    }
                }
            }
        }
    }

    companion object {
        /** 版本信息文件的位置。换地方只改这一行（并同步 WORKLOG 里那份发布说明）。 */
        const val UPDATE_URL = "https://lovelev.eu.cc/mediadock/version.json"

        private val JSON = Json { ignoreUnknownKeys = true }

        /** 远端 versionCode **更大**才算有新版本。 */
        fun isNewer(current: Int, remote: Int): Boolean = remote > current

        /** 解析站点上的版本 JSON。抽出来是为了能单测 —— 网络那半截本机跑不了。 */
        internal fun parseManifest(body: String): UpdateManifest? =
            runCatching { JSON.decodeFromString<UpdateManifest>(body) }.getOrNull()
    }
}
