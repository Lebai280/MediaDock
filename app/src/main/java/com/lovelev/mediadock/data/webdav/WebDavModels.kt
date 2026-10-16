package com.lovelev.mediadock.data.webdav

/**
 * WebDAV 目录里的一个条目。
 *
 * [path] 是**已百分号解码**的相对路径（相对数据源根目录），例如 `电影/2024/demo.mkv`。
 * 内部一律保存解码后的路径，只在真正发请求时才编码 —— 否则会二次编码。
 */
data class WebDavEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long?,
    val lastModified: String?,
    val contentType: String?,
)
