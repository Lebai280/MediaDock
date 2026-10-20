package com.lovelev.mediadock.data.repo

import com.lovelev.mediadock.core.data.identityOf
import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaLibraryStore
import com.lovelev.mediadock.data.local.Playlist
import com.lovelev.mediadock.data.local.PlaylistItem
import kotlinx.coroutines.flow.Flow

/** 用户片单：创建、添加/移除条目、按片单取路径队列。 */
class PlaylistRepository(
    private val db: MediaDatabase,
    private val store: MediaLibraryStore,
) {

    fun playlists(): Flow<List<Playlist>> = db.playlistDao().observeAll()

    fun itemCount(id: Long): Flow<Int> = db.playlistDao().observeCount(id)

    fun items(id: Long): Flow<List<PlaylistItem>> = db.playlistDao().observeItems(id)

    suspend fun create(name: String): Long = store.createPlaylist(name)

    suspend fun add(id: Long, path: String) {
        val identity = identityOf(path)
        store.addToPlaylist(
            playlistId = id,
            path = path,
            title = identity.title,
            year = identity.year,
            season = identity.season,
            episode = identity.episode,
        )
    }

    suspend fun remove(id: Long, path: String) = store.removeFromPlaylist(id, path)

    suspend fun delete(id: Long) = store.deletePlaylist(id)

    suspend fun paths(id: Long): List<String> = db.playlistDao().items(id).map { it.path }

    /**
     * 片单条目的播放队列：剧集展开为整剧的分集（按季/集排序），电影只播选中的版本。
     * 解析不到作品时退回单条路径，保证永远能播。
     */
    suspend fun playQueue(path: String): List<String> {
        val resolved = store.resolve(path) ?: return listOf(path)
        val type = db.mediaDao().workType(resolved.workId)
        return if (type == "tv") {
            db.mediaDao().episodePathsOfWork(resolved.workId).ifEmpty { listOf(path) }
        } else {
            listOf(path)
        }
    }
}
