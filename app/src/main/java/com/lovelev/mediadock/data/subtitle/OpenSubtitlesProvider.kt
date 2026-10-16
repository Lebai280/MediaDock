package com.lovelev.mediadock.data.subtitle

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream

/** OpenSubtitles 的两段凭据：Api-Key（搜用）+ 账号密码（下载用）。 */
data class OpenSubtitlesCredentials(
    val apiKey: String,
    val username: String,
    val password: String,
)

/**
 * OpenSubtitles.com 的 REST 实现。
 *
 * - 搜索只要 **Api-Key**（在站点注册应用即得）；下载要**账号密码**换一个 Bearer token。
 * - 站点有下载额度：超了回 406 / 429 —— 当「额度」处理，**不重试**。
 * - token 过期（下载 401）时**重新登录一次**再试一遍（只一次，不做无脑重试）。
 *
 * ⚠️ 这套是照公开文档的形状写的，解析用 `ignoreUnknownKeys` 宽容处理。**本机没有 key，跑不通真实请求**，
 * 所以字段名以站点为准：站点哪天改了形状，改下面这几个 DTO 就行，别动上面的逻辑。
 */
class OpenSubtitlesProvider(
    private val client: OkHttpClient,
    private val credentials: suspend () -> OpenSubtitlesCredentials?,
) : SubtitleProvider {

    override val name = "OpenSubtitles"

    /** 登录换来的 Bearer，缓存在内存（不落盘）。 */
    private var token: String? = null

    override suspend fun isConfigured(): Boolean = credentials()?.let {
        it.apiKey.isNotBlank() && it.username.isNotBlank() && it.password.isNotBlank()
    } == true

    override suspend fun search(
        query: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        languages: String,
    ): List<SubtitleHit> = withContext(Dispatchers.IO) {
        val cred = credentials() ?: throw SubtitleAuthException("还没填 OpenSubtitles 凭据，去设置里填一下")

        val url = BASE.toHttpUrl().newBuilder().addPathSegments("subtitles")
            .addQueryParameter("query", query)
            .addQueryParameter("languages", languages)
        if (season != null || episode != null) {
            url.addQueryParameter("type", "episode")
            season?.let { url.addQueryParameter("season_number", it.toString()) }
            episode?.let { url.addQueryParameter("episode_number", it.toString()) }
        } else {
            url.addQueryParameter("type", "movie")
            year?.let { url.addQueryParameter("year", it.toString()) }
        }

        val request = Request.Builder()
            .url(url.build())
            .header("Api-Key", cred.apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()

        val body = execute(request)
        val parsed = try {
            JSON.decodeFromString<SearchResponse>(body)
        } catch (e: Exception) {
            throw SubtitleException("OpenSubtitles 的响应看不懂：${e.message}", e)
        }

        parsed.data.mapNotNull { item ->
            val attrs = item.attributes ?: return@mapNotNull null
            val fileId = attrs.files.firstOrNull()?.file_id ?: return@mapNotNull null
            val feature = attrs.feature_details ?: return@mapNotNull null
            val name = feature.movie_name ?: feature.title ?: return@mapNotNull null
            SubtitleHit(
                id = fileId.toString(),
                label = listOfNotNull(name, feature.year?.toString()).joinToString(" "),
                language = attrs.language.orEmpty(),
                downloads = attrs.download_count,
                release = attrs.release,
            )
        }.sortedByDescending { it.downloads ?: 0 }
    }

    override suspend fun download(hit: SubtitleHit): SubtitleFile = withContext(Dispatchers.IO) {
        val cred = credentials() ?: throw SubtitleAuthException("还没填 OpenSubtitles 凭据，去设置里填一下")
        val fileId = hit.id.toIntOrNull() ?: throw SubtitleException("这条结果的 id 不对")

        // token 过期时重新登录一次再试一遍（只一次）
        val bearer = token ?: login(cred).also { token = it }
        val parsed = try {
            requestDownload(fileId, cred.apiKey, bearer)
        } catch (e: SubtitleAuthException) {
            token = null
            val fresh = login(cred).also { token = it }
            requestDownload(fileId, cred.apiKey, fresh)
        }

        val link = parsed.link ?: throw SubtitleException(parsed.message ?: "OpenSubtitles 没给下载地址")
        val bytes = fetchBytes(link)
        SubtitleFile(
            fileName = parsed.file_name ?: hit.release?.let { "$it.srt" } ?: "subtitle.srt",
            bytes = bytes,
        )
    }

    private fun login(cred: OpenSubtitlesCredentials): String {
        val payload = JSON.encodeToString(LoginRequest(cred.username, cred.password))
        val body = post("login", payload, cred.apiKey, bearer = null)
        val parsed = try {
            JSON.decodeFromString<LoginResponse>(body)
        } catch (e: Exception) {
            throw SubtitleException("OpenSubtitles 登录响应看不懂：${e.message}", e)
        }
        return parsed.token ?: throw SubtitleAuthException("OpenSubtitles 登录失败，检查账号密码")
    }

    private fun requestDownload(fileId: Int, apiKey: String, bearer: String): DownloadResponse {
        val body = post("download", """{"file_id":$fileId,"sub_format":"srt"}""", apiKey, bearer)
        return try {
            JSON.decodeFromString<DownloadResponse>(body)
        } catch (e: Exception) {
            throw SubtitleException("OpenSubtitles 的下载响应看不懂：${e.message}", e)
        }
    }

    /** 下载直链一般是 UTF-8 的 srt；万一是 gz 就解一下。 */
    private fun fetchBytes(link: String): ByteArray {
        val response = try {
            client.newCall(Request.Builder().url(link).header("User-Agent", USER_AGENT).build())
                .execute()
        } catch (e: IOException) {
            throw SubtitleException("下载字幕失败：${e.message}", e)
        }
        response.use { resp ->
            if (!resp.isSuccessful) throw SubtitleException("下载字幕失败：HTTP ${resp.code}")
            val raw = resp.body.bytes()
            val gzipped = raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()
            if (!gzipped) return raw
            return try {
                GZIPInputStream(raw.inputStream()).use { input ->
                    val out = ByteArrayOutputStream()
                    input.copyTo(out)
                    out.toByteArray()
                }
            } catch (e: Exception) {
                throw SubtitleException("字幕解压失败：${e.message}", e)
            }
        }
    }

    private fun post(path: String, payload: String, apiKey: String, bearer: String?): String {
        val builder = Request.Builder()
            .url(BASE.toHttpUrl().newBuilder().addPathSegments(path).build())
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        return execute(builder.build())
    }

    /** 状态码统一在这儿翻成人话：凭据错 → Auth、额度/限流 → Quota，其余 → 普通错误。都不重试。 */
    private fun execute(request: Request): String {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw SubtitleException("连不上 OpenSubtitles：${e.message}", e)
        }
        response.use { resp ->
            val text = resp.body.string()
            return when (resp.code) {
                200, 201 -> text
                401, 403 -> throw SubtitleAuthException("OpenSubtitles 凭据无效，去设置里重新填")
                406 -> throw SubtitleQuotaException(messageOf(text) ?: "OpenSubtitles 今天的下载额度用完了，过会儿再试")
                429 -> throw SubtitleQuotaException(messageOf(text) ?: "请求太频繁了，过会儿再试")
                else -> throw SubtitleException("OpenSubtitles 返回 HTTP ${resp.code}")
            }
        }
    }

    /** 从错误响应里抠一句 message（站点通常给形如 {"message": "..."}）。 */
    private fun messageOf(body: String): String? = runCatching {
        JSON.decodeFromString<ErrorResponse>(body).message?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private companion object {
        const val BASE = "https://api.opensubtitles.com/api/v1"
        const val USER_AGENT = "MediaDock v0.1.0"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class SearchResponse(val data: List<Item> = emptyList())

@Serializable
private data class Item(val id: String = "", val attributes: Attributes? = null)

@Serializable
private data class Attributes(
    val language: String? = null,
    val download_count: Int? = null,
    val release: String? = null,
    val feature_details: FeatureDetails? = null,
    val files: List<FileRef> = emptyList(),
)

@Serializable
private data class FeatureDetails(
    val title: String? = null,
    val movie_name: String? = null,
    val year: Int? = null,
)

@Serializable
private data class FileRef(val file_id: Int? = null, val file_name: String? = null)

@Serializable
private data class LoginRequest(val username: String, val password: String)

@Serializable
private data class LoginResponse(val token: String? = null, val status: Int = 0)

@Serializable
private data class DownloadResponse(
    val link: String? = null,
    val file_name: String? = null,
    val remaining: Int? = null,
    val message: String? = null,
)

@Serializable
private data class ErrorResponse(val message: String? = null)
