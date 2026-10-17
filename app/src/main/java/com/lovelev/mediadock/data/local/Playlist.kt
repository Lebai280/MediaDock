package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 用户自建片单。 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** 片单条目（按 path 引用媒体库条目）。 */
@Entity(tableName = "playlist_items", primaryKeys = ["playlistId", "path"])
data class PlaylistItem(
    val playlistId: Long,
    val path: String,
    val addedAt: Long,
)

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY createdAt")
    fun observeAll(): Flow<List<Playlist>>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :id")
    fun observeCount(id: Long): Flow<Int>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY addedAt")
    fun observeItems(id: Long): Flow<List<PlaylistItem>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :id ORDER BY addedAt")
    suspend fun items(id: Long): List<PlaylistItem>

    @Insert
    suspend fun insert(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addItem(item: PlaylistItem)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id AND path = :path")
    suspend fun removeItem(id: Long, path: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :id")
    suspend fun clearItems(id: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)
}
