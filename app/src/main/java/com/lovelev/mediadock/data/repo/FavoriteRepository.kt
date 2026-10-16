package com.lovelev.mediadock.data.repo

import com.lovelev.mediadock.data.local.Favorite
import com.lovelev.mediadock.data.local.MediaDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoriteRepository(private val db: MediaDatabase) {

    /** 只给界面一个 path 集合，够判断某一条有没有被收藏。 */
    fun observePaths(): Flow<Set<String>> =
        db.favoriteDao().observeAll().map { list -> list.map { it.path }.toSet() }

    /** 翻转收藏。**以库里的当前状态为准** —— 界面那份 Flow 有订阅延迟，不能拿来判方向。 */
    suspend fun toggle(path: String) {
        if (db.favoriteDao().countOf(path) > 0) {
            db.favoriteDao().remove(path)
        } else {
            db.favoriteDao().add(Favorite(path = path, addedAt = System.currentTimeMillis()))
        }
    }
}
