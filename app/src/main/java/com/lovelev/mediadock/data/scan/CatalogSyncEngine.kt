package com.lovelev.mediadock.data.scan

import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.core.model.SourcePaths
import com.lovelev.mediadock.data.emby.EmbyClient
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.source.SourceConfig

/**
 * Emby / Jellyfin 的目录同步：分页拉条目 → 写成伪文件（`item:<id>`）→ 写服务端元数据。
 *
 * 服务端条目没有目录树，因此不走 [com.lovelev.mediadock.core.data.scan.ScanEngine] 的 BFS；
 * 每次同步是整源替换（与扫描一致：本轮没见到的条目标记为缺失）。
 * 认证失败/网络错误由 [EmbyClient.Failure] 抛出，交给 Worker 映射。
 */
class CatalogSyncEngine(
    private val store: MediaLibraryStore,
    private val client: EmbyClient,
) {

    suspend fun run(
        source: SourceConfig,
        onProgress: suspend (files: Int) -> Unit,
    ): Int {
        val auth = if (source.apiKey.isNullOrBlank()) {
            client.authenticate(source.url, source.username, source.password)
        } else {
            null
        }

        val items = mutableListOf<EmbyClient.CatalogItem>()
        var start = 0
        while (true) {
            val page = client.items(
                baseUrl = source.url,
                auth = auth,
                apiKey = source.apiKey,
                startIndex = start,
                limit = PAGE_SIZE,
            )
            items += page
            onProgress(items.size)
            if (page.size < PAGE_SIZE || items.size >= MAX_ITEMS) break
            start += page.size
        }

        val playable = items.filter { it.type == "Movie" || it.type == "Episode" }
        val sourceId = SourceId(source.id)
        val files = playable.mapNotNull { CatalogMapping.file(sourceId, it) }
        store.replaceScannedFiles(sourceId, files)

        store.applyCatalogMetadata(
            playable.map { CatalogMapping.meta(sourceId, it) },
        )
        return files.size
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_ITEMS = 20_000
    }
}

/** 服务端条目 → 写层模型（纯函数，便于单测）。 */
internal object CatalogMapping {

    fun file(
        sourceId: SourceId,
        item: EmbyClient.CatalogItem,
    ): MediaLibraryStore.ScannedFile? {
        if (item.type != "Movie" && item.type != "Episode") return null
        val key = "item:${item.itemId}"
        return MediaLibraryStore.ScannedFile(
            path = key,
            name = item.name,
            sizeBytes = null,
            mtime = null,
            title = displayTitle(item),
            year = item.year,
            season = item.season,
            episode = item.episode,
            legacyPath = SourcePaths.qualify(sourceId, key),
            providerItemId = item.itemId,
        )
    }

    fun meta(
        sourceId: SourceId,
        item: EmbyClient.CatalogItem,
    ): MediaLibraryStore.CatalogMetaEntry = MediaLibraryStore.CatalogMetaEntry(
        path = SourcePaths.qualify(sourceId, "item:${item.itemId}"),
        title = displayTitle(item),
        year = item.year,
        posterUrl = item.posterUrl,
        overview = item.overview,
        rating = item.rating,
    )

    private fun displayTitle(item: EmbyClient.CatalogItem): String =
        if (item.type == "Episode") {
            item.seriesName?.takeIf { it.isNotBlank() } ?: item.name
        } else {
            item.name
        }
}
