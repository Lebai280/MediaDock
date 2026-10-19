package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 详情页补充信息 —— **v9 起是只读兼容 VIEW**，数据来自 `work_details`。
 * 视图把作品展开成所有文件 path，详情页按 path 查的逻辑不变。
 */
@DatabaseView(viewName = "title_details", value = ViewSql.TITLE_DETAILS)
data class TitleDetail(
    val path: String,
    val tmdbId: Int,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    /** 逗号分隔的类型名，例如 `动画,短片,家庭`。 */
    val genres: String,
    /** 已经转成中文的国家/地区名。 */
    val country: String?,
    /** 演职人员的 JSON（见 `TmdbClient.CastMember`）。 */
    val castJson: String,
    val fetchedAt: Long,
    /** 剧集总集数 / 总季数（TMDB 官方值；电影为 null）。 */
    val totalEpisodes: Int? = null,
    val totalSeasons: Int? = null,
)

@Dao
interface TitleDetailDao {

    @Query("SELECT * FROM title_details WHERE path = :path")
    fun observe(path: String): Flow<TitleDetail?>

    /** 全部详情 —— 首页搜索要按类型/国家/演员过滤时用。 */
    @Query("SELECT * FROM title_details")
    fun observeAll(): Flow<List<TitleDetail>>

    @Query("SELECT * FROM title_details WHERE path = :path")
    suspend fun find(path: String): TitleDetail?
}
