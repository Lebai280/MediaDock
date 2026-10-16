package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 收藏。**独立表**（和进度同样的理由）：媒体条目表每次扫描都整表替换，
 * 挂在上面会被扫没。
 */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val path: String,
    val addedAt: Long,
)

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<Favorite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun remove(path: String)

    /** 翻转收藏前先问库一次，别依赖界面那份可能还没同步的快照。 */
    @Query("SELECT COUNT(*) FROM favorites WHERE path = :path")
    suspend fun countOf(path: String): Int
}
