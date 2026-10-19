package com.lovelev.mediadock.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 用户自建片单。v9 起带 user_id，为 Phase 5 多资料预留。 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * 片单条目 —— **v9 起是只读兼容 VIEW**。片单按作品存；
 * 视图为每个作品挑一个代表文件 path，保持现有「片单 → 播放」的 path 接口。
 */
@DatabaseView(viewName = "playlist_items_view", value = ViewSql.PLAYLIST_ITEMS)
data class PlaylistItem(
    val playlistId: Long,
    val path: String,
    val addedAt: Long,
)

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY created_at")
    fun observeAll(): Flow<List<Playlist>>

    @Query("SELECT COUNT(*) FROM playlist_items_view WHERE playlistId = :id")
    fun observeCount(id: Long): Flow<Int>

    @Query("SELECT * FROM playlist_items_view WHERE playlistId = :id ORDER BY addedAt")
    fun observeItems(id: Long): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items_view WHERE playlistId = :id ORDER BY addedAt")
    suspend fun items(id: Long): List<PlaylistItem>
}
