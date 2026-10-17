package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 分集元数据（TMDB `/tv/{id}/season/{n}` 拉回来的）。
 *
 * 详情页要按爆米花的样式显示「剧照 + 分集标题 + 时长」，这些 TMDB 的总览接口里没有，
 * 必须按季拉一次。**拉过就缓存**，别每次打开详情页都打 TMDB。
 */
@Entity(
    tableName = "episode_meta",
    primaryKeys = ["tmdbId", "season", "episode"],
)
data class EpisodeMeta(
    val tmdbId: Int,
    val season: Int,
    val episode: Int,
    val name: String,
    val stillPath: String?,
    val runtimeMinutes: Int?,
    val fetchedAt: Long,
)

@Dao
interface EpisodeMetaDao {

    @Query("SELECT * FROM episode_meta WHERE tmdbId = :tmdbId AND season = :season")
    fun observe(tmdbId: Int, season: Int): Flow<List<EpisodeMeta>>

    @Query("SELECT * FROM episode_meta WHERE tmdbId = :tmdbId AND season = :season")
    suspend fun find(tmdbId: Int, season: Int): List<EpisodeMeta>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<EpisodeMeta>)
}
