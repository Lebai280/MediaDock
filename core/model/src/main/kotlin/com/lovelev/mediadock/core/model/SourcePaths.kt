package com.lovelev.mediadock.core.model

/**
 * 多数据源下的路径身份。
 *
 * 旧 WebDAV 源（[SourceId.WEBDAV]）继续用**裸相对路径**，本地媒体继续用 `local/…` /
 * `content://…` —— 这两类路径与历史数据完全兼容，不需要迁移。
 * 其他源（第二个 WebDAV 账号、SMB、Emby/Jellyfin）统一写成
 * `source:<sourceId>/<remoteKey>`，从而在同一个字符串空间里与旧路径不冲突。
 *
 * 解析只在**语法层**做（源 id 形如 `webdav-3f2a`）；是否真的存在该源由应用层的注册表判断，
 * 避免数据库层依赖运行时配置。
 */
object SourcePaths {

    const val PREFIX = "source:"

    private val SOURCE_ID = Regex("^[a-z0-9][a-z0-9_-]*$")

    data class Parsed(val sourceId: SourceId, val key: String)

    /** 把源内相对键写成跨源唯一路径。旧源与本地保持原样。 */
    fun qualify(sourceId: SourceId, remoteKey: String): String =
        if (sourceId == SourceId.WEBDAV || sourceId == SourceId.LOCAL) {
            remoteKey
        } else {
            "$PREFIX${sourceId.value}/$remoteKey"
        }

    /** 语法解析；不是限定路径或格式不合法返回 null。 */
    fun parse(path: String): Parsed? {
        if (!path.startsWith(PREFIX)) return null
        val rest = path.substring(PREFIX.length)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.lastIndex) return null
        val id = rest.substring(0, slash)
        if (!SOURCE_ID.matches(id)) return null
        return Parsed(SourceId(id), rest.substring(slash + 1))
    }

    /** 应用层解析：只认已注册的源，避免把真实存在的 `source:` 目录误判成源前缀。 */
    fun parseKnown(path: String, knownSourceIds: Set<String>): Parsed? =
        parse(path)?.takeIf { it.sourceId.value in knownSourceIds }

    /** 路径所属源；裸路径按调用方给的默认源处理（默认是旧 WebDAV 源）。 */
    fun sourceIdOf(path: String, fallback: SourceId = SourceId.WEBDAV): SourceId =
        parse(path)?.sourceId ?: fallback

    /** 去掉源前缀后的源内相对键。 */
    fun keyOf(path: String): String = parse(path)?.key ?: path
}
