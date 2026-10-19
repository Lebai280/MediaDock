package com.lovelev.mediadock.data.webdav

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream

/**
 * WebDAV 地址拼装与路径编解码。**所有 URL 都必须经这里生成，绝不字符串拼接** ——
 * 中文名、空格、`#`、`?` 等字符手工拼接会拼坏。
 */
object WebDavUrl {

    /** 解析用户填写的地址；缺协议时按 https 补。失败返回 null。 */
    fun parse(raw: String): HttpUrl? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val withScheme =
            if (t.startsWith("http://", true) || t.startsWith("https://", true)) t
            else "https://$t"
        return withScheme.toHttpUrlOrNull()
    }

    /**
     * 集合地址：**末尾一定补 `/`**。
     * AList/OpenList 会把 `/dav` 301 到 `/dav/`，而 OkHttp 跟随重定向时可能丢掉 PROPFIND 方法。
     */
    fun collection(base: HttpUrl): HttpUrl {
        val p = base.encodedPath.trimEnd('/')
        return base.newBuilder().encodedPath(if (p.isEmpty()) "/" else "$p/").build()
    }

    /**
     * 在根集合下取相对路径（**未编码**，此处负责正确编码）。
     * 目录请传 `isCollection = true`，会补结尾 `/`。
     */
    fun child(root: HttpUrl, relativePath: String, isCollection: Boolean = false): HttpUrl {
        val clean = relativePath.trim('/')
        val b = root.newBuilder()
        if (clean.isNotEmpty()) b.addPathSegments(clean)
        val built = b.build()
        return if (isCollection) collection(built) else built
    }

    /**
     * 数据源根集合 = 用户填的地址 + 可选根路径，末尾补 `/`。
     * 地址不合法返回 null。
     */
    fun rootCollection(rawUrl: String, rootPath: String): HttpUrl? {
        val parsed = parse(rawUrl) ?: return null
        return if (rootPath.isBlank()) {
            collection(parsed)
        } else {
            collection(child(parsed, rootPath, isCollection = true))
        }
    }

    /**
     * 百分号解码（UTF-8）。**不把 `+` 当空格** —— 路径里的 `+` 是字面加号，
     * 用 `URLDecoder` 会错解成空格。
     */
    fun decodePath(encoded: String): String {
        if (!encoded.contains('%')) return encoded
        val sb = StringBuilder(encoded.length)
        val buf = ByteArrayOutputStream(8)

        fun flush() {
            if (buf.size() > 0) {
                sb.append(String(buf.toByteArray(), Charsets.UTF_8))
                buf.reset()
            }
        }

        var i = 0
        while (i < encoded.length) {
            val c = encoded[i]
            if (c == '%' && i + 2 < encoded.length) {
                val hi = Character.digit(encoded[i + 1], 16)
                val lo = Character.digit(encoded[i + 2], 16)
                if (hi >= 0 && lo >= 0) {
                    buf.write((hi shl 4) or lo)
                    i += 3
                    continue
                }
            }
            flush()
            sb.append(c)
            i++
        }
        flush()
        return sb.toString()
    }

    /** 归一化，用于判断「这个 href 是不是我自己」。 */
    fun normalizeForCompare(encoded: String): String = decodePath(encoded).trimEnd('/')
}
