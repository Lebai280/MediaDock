package com.lovelev.mediadock.data.local

import androidx.room.Dao
import androidx.room.DatabaseView
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 手工改过的片名/年份 —— **v9 起是只读兼容 VIEW**。覆盖按作品存；
 * 视图把作品展开成所有文件 path，保证整部剧的每一集都显示改过的名字。
 */
@DatabaseView(viewName = "title_overrides_view", value = ViewSql.TITLE_OVERRIDES)
data class TitleOverride(
    val path: String,
    val title: String,
    val year: Int?,
)

@Dao
interface TitleOverrideDao {

    @Query("SELECT * FROM title_overrides_view")
    fun observeAll(): Flow<List<TitleOverride>>
}
