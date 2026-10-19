package com.lovelev.mediadock.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * works 查询的原始行。字段名与 SQL 别名一致；`paths` 是 char(31) 分隔的旧 path 列表。
 */
data class WorkCardRow(
    val id: String,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?,
    val type: String,
    val addedAt: Long,
    val episodeCount: Int,
    val completedEpisodes: Int,
    val movieProgress: Double,
    val isFavorite: Int,
    val genres: String?,
    val country: String?,
    val releaseDate: String?,
    val paths: String?,
)

@Dao
interface WorksCardDao {

    @RawQuery(
        observedEntities = [
            WorkEntity::class,
            EpisodeEntity::class,
            FavoriteEntity::class,
            EpisodeProgressEntity::class,
            MovieProgressEntity::class,
            WorkDetailEntity::class,
            MovieVersionEntity::class,
            EpisodeVersionEntity::class,
            MediaFileEntity::class,
        ],
    )
    fun pagingWorks(query: SupportSQLiteQuery): PagingSource<Int, WorkCardRow>

    @RawQuery(
        observedEntities = [
            WorkEntity::class,
            EpisodeEntity::class,
            FavoriteEntity::class,
            EpisodeProgressEntity::class,
            MovieProgressEntity::class,
            WorkDetailEntity::class,
            MovieVersionEntity::class,
            EpisodeVersionEntity::class,
            MediaFileEntity::class,
        ],
    )
    suspend fun cards(query: SupportSQLiteQuery): List<WorkCardRow>
}
