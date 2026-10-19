package com.lovelev.mediadock.core.data

import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * 媒体库只读查询：海报墙、详情页与搜索共用的读模型。
 *
 * 只依赖 `:core:database`；数据来源仍是 v9 兼容 VIEW，UI 读到的是与 v8 相同的列。
 * 扫描、刮削等写路径不在这里（见 `:app` 的 LibraryRepository / MediaLibraryStore）。
 */
class LibraryQueryRepository(private val db: MediaDatabase) {

    /** 库里的条目，**已经把手工覆盖的片名/年份合并进来**。 */
    val items: Flow<List<MediaItemEntity>> =
        combine(db.mediaDao().observeAll(), db.titleOverrideDao().observeAll()) { all, overrides ->
            val byPath = overrides.associateBy { it.path }
            all.map { item -> item.withTitleOverride(byPath[item.path]) }
        }

    /** 全部已缓存的详情（首页搜索按类型/国家/演员过滤用）。 */
    val details: Flow<List<com.lovelev.mediadock.data.local.TitleDetail>> =
        db.titleDetailDao().observeAll()

    /**
     * 详情页要的那一批条目。
     * **空列表必须挡在这里** —— Room 会把 `IN (:paths)` 展开成 `IN ()`，那是 SQL 语法错。
     */
    fun observeByPaths(paths: List<String>): Flow<List<MediaItemEntity>> =
        if (paths.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(
                db.mediaDao().observeByPaths(paths),
                db.titleOverrideDao().observeAll(),
            ) { rows, overrides ->
                val byPath = overrides.associateBy { it.path }
                rows.map { item -> item.withTitleOverride(byPath[item.path]) }
            }
        }
}

/** 把手工覆盖并进条目：只改显示/检索用的片名与年份，不动路径。 */
fun MediaItemEntity.withTitleOverride(override: TitleOverride?): MediaItemEntity =
    if (override == null) this else copy(parsedTitle = override.title, year = override.year)
