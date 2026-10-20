package com.lovelev.mediadock.data.repo

import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.WorkVersionRow
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.source.SourceRepository

/** 版本排序用的纯键，便于单测。 */
internal data class VersionRank(
    val pinned: Boolean,
    val priority: Int,
    val local: Boolean,
    val sizeBytes: Long?,
    val path: String,
)

/** 固定（pinned）> 源优先级（数字小者优先）> 本地源优先 > 体积更大者 > path。 */
internal fun versionOrder(): Comparator<VersionRank> =
    compareByDescending<VersionRank> { it.pinned }
        .thenBy { it.priority }
        .thenBy { if (it.local) 0 else 1 }
        .thenByDescending { it.sizeBytes ?: 0L }
        .thenBy { it.path }

/** 同一作品/分集在各数据源的版本，按选源规则排序。 */
class PlaybackVersionRepository(
    private val db: MediaDatabase,
    private val store: MediaLibraryStore,
    private val sources: SourceRepository,
) {

    suspend fun versionsOf(path: String): List<PlayableVersion> {
        val resolved = store.resolve(path) ?: return emptyList()
        val dao = db.mediaDao()
        val episodeId = resolved.episodeId
        val rows = if (episodeId != null) {
            dao.episodeVersionRows(episodeId)
        } else {
            dao.movieVersionRows(resolved.workId)
        }
        if (rows.isEmpty()) return emptyList()
        if (sources.cachedAll().isEmpty()) sources.refresh()
        val byId = sources.cachedAll().associateBy { it.id }
        val comparator = versionOrder()
        return rows
            .sortedWith(Comparator { a, b ->
                comparator.compare(rankOf(a, byId), rankOf(b, byId))
            })
            .map {
                PlayableVersion(
                    path = it.path,
                    name = it.path.substringAfterLast('/'),
                    sourceId = it.sourceId,
                    sourceName = byId[it.sourceId]?.name ?: it.sourceId,
                    sizeBytes = it.sizeBytes,
                    pinned = it.pinned > 0,
                )
            }
    }

    private fun rankOf(row: WorkVersionRow, byId: Map<String, SourceConfig>): VersionRank {
        val source = byId[row.sourceId]
        return VersionRank(
            pinned = row.pinned > 0,
            priority = source?.priority ?: 100,
            local = source?.type == SourceType.LOCAL,
            sizeBytes = row.sizeBytes,
            path = row.path,
        )
    }
}
