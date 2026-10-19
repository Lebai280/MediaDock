package com.lovelev.mediadock.data.local

import androidx.room.withTransaction
import com.lovelev.mediadock.core.model.ContentIds
import com.lovelev.mediadock.core.model.DEFAULT_USER_ID
import com.lovelev.mediadock.core.model.LogicalKeyCodec
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.core.model.normalizeRemoteKey

/**
 * 路径 API → v9 身份模型的写适配层。
 *
 * 现有仓库与 UI 仍按 path 调用；这里负责解析到 `media_files` / `works` / `episodes`，
 * 再写对应的新表。所有多步操作走同一个事务。
 */
class MediaLibraryStore(private val db: MediaDatabase) {

    private val w = db.writeDao()

    data class Resolved(
        val workId: String,
        val episodeId: String?,
        val fileId: String,
        val segmentCount: Int,
    )

    /** 扫描器交给写层的文件事实（标题等由 app 层解析后传入）。 */
    data class ScannedFile(
        val path: String,
        val name: String,
        val sizeBytes: Long?,
        val mtime: String?,
        val title: String,
        val year: Int?,
        val season: Int?,
        val episode: Int?,
    )

    suspend fun resolve(path: String): Resolved? = db.withTransaction { resolveLocked(path) }

    private suspend fun resolveLocked(path: String): Resolved? {
        val file = w.fileByPath(path) ?: return null
        return resolveForFileLocked(file)
    }

    private suspend fun resolveForFileLocked(file: MediaFileEntity): Resolved? {
        val episodeId = w.episodeIdForFile(file.id)
        val workId = if (episodeId != null) {
            w.workIdForEpisode(episodeId)
        } else {
            w.movieWorkIdForFile(file.id)
        } ?: return null
        return Resolved(workId, episodeId, file.id, w.segmentCount(file.id))
    }

    // ---------------------------------------------------------------- 扫描

    /**
     * 整批替换一个来源（或本地的一个前缀子集）的文件事实。
     *
     * [missingPrefix] 非空时只把该前缀下的旧条目标记为缺失（SAF / 系统媒体库各占一半）。
     * 缺失条目不删除作品与用户数据，文件回来后会重新挂上。
     */
    suspend fun replaceScannedFiles(
        sourceId: SourceId,
        files: List<ScannedFile>,
        missingPrefix: String? = null,
    ) = db.withTransaction {
        val now = System.currentTimeMillis()
        if (missingPrefix != null) {
            w.markMissingByPrefix(missingPrefix, now)
        } else {
            w.markMissingBySource(sourceId.value, now)
        }
        for (file in files) {
            val remoteKey = normalizeRemoteKey(file.path)
            val existing = w.fileByKey(sourceId.value, remoteKey)
            val fileId = existing?.id ?: ContentIds.file(sourceId, remoteKey)
            val sameSize = existing == null ||
                existing.sizeBytes == null ||
                file.sizeBytes == null ||
                existing.sizeBytes == file.sizeBytes
            val resolved = if (existing != null && sameSize) resolveForFileLocked(existing) else null
            if (resolved == null) {
                w.deleteMovieVersionsForFile(fileId)
                w.deleteEpisodeVersionsForFile(fileId)
                w.deleteSegmentsForFile(fileId)
                createContentLocked(sourceId, fileId, file)
            }
            w.upsertFile(
                MediaFileEntity(
                    id = fileId,
                    sourceId = sourceId.value,
                    remoteKey = remoteKey,
                    legacyPath = file.path,
                    mediaKind = "main",
                    name = file.name,
                    sizeBytes = file.sizeBytes,
                    mtime = file.mtime,
                    missingAt = null,
                    parsedTitle = file.title,
                    parsedYear = file.year,
                    parsedSeason = file.season,
                    parsedEpisode = file.episode,
                    legacyCompat = 0,
                ),
            )
        }
    }

    private suspend fun createContentLocked(
        sourceId: SourceId,
        fileId: String,
        file: ScannedFile,
    ): Resolved {
        val now = System.currentTimeMillis()
        val isEpisode = file.season != null || file.episode != null
        return if (isEpisode) {
            val work = w.matchedWorkByTitle(file.title, file.year, "tv")
                ?: w.anyWorkByTitle(file.title, file.year, "tv")
                ?: run {
                    val key = LogicalKeyCodec.file(
                        sourceId,
                        "tv:${normalizeRemoteKey(file.title)}|${file.year ?: ""}|s${file.season ?: 0}",
                    )
                    val id = ContentIds.work(key)
                    w.insertWorkIgnore(
                        WorkEntity(
                            id = id,
                            logicalKey = key,
                            tmdbId = null,
                            type = "tv",
                            title = file.title,
                            year = file.year,
                            addedAt = now,
                        ),
                    )
                    w.workById(id)!!
                }
            val season = file.season ?: 0
            val episode = file.episode ?: 0
            val episodeKey = work.tmdbId?.let { LogicalKeyCodec.episode(it, season, episode) }
                ?: LogicalKeyCodec.file(
                    sourceId,
                    "episode:${normalizeRemoteKey(file.title)}|${file.year ?: ""}|s$season|e$episode",
                )
            val ep = w.episodeByNumber(work.id, season, episode) ?: run {
                val id = ContentIds.episode(episodeKey)
                w.insertEpisodeIgnore(
                    EpisodeEntity(
                        id = id,
                        logicalKey = episodeKey,
                        workId = work.id,
                        season = season,
                        episode = episode,
                        title = file.title,
                    ),
                )
                w.episodeByNumber(work.id, season, episode)!!
            }
            w.upsertEpisodeVersion(
                EpisodeVersionEntity(
                    episodeId = ep.id,
                    fileId = fileId,
                    sourceId = sourceId.value,
                    sizeBytes = file.sizeBytes,
                ),
            )
            w.upsertSegment(FileSegmentEntity(fileId, ep.id, 0, 0L, null))
            Resolved(work.id, ep.id, fileId, 1)
        } else {
            val work = w.matchedWorkByTitle(file.title, file.year, "movie")
                ?: w.anyWorkByTitle(file.title, file.year, "movie")
                ?: run {
                    val key = LogicalKeyCodec.file(
                        sourceId,
                        "movie:${normalizeRemoteKey(file.title)}|${file.year ?: ""}",
                    )
                    val id = ContentIds.work(key)
                    w.insertWorkIgnore(
                        WorkEntity(
                            id = id,
                            logicalKey = key,
                            tmdbId = null,
                            type = "movie",
                            title = file.title,
                            year = file.year,
                            addedAt = now,
                        ),
                    )
                    w.workById(id)!!
                }
            w.upsertMovieVersion(
                MovieVersionEntity(
                    workId = work.id,
                    fileId = fileId,
                    sourceId = sourceId.value,
                    sizeBytes = file.sizeBytes,
                ),
            )
            Resolved(work.id, null, fileId, 0)
        }
    }

    /** 清空媒体库：删文件与版本/分段；作品、分集与用户数据保留。 */
    suspend fun clearLibrary() = db.withTransaction {
        w.deleteAllMovieVersions()
        w.deleteAllEpisodeVersions()
        w.deleteAllSegments()
        w.deleteAllFiles()
    }

    // ---------------------------------------------------------------- 进度

    suspend fun saveProgress(
        path: String,
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
        positionMs: Long,
        durationMs: Long,
        updatedAt: Long,
    ) = db.withTransaction {
        if (durationMs <= 0L) return@withTransaction
        val content = resolveLocked(path)
            ?: ensureContentLocked(path, title, year, season, episode)
        val position = positionMs.coerceIn(0L, durationMs)
        val completed = if (position >= durationMs * 9 / 10) 1 else 0
        when {
            content.segmentCount > 1 -> w.upsertFileProgress(
                FileProgressEntity(content.fileId, DEFAULT_USER_ID, position, durationMs, updatedAt),
            )
            content.episodeId != null -> w.upsertEpisodeProgress(
                EpisodeProgressEntity(content.episodeId, DEFAULT_USER_ID, position, durationMs, completed, updatedAt),
            )
            else -> w.upsertMovieProgress(
                MovieProgressEntity(content.workId, DEFAULT_USER_ID, position, durationMs, completed, updatedAt),
            )
        }
    }

    suspend fun clearProgress(path: String) = db.withTransaction {
        val content = resolveLocked(path) ?: return@withTransaction
        when {
            content.segmentCount > 1 -> w.deleteFileProgress(content.fileId, DEFAULT_USER_ID)
            content.episodeId != null -> w.deleteEpisodeProgress(content.episodeId, DEFAULT_USER_ID)
            else -> w.deleteMovieProgress(content.workId, DEFAULT_USER_ID)
        }
    }

    // ---------------------------------------------------------------- 收藏 / 片单 / 改名 / 详情

    suspend fun toggleFavorite(path: String, title: String, year: Int?, season: Int?, episode: Int?): Boolean =
        db.withTransaction {
            val content = resolveLocked(path)
                ?: ensureContentLocked(path, title, year, season, episode)
            if (w.favoriteCount(content.workId, DEFAULT_USER_ID) > 0) {
                w.deleteFavorite(content.workId, DEFAULT_USER_ID)
                false
            } else {
                w.insertFavorite(FavoriteEntity(content.workId, DEFAULT_USER_ID, System.currentTimeMillis()))
                true
            }
        }

    suspend fun saveOverride(paths: List<String>, title: String, year: Int?) = db.withTransaction {
        val workIds = paths.mapNotNull { path ->
            resolveLocked(path)?.workId ?: ensureContentLocked(
                path,
                fallbackTitle(path),
                null,
                null,
                null,
            ).workId
        }.distinct()
        if (title.isBlank()) {
            workIds.forEach { w.deleteOverride(it) }
        } else {
            workIds.forEach { w.upsertOverride(TitleOverrideEntity(it, title.trim(), year)) }
        }
    }

    suspend fun createPlaylist(name: String): Long =
        w.insertPlaylist(Playlist(userId = DEFAULT_USER_ID, name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun addToPlaylist(playlistId: Long, path: String, title: String, year: Int?, season: Int?, episode: Int?) =
        db.withTransaction {
            val content = resolveLocked(path)
                ?: ensureContentLocked(path, title, year, season, episode)
            w.addPlaylistItem(PlaylistItemEntity(playlistId, content.workId, System.currentTimeMillis()))
        }

    suspend fun removeFromPlaylist(playlistId: Long, path: String) = db.withTransaction {
        val content = resolveLocked(path) ?: return@withTransaction
        w.removePlaylistItem(playlistId, content.workId)
    }

    suspend fun deletePlaylist(playlistId: Long) = db.withTransaction {
        w.clearPlaylistItems(playlistId)
        w.deletePlaylist(playlistId)
    }

    suspend fun saveDetails(
        path: String,
        tmdbId: Int,
        releaseDate: String?,
        runtimeMinutes: Int?,
        genres: String,
        country: String?,
        castJson: String,
        totalEpisodes: Int?,
        totalSeasons: Int?,
        fetchedAt: Long,
    ) = db.withTransaction {
        val workId = resolveLocked(path)?.workId ?: return@withTransaction
        w.upsertWorkDetail(
            WorkDetailEntity(
                workId = workId,
                tmdbId = tmdbId,
                releaseDate = releaseDate,
                runtimeMinutes = runtimeMinutes,
                genres = genres,
                country = country,
                castJson = castJson,
                fetchedAt = fetchedAt,
                totalEpisodes = totalEpisodes,
                totalSeasons = totalSeasons,
            ),
        )
    }

    suspend fun deleteDetails(paths: List<String>) = db.withTransaction {
        val workIds = paths.mapNotNull { resolveLocked(it)?.workId }.distinct()
        if (workIds.isNotEmpty()) w.deleteWorkDetails(workIds)
    }

    // ---------------------------------------------------------------- 刮削

    /**
     * 把一次匹配写回作品：逻辑键从 `file:` 升级为 `tmdb:`；
     * 若目标身份已存在则执行合并（版本/进度/收藏/片单/改名/详情）。
     */
    suspend fun applyScrape(
        path: String,
        tmdbId: Int?,
        title: String?,
        year: Int?,
        posterPath: String?,
        backdropPath: String?,
        overview: String?,
        rating: Double?,
        matched: Boolean,
        scrapedAt: Long,
    ) = db.withTransaction {
        val resolved = resolveLocked(path) ?: return@withTransaction
        var work = w.workById(resolved.workId) ?: return@withTransaction
        val state = if (matched) ScrapeStatus.MATCHED else ScrapeStatus.FAILED
        if (!matched || tmdbId == null) {
            work = work.copy(
                tmdbId = null,
                posterPath = null,
                backdropPath = null,
                overview = null,
                rating = null,
                scrapeState = state,
                scrapedAt = scrapedAt,
                updatedAt = scrapedAt,
            )
            w.updateWork(work)
            return@withTransaction
        }
        val id = tmdbId ?: return@withTransaction
        val targetKey = if (work.type == "tv") {
            LogicalKeyCodec.show(id)
        } else {
            LogicalKeyCodec.movie(id)
        }
        val target = w.workByLogicalKey(targetKey)
        if (target != null && target.id != work.id) {
            mergeWorkLocked(work, target)
            work = target
        }
        work = work.copy(
            logicalKey = targetKey,
            tmdbId = id,
            title = title ?: work.title,
            year = year ?: work.year,
            posterPath = posterPath ?: work.posterPath,
            backdropPath = backdropPath ?: work.backdropPath,
            overview = overview ?: work.overview,
            rating = rating ?: work.rating,
            scrapeState = state,
            scrapedAt = scrapedAt,
            updatedAt = scrapedAt,
        )
        w.updateWork(work)
        if (work.type == "tv") {
            relinkEpisodesLocked(work, id)
        }
    }

    private suspend fun relinkEpisodesLocked(work: WorkEntity, tmdbId: Int) {
        w.episodesOfWork(work.id).forEach { ep ->
            val key = LogicalKeyCodec.episode(tmdbId, ep.season, ep.episode)
            val existing = w.episodeByLogicalKey(key)
            if (existing != null && existing.id != ep.id) {
                w.moveEpisodeVersions(ep.id, existing.id)
                w.moveSegments(ep.id, existing.id)
                w.moveEpisodeProgress(ep.id, existing.id)
                w.deleteEpisodeVersionsOfEpisode(ep.id)
                w.deleteSegmentsOfEpisode(ep.id)
                w.deleteEpisodeProgressOf(ep.id)
                w.insertTombstone(ContentTombstoneEntity(ep.id, existing.id, System.currentTimeMillis()))
            } else {
                w.updateEpisodeLogicalKey(ep.id, key)
            }
        }
    }

    /** 合并到已存在作品；旧作品留 tombstone，不物理删除。 */
    private suspend fun mergeWorkLocked(from: WorkEntity, to: WorkEntity) {
        val now = System.currentTimeMillis()
        w.moveMovieVersions(from.id, to.id)
        w.deleteMovieVersionsOfWork(from.id)
        w.episodesOfWork(from.id).forEach { ep ->
            val targetEp = w.episodeByNumber(to.id, ep.season, ep.episode)
            if (targetEp != null) {
                w.moveEpisodeVersions(ep.id, targetEp.id)
                w.moveSegments(ep.id, targetEp.id)
                w.moveEpisodeProgress(ep.id, targetEp.id)
                w.deleteEpisodeVersionsOfEpisode(ep.id)
                w.deleteSegmentsOfEpisode(ep.id)
                w.deleteEpisodeProgressOf(ep.id)
                w.insertTombstone(ContentTombstoneEntity(ep.id, targetEp.id, now))
            } else {
                w.moveEpisodeToWork(ep.id, to.id)
            }
        }
        w.moveMovieProgress(from.id, to.id)
        w.deleteMovieProgressOfWork(from.id)
        w.moveFavorites(from.id, to.id)
        w.deleteFavorites(from.id)
        w.movePlaylistItems(from.id, to.id)
        w.deletePlaylistItemsOfWork(from.id)
        w.workDetail(from.id)?.let { detail ->
            if (w.workDetail(to.id) == null) w.upsertWorkDetail(detail.copy(workId = to.id))
            w.deleteWorkDetails(listOf(from.id))
        }
        w.moveOverride(from.id, to.id)
        w.deleteOverride(from.id)
        w.insertTombstone(ContentTombstoneEntity(from.id, to.id, now))
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun ensureContentLocked(
        path: String,
        title: String,
        year: Int?,
        season: Int?,
        episode: Int?,
    ): Resolved {
        resolveLocked(path)?.let { return it }
        val sourceId = sourceOf(path)
        val remoteKey = normalizeRemoteKey(path)
        val fileId = ContentIds.file(sourceId, remoteKey)
        val name = path.substringAfterLast('/').ifBlank { path }
        w.upsertFile(
            MediaFileEntity(
                id = fileId,
                sourceId = sourceId.value,
                remoteKey = remoteKey,
                legacyPath = path,
                name = name,
                sizeBytes = null,
                parsedTitle = title.ifBlank { fallbackTitle(path) },
                parsedYear = year,
                parsedSeason = season,
                parsedEpisode = episode,
                legacyCompat = 0,
            ),
        )
        return createContentLocked(
            sourceId = sourceId,
            fileId = fileId,
            file = ScannedFile(
                path = path,
                name = name,
                sizeBytes = null,
                mtime = null,
                title = title.ifBlank { fallbackTitle(path) },
                year = year,
                season = season,
                episode = episode,
            ),
        )
    }

    private fun sourceOf(path: String): SourceId =
        if (path.startsWith("local/") || path.startsWith("content://")) SourceId.LOCAL else SourceId.WEBDAV

    private fun fallbackTitle(path: String): String =
        path.substringAfterLast('/').substringBeforeLast('.').ifBlank { path }
}
