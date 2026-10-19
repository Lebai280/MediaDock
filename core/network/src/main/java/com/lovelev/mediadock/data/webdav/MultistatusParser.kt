package com.lovelev.mediadock.data.webdav

import android.util.Xml
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * 解析 WebDAV 的 `207 Multi-Status` 响应。
 *
 * 两个必须处理的现实：
 *  1. 命名空间前缀不统一（`D:` / `d:` / `lp1:` / 无前缀）→ **只按 local name 匹配**。
 *  2. `href` 可能是绝对 URL（`https://host/dav/%E7%94%B5%E5%BD%B1/`）也可能是相对路径
 *     （`/dav/%E7%94%B5%E5%BD%B1/`）→ 统一相对化到**数据源根**之下。
 *
 * Depth:1 会返回集合自身；它的 ETag/lastModified 用来做增量扫描的目录缓存。
 */
object MultistatusParser {

    private const val EVENT_GUARD = 2_000_000

    fun parse(
        input: InputStream,
        collectionEncodedPath: String,
        rootEncodedPath: String,
    ): List<WebDavEntry> = parseWithSelf(input, collectionEncodedPath, rootEncodedPath).entries

    /**
     * @param collectionEncodedPath 被列举集合的已编码路径，例如 `/dav/%E7%94%B5%E5%BD%B1/`。
     * @param rootEncodedPath **数据源根**集合的已编码路径，例如 `/dav/`。
     */
    fun parseWithSelf(
        input: InputStream,
        collectionEncodedPath: String,
        rootEncodedPath: String,
    ): WebDavListing {
        val parser = Xml.newPullParser()
        // 关闭命名空间处理：parser.name 会给出 "D:href" 这样的限定名，取冒号后段即可
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val root = WebDavUrl.decodePath(rootEncodedPath).trimEnd('/')
        val self = WebDavUrl.decodePath(collectionEncodedPath).trimEnd('/')
            .removePrefix(root)
            .trim('/')
        val result = ArrayList<WebDavEntry>()
        var selfEtag: String? = null
        var selfLastModified: String? = null
        var current: MutableEntry? = null
        var inResourceType = false
        var guard = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (++guard > EVENT_GUARD) throw WebDavException.Malformed("响应过大")
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "response" -> current = MutableEntry()
                    "resourcetype" -> inResourceType = true
                    // <collection/> 在 <resourcetype> 里 => 目录
                    "collection" -> if (inResourceType) current?.isDirectory = true
                    "href" -> if (current?.href == null) current?.href = parser.nextText().trim()
                    "displayname" -> current?.displayName = parser.nextText().trim()
                    "getcontentlength" -> current?.size = parser.nextText().trim().toLongOrNull()
                    "getlastmodified" -> current?.lastModified = parser.nextText().trim()
                    "getcontenttype" -> current?.contentType = parser.nextText().trim()
                    "getetag" -> current?.etag = parser.nextText().trim()
                    else -> Unit
                }

                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "resourcetype" -> inResourceType = false
                    "response" -> {
                        current?.resolve(root, self)?.let { resolved ->
                            if (resolved.isSelf) {
                                selfEtag = resolved.etag
                                selfLastModified = resolved.lastModified
                            } else {
                                resolved.entry?.let(result::add)
                            }
                        }
                        current = null
                    }
                    else -> Unit
                }
            }
            event = parser.next()
        }
        return WebDavListing(result, selfEtag, selfLastModified)
    }

    /** 取限定名的 local name：`D:href` → `href`。 */
    private fun localName(qualified: String): String =
        qualified.substringAfterLast(':').lowercase()

    /**
     * 把 href 转成相对 [root] 的解码路径。返回空串表示"这就是根自身"。
     */
    private fun relativize(href: String, root: String): String {
        var raw = href
        // 绝对 URL 只取路径部分
        val absolute = raw.toHttpUrlOrNull()
        if (absolute != null) raw = absolute.encodedPath
        val decoded = WebDavUrl.decodePath(raw)
        val rel =
            if (root.isNotEmpty() && decoded.startsWith(root)) decoded.removePrefix(root)
            else decoded
        return rel.trim('/')
    }

    private class MutableEntry {
        var href: String? = null
        var displayName: String? = null
        var isDirectory: Boolean = false
        var size: Long? = null
        var lastModified: String? = null
        var contentType: String? = null
        var etag: String? = null

        fun resolve(root: String, self: String): Resolved? {
            val h = href ?: return null
            val rel = relativize(h, root)
            if (rel == self) {
                return Resolved(
                    entry = null,
                    isSelf = true,
                    etag = etag,
                    lastModified = lastModified,
                )
            }
            val name = rel.substringAfterLast('/')
            return Resolved(
                entry = WebDavEntry(
                    path = rel,
                    name = name.ifBlank { displayName ?: return null },
                    isDirectory = isDirectory,
                    size = if (isDirectory) null else size,
                    lastModified = lastModified,
                    contentType = contentType,
                    etag = etag,
                ),
                isSelf = false,
                etag = etag,
                lastModified = lastModified,
            )
        }
    }

    private data class Resolved(
        val entry: WebDavEntry?,
        val isSelf: Boolean,
        val etag: String?,
        val lastModified: String?,
    )
}
