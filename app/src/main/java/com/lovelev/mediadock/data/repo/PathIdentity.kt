package com.lovelev.mediadock.data.repo

import com.lovelev.mediadock.data.tmdb.TitleParser

/** 从旧 path 解析出的内容身份（写新表时用）。 */
internal data class PathIdentity(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
)

/**
 * 现有仓库很多调用只给 path；v9 写层需要标题/季集。
 * 统一在这里按文件名 + 季目录解析，避免每个仓库各写一套。
 */
internal fun identityOf(path: String): PathIdentity {
    val name = path.substringAfterLast('/')
    val folderName = path.substringBeforeLast('/', "").substringAfterLast('/')
    val seasonFromPath = TitleParser.seasonOfPath(path)
    val parsed = TitleParser.parse(
        fileName = name,
        folderName = folderName,
        inSeasonDir = seasonFromPath != null,
    )
    return PathIdentity(
        title = parsed.title,
        year = parsed.year,
        season = parsed.season ?: seasonFromPath,
        episode = parsed.episode,
    )
}
