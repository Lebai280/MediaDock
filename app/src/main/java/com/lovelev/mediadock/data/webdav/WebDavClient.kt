package com.lovelev.mediadock.data.webdav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Base64

/**
 * 极简 WebDAV 客户端：目前只需要 PROPFIND。
 *
 * 刻意不用 sardine-android（它只发布在 JitPack，本机拉取不稳），自己在 OkHttp 上实现，
 * 好处是能完全掌控鉴权头、超时和重试策略。
 *
 * **重试策略是刻意的：除连接层瞬时故障外不做任何重试。** OkHttp 只在未设置
 * `Authenticator` 时才会把 401 原样返回 —— 这里不设置，所以 401 不会触发内置重试。
 * 这是硬要求：目标服务器的登录失败锁定按来源 IP 计数，重试会连累其他设备。
 */
class WebDavClient(private val client: OkHttpClient) {

    /**
     * 一次 PROPFIND。失败抛 [WebDavException]。**不重试**。
     *
     * @param collection 要列举的集合。
     * @param root 数据源根集合。返回条目里的 path 一律相对**它**（不是相对 [collection]），
     *   这样才能原样喂回 [WebDavUrl.child] 重建地址 —— 列举子目录时两者不同，别传错。
     */
    suspend fun propfind(
        collection: HttpUrl,
        root: HttpUrl,
        basicAuthHeader: String,
        depth: Int = 1,
    ): List<WebDavEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(collection)
            .method(
                "PROPFIND",
                PROPFIND_BODY.toRequestBody("application/xml; charset=utf-8".toMediaType()),
            )
            .header("Depth", depth.coerceIn(0, 1).toString())
            .header("Authorization", basicAuthHeader)
            .header("Accept", "application/xml, text/xml")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw WebDavException.Network(e)
        }

        response.use { resp ->
            when (resp.code) {
                207, 200 -> {
                    val body = resp.body
                    try {
                        body.byteStream().use {
                            MultistatusParser.parse(it, collection.encodedPath, root.encodedPath)
                        }
                    } catch (e: WebDavException) {
                        throw e
                    } catch (e: Exception) {
                        throw WebDavException.Malformed(e.message ?: e::class.java.simpleName)
                    }
                }
                401 -> throw WebDavException.Unauthorized()
                429 -> throw WebDavException.Locked()
                404 -> throw WebDavException.NotFound()
                else -> throw WebDavException.Http(resp.code, resp.message.ifBlank { null })
            }
        }
    }

    companion object {
        /** 只要这些属性：目录/文件判定、大小、时间、类型。 */
        private val PROPFIND_BODY = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:propfind xmlns:D="DAV:">
              <D:prop>
                <D:displayname/>
                <D:resourcetype/>
                <D:getcontenttype/>
                <D:getcontentlength/>
                <D:getlastmodified/>
              </D:prop>
            </D:propfind>
        """.trimIndent()

        /** 生成 `Basic xxx` 头。 */
        fun basicAuth(username: String, password: String): String {
            val raw = "$username:$password"
            val encoded = Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))
            return "Basic $encoded"
        }
    }
}
