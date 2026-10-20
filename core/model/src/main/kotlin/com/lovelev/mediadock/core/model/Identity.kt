package com.lovelev.mediadock.core.model

/**
 * 数据源标识。
 *
 * 0a 只区分 WebDAV 与本地；SMB / Emby / Jellyfin 在 Phase 2 接入时复用同一类型。
 */
@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "SourceId 不能为空" }
        require(':' !in value) { "SourceId 不能包含冒号：$value" }
    }

    companion object {
        val WEBDAV = SourceId("webdav")
        val LOCAL = SourceId("local")
    }
}

enum class SourceType {
    WEBDAV,
    LOCAL,
    SMB,
    EMBY,
    JELLYFIN,
}

/** 内容 UUID（作品或分集）。用户数据只引用它，不引用 path。 */
@JvmInline
value class ContentId(val value: String) {
    init {
        require(value.isNotBlank()) { "ContentId 不能为空" }
    }
}

/**
 * 逻辑身份。**唯一但允许变化**：未匹配的临时身份在刮削后升级为 TMDB 身份。
 *
 * 统一经 [LogicalKeyCodec] 生成与解析，禁止各处手拼字符串。
 */
sealed interface LogicalKey {
    val encoded: String

    data class Movie(val tmdbId: Int) : LogicalKey {
        override val encoded: String get() = LogicalKeyCodec.movie(tmdbId)
    }

    data class Show(val tmdbId: Int) : LogicalKey {
        override val encoded: String get() = LogicalKeyCodec.show(tmdbId)
    }

    data class Episode(val tmdbId: Int, val season: Int, val episode: Int) : LogicalKey {
        override val encoded: String get() = LogicalKeyCodec.episode(tmdbId, season, episode)
    }

    data class File(val sourceId: SourceId, val key: String) : LogicalKey {
        override val encoded: String get() = LogicalKeyCodec.file(sourceId, key)
    }
}

object LogicalKeyCodec {
    fun movie(tmdbId: Int, scope: SourceId? = null): String =
        "tmdb:movie:$tmdbId" + scopeSuffix(scope)

    fun show(tmdbId: Int, scope: SourceId? = null): String =
        "tmdb:tv:$tmdbId" + scopeSuffix(scope)

    /**
     * 多数据源下**同一作品不跨源合并**：额外的源追加 `@<sourceId>` 后缀。
     * 旧 WebDAV 与本地沿用不带后缀的格式，保持既有用户数据与合并行为不变。
     */
    private fun scopeSuffix(scope: SourceId?): String =
        if (scope == null || scope == SourceId.WEBDAV || scope == SourceId.LOCAL) {
            ""
        } else {
            "@${scope.value}"
        }

    fun episode(tmdbId: Int, season: Int, episode: Int): String =
        "tmdb:tv:$tmdbId:s$season:e$episode"

    fun file(sourceId: SourceId, key: String): String = "file:${sourceId.value}:$key"

    /** 解析失败返回 null（调用方应把它当作未知身份处理，不要猜）。 */
    fun parse(encoded: String): LogicalKey? {
        val parts = encoded.split(':')
        if (parts.size < 3) return null
        return when (parts[0]) {
            "tmdb" -> when (parts[1]) {
                "movie" -> parts.getOrNull(2)?.toIntOrNull()?.let(LogicalKey::Movie)
                "tv" -> {
                    val tmdbId = parts.getOrNull(2)?.toIntOrNull() ?: return null
                    if (parts.size == 3) {
                        LogicalKey.Show(tmdbId)
                    } else {
                        val season = parts.getOrNull(3)?.removePrefix("s")?.toIntOrNull() ?: return null
                        val episode = parts.getOrNull(4)?.removePrefix("e")?.toIntOrNull() ?: return null
                        LogicalKey.Episode(tmdbId, season, episode)
                    }
                }
                else -> null
            }
            "file" -> {
                val sourceId = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
                val key = parts.drop(2).joinToString(":")
                if (key.isBlank()) null else LogicalKey.File(SourceId(sourceId), key)
            }
            else -> null
        }
    }
}

/**
 * 远程键归一化：NFC、统一斜杠、去重复斜杠、去尾斜杠。
 *
 * **URI 的 scheme 分隔符必须保留**（`content://` / `saf://`），
 * 否则本地媒体库的键会被改坏、和扫描时的前缀对不上。
 */
fun normalizeRemoteKey(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val normalized = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFC)
    val schemeEnd = normalized.indexOf("://")
    if (schemeEnd > 0) {
        val scheme = normalized.substring(0, schemeEnd + 3)
        return scheme + normalizePath(normalized.substring(schemeEnd + 3))
    }
    return normalizePath(normalized)
}

private fun normalizePath(path: String): String {
    var result = path.replace('\\', '/')
    while (result.startsWith("./")) result = result.removePrefix("./")
    result = result.replace(Regex("/{2,}"), "/")
    if (result.length > 1) result = result.trimEnd('/')
    return result
}

/** 确定性 UUID（v3），迁移与扫描重跑都得到同一 id，不依赖随机数。 */
object ContentIds {
    fun file(sourceId: SourceId, remoteKey: String): String =
        uuid("file:${sourceId.value}:$remoteKey")

    fun work(logicalKey: String): String = uuid("work:$logicalKey")

    fun episode(logicalKey: String): String = uuid("episode:$logicalKey")

    private fun uuid(seed: String): String =
        java.util.UUID.nameUUIDFromBytes(seed.toByteArray(Charsets.UTF_8)).toString()
}

/** 0a 只有一个默认资料；Phase 5 多资料时替换。 */
const val DEFAULT_USER_ID: String = "default"
