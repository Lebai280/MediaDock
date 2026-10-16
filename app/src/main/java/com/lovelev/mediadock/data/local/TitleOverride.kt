package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 手工改过的片名/年份。**独立表**（同进度、收藏的理由：媒体条目表每次扫描整表替换）。
 *
 * 改过之后：海报墙、详情页、以及**重新刮削用的检索词**都走这一份 ——
 * 这样「自动匹配错了」就有得救（实测 `银河` 会被匹配成《银河护卫队》）。
 */
@Entity(tableName = "title_overrides")
data class TitleOverride(
    @PrimaryKey val path: String,
    val title: String,
    val year: Int?,
)

@Dao
interface TitleOverrideDao {

    @Query("SELECT * FROM title_overrides")
    fun observeAll(): Flow<List<TitleOverride>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: TitleOverride)

    @Query("DELETE FROM title_overrides WHERE path = :path")
    suspend fun remove(path: String)
}
