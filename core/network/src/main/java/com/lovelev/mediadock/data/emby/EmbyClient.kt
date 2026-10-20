package com.lovelev.mediadock.data.emby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Emby / Jellyfin 的只读客户端：登录、分页拉条目、拼 Direct Play 地址。 */
class EmbyClient(private val client: OkHttpClient) {

    data class Auth(val token: String, val userId: String)

    data class CatalogItem(
        val itemId: String,
        val type: String,
        val name: String,
        val seriesId: String?,
        val seriesName: String?,
        val season: Int?,
        val episode: Int?,
        val year: Int?,
        val tmdbId: Int?,
        val runtimeMs: Long?,
        val overview: String?,
        val rating: Double?,
        val posterUrl: String?,
    )

    sealed class Failure(message: String) : Exception(message) {
        class Auth(message: String) : Failure(message)
        class Network(message: String) : Failure(message)
    }

    /** 用户名密码登录；失败抛 [Failure.Auth]。 */
    suspend fun authenticate(
        baseUrl: String,
        username: String,
        password: String,
    ): Auth = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl)
        val body = """{"Username":${quote(username)},"Pw":${quote(password)}}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$base/Users/AuthenticateByName")
            .header("X-Emby-Authorization", authHeader())
            .post(body)
            .build()
        val json = execute(request, authExpected = true)
        val token = json["AccessToken"]?.jsonPrimitive?.contentOrNull
            ?: throw Failure.Auth("登录响应缺少 AccessToken")
        val userId = json["User"]?.jsonObject?.get("Id")?.jsonPrimitive?.contentOrNull
            ?: throw Failure.Auth("登录响应缺少 User.Id")
        Auth(token, userId)
    }

    /** 拉一页条目（电影 / 剧集 / 分集）。[auth] 为空时用 [apiKey]。 */
    suspend fun items(
        baseUrl: String,
        auth: Auth?,
        apiKey: String?,
        startIndex: Int,
        limit: Int,
    ): List<CatalogItem> = withContext(Dispatchers.IO) {
        val base = normalize(baseUrl)
        val token = auth?.token ?: apiKey ?: throw Failure.Auth("未登录且没有 API Key")
        val userId = auth?.userId
        val url = buildString {
            append(base)
            append(if (userId != null) "/Users/$userId/Items" else "/Items")
            append("?Recursive=true")
            append("&IncludeItemTypes=Movie,Series,Episode")
            append("&Fields=ProviderIds,ProductionYear,RunTimeTicks,SeriesId,SeriesName,")
            append("IndexNumber,ParentIndexNumber,Overview,CommunityRating,ImageTags,SeriesPrimaryImageTag")
            append("&ImageTypeLimit=1&EnableImageTypes=Primary")
            append("&StartIndex=$startIndex&Limit=$limit")
        }
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", token)
            .header("X-Emby-Authorization", authHeader())
            .get()
            .build()
        val root = execute(request, authExpected = false)
        val array = root["Items"]?.jsonArray ?: return@withContext emptyList()
        array.mapNotNull { element ->
            val item = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = item.string("Id") ?: return@mapNotNull null
            val type = item.string("Type") ?: return@mapNotNull null
            val posterTag = item["ImageTags"]?.jsonObject?.get("Primary")?.jsonPrimitive?.contentOrNull
            val seriesTag = item.string("SeriesPrimaryImageTag")
            val posterOwner = if (posterTag != null) id else item.string("SeriesId")
            val posterTagFinal = posterTag ?: seriesTag
            CatalogItem(
                itemId = id,
                type = type,
                name = item.string("Name").orEmpty(),
                seriesId = item.string("SeriesId"),
                seriesName = item.string("SeriesName"),
                season = item["ParentIndexNumber"]?.jsonPrimitive?.intOrNull,
                episode = item["IndexNumber"]?.jsonPrimitive?.intOrNull,
                year = item["ProductionYear"]?.jsonPrimitive?.intOrNull,
                tmdbId = item["ProviderIds"]?.jsonObject?.get("Tmdb")
                    ?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                runtimeMs = item["RunTimeTicks"]?.jsonPrimitive?.longOrNull?.let { it / 10_000 },
                overview = item.string("Overview"),
                rating = item["CommunityRating"]?.jsonPrimitive?.doubleOrNull,
                posterUrl = if (posterOwner != null && posterTagFinal != null) {
                    "$base/Items/$posterOwner/Images/Primary?maxWidth=400&tag=$posterTagFinal"
                } else {
                    null
                },
            )
        }
    }

    /** Direct Play 地址；鉴权头由播放器的多源 DataSource 按源补。 */
    fun streamUrl(baseUrl: String, itemId: String): String =
        "${normalize(baseUrl)}/Videos/$itemId/stream?static=true"

    private fun execute(request: Request, authExpected: Boolean): JsonObject {
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw Failure.Network(e.message ?: "网络错误")
        }
        response.use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                throw Failure.Auth(if (authExpected) "账号或密码不正确" else "登录已失效")
            }
            if (!resp.isSuccessful) {
                throw Failure.Network("服务器返回 HTTP ${resp.code}")
            }
            val body = resp.body.string()
            return runCatching { Json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw Failure.Network("响应格式不正确") }
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun quote(value: String): String = JsonPrimitive(value).toString()

    private fun authHeader(): String =
        "MediaBrowser Client=\"MediaDock\", Device=\"Android\", DeviceId=\"mediadock\", Version=\"1.0\""

    private fun normalize(raw: String): String = raw.trim().trimEnd('/')
}
