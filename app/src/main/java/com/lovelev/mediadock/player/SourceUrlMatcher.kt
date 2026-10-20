package com.lovelev.mediadock.player

import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.webdav.WebDavUrl

/**
 * 播放地址 → 数据源的匹配规则（与 Android Uri 解耦，便于单测）。
 *
 * 规则：**同一 scheme/host/端口下，根路径最长前缀命中**。这样同一域名下的两个
 * WebDAV 账号（不同根路径）不会串号；没命中任何源时调用方不得发送凭据。
 */
internal object SourceUrlMatcher {

    data class UrlParts(
        val scheme: String?,
        val host: String?,
        /** Uri.port；-1 表示未显式写端口。 */
        val port: Int,
        val path: String,
    )

    fun bestMatch(sources: List<SourceConfig>, url: UrlParts): SourceConfig? {
        if (url.host.isNullOrBlank()) return null
        return sources
            .filter {
                it.credentialOk && (
                    it.type == SourceType.WEBDAV ||
                        it.type == SourceType.EMBY ||
                        it.type == SourceType.JELLYFIN
                    )
            }
            .mapNotNull { source ->
                rootPath(source)?.let { root ->
                    if (matches(source, url, root)) source to root.length else null
                }
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /** 源的根路径（编码形式，以 `/` 结尾）；不支持的源返回 null。 */
    fun rootPath(source: SourceConfig): String? = when (source.type) {
        SourceType.WEBDAV -> WebDavUrl.rootCollection(source.url, source.rootPath)?.encodedPath
        SourceType.EMBY, SourceType.JELLYFIN ->
            WebDavUrl.parse(source.url.trimEnd('/') + "/")?.encodedPath
        else -> null
    }

    private fun matches(source: SourceConfig, url: UrlParts, rootPath: String): Boolean {
        val rootHost = when (source.type) {
            SourceType.WEBDAV -> WebDavUrl.parse(source.url)
            else -> WebDavUrl.parse(source.url.trimEnd('/') + "/")
        } ?: return false
        if (url.scheme != rootHost.scheme) return false
        if (!url.host.equals(rootHost.host, ignoreCase = true)) return false
        val effectivePort = if (url.port == -1) defaultPort(url.scheme) else url.port
        if (effectivePort != rootHost.port) return false
        return rootPath.isNotEmpty() && url.path.startsWith(rootPath)
    }

    private fun defaultPort(scheme: String?): Int = when (scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }
}
