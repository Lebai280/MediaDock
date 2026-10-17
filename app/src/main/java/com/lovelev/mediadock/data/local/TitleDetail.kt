package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 详情页的补充信息：类型、制片国家、时长、完整上映日期、演职人员。
 *
 * **和 `media_items` 分开、而且是「打开详情页时才拉」的**，理由有两个：
 *  - 整库刮削时不需要这些（92 条就是 92 次额外请求，白等一两分钟）；
 *  - 演员表的 JSON 挺大，塞进媒体条目表不值当。
 * 拉过一次就缓存住，之后不再请求。
 */
@Entity(tableName = "title_details")
data class TitleDetail(
    @PrimaryKey val path: String,
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(detail: TitleDetail)

    /** 手动改匹配后，旧的类型/演职人员缓存要作废。 */
    @Query("DELETE FROM title_details WHERE path IN (:paths)")
    suspend fun deleteAll(paths: List<String>)
}
