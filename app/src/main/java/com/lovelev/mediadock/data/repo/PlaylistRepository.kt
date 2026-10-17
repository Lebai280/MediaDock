package com.lovelev.mediadock.data.repo

import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.Playlist
import com.lovelev.mediadock.data.local.PlaylistItem
import kotlinx.coroutines.flow.Flow

/** 用户片单：创建、添加/移除条目、按片单取路径队列。 */
class PlaylistRepository(private val db: MediaDatabase) {

    fun playlists(): Flow<List<Playlist>> = db.playlistDao().observeAll()

    fun itemCount(id: Long): Flow<Int> = db.playlistDao().observeCount(id)

    fun items(id: Long): Flow<List<PlaylistItem>> = db.playlistDao().observeItems(id)

    suspend fun create(name: String): Long =
        db.playlistDao().insert(Playlist(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun add(id: Long, path: String) {
        db.playlistDao().addItem(PlaylistItem(id, path, System.currentTimeMillis()))
    }

    suspend fun remove(id: Long, path: String) {
        db.playlistDao().removeItem(id, path)
    }

    suspend fun delete(id: Long) {
        db.playlistDao().clearItems(id)
        db.playlistDao().delete(id)
    }

    suspend fun paths(id: Long): List<String> = db.playlistDao().items(id).map { it.path }
}
