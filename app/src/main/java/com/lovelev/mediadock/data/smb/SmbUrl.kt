package com.lovelev.mediadock.data.smb

import com.lovelev.mediadock.data.source.SourceConfig

/**
 * SMB 播放地址：`smb://host[:port]/share/path`。
 *
 * 路径按段百分号编码（`+` 保留为字面加号），与 WebDAV 的编码约定一致。
 */
object SmbUrl {

    data class Parsed(
        val host: String,
        val port: Int,
        val share: String,
        val path: String,
    )

    fun build(source: SourceConfig, key: String): String? {
        val host = hostOf(source.url) ?: return null
        val share = source.share.trim('/')
        if (share.isBlank()) return null
        val encodedShare = encode(share)
        val clean = key.trim('/')
        return if (clean.isEmpty()) {
            "smb://$host/$encodedShare"
        } else {
            "smb://$host/$encodedShare/" + clean.split('/')
                .filter { it.isNotEmpty() }
                .joinToString("/") { encode(it) }
        }
    }

    /** 用户填的「主机」：允许 `host`、`host:445`、`smb://host`。 */
    fun hostOf(raw: String): String? = raw.trim()
        .removePrefix("smb://")
        .removePrefix("SMB://")
        .trimEnd('/')
        .takeIf { it.isNotBlank() }

    fun parse(uri: String): Parsed? {
        val withoutScheme = uri.removePrefix("smb://").removePrefix("SMB://")
        if (withoutScheme == uri) return null
        val slash = withoutScheme.indexOf('/')
        if (slash <= 0 || slash == withoutScheme.lastIndex) return null
        val authority = withoutScheme.substring(0, slash)
        val segments = withoutScheme.substring(slash + 1)
            .split('/')
            .filter { it.isNotEmpty() }
            .map(::decode)
        if (segments.isEmpty()) return null
        val colon = authority.lastIndexOf(':')
        val host: String
        val port: Int
        if (colon > 0) {
            host = authority.substring(0, colon)
            port = authority.substring(colon + 1).toIntOrNull() ?: return null
        } else {
            host = authority
            port = 445
        }
        if (host.isBlank()) return null
        return Parsed(
            host = host,
            port = port,
            share = segments.first(),
            path = segments.drop(1).joinToString("/"),
        )
    }

    fun matches(source: SourceConfig, parsed: Parsed): Boolean {
        val host = hostOf(source.url) ?: return false
        val sourceHost = host.substringBeforeLast(':')
        val sourcePort = host.substringAfterLast(':', "445").toIntOrNull() ?: 445
        return sourceHost.equals(parsed.host, ignoreCase = true) &&
            sourcePort == parsed.port &&
            source.share.trim('/') == parsed.share
    }

    private fun encode(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private fun decode(segment: String): String =
        runCatching {
            java.net.URLDecoder.decode(segment.replace("+", "%2B"), "UTF-8")
        }.getOrDefault(segment)
}
