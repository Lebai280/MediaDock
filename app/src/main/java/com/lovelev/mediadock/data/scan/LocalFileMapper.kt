package com.lovelev.mediadock.data.scan

import com.lovelev.mediadock.core.model.TitleParser
import com.lovelev.mediadock.data.local.LocalFile
import com.lovelev.mediadock.data.local.LocalMedia
import com.lovelev.mediadock.data.local.MediaLibraryStore

/** 和 WebDAV 条目走同一个解析器 —— 所以本地文件扫进来也有像样的片名和海报墙。 */
internal fun LocalFile.toScannedFile(): MediaLibraryStore.ScannedFile {
    val storedPath = LocalMedia.toStoredPath(key)
    val seasonFromPath = TitleParser.seasonOfPath(storedPath)
    val parsed = TitleParser.parse(
        fileName = name,
        folderName = folderName,
        inSeasonDir = seasonFromPath != null,
    )
    return MediaLibraryStore.ScannedFile(
        path = storedPath,
        name = name,
        sizeBytes = sizeBytes,
        // WebDAV 那边存的是 RFC1123 字符串；本地是毫秒数，转字符串只当元信息。
        mtime = lastModified.toString(),
        title = parsed.title,
        year = parsed.year,
        // 文件名只给集数时，季号往往在上层目录（S01 / Season 1 / 第1季）——
        // 不补的话 S01/S02 的同集号会互相覆盖排序
        season = parsed.season ?: seasonFromPath,
        episode = parsed.episode,
    )
}
