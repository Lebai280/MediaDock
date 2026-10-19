package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 收藏 —— **v9 起是只读兼容 VIEW**。收藏按作品（works）存；
 * 视图把作品展开成它的所有文件 path，界面「某条是否被收藏」的判定保持一致。
 */
@DatabaseView(viewName = "favorites_view", value = ViewSql.FAVORITES)
data class Favorite(
    val path: String,
    val addedAt: Long,
)

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites_view ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Favorite>>

    /** 翻转收藏前先问库一次，别依赖界面那份可能还没同步的快照。 */
    @Query("SELECT COUNT(*) FROM favorites_view WHERE path = :path")
    suspend fun countOf(path: String): Int
}
