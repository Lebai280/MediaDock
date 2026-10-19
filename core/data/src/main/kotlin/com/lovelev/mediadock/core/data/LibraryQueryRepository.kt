package com.lovelev.mediadock.core.data

import com.lovelev.mediadock.data.local.MediaDatabase
import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleOverride
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

    /** 海报墙分页：30 条/页，由 works 表驱动。 */
    fun worksPager(
        sort: WorksSort = WorksSort.TITLE,
        search: String = "",
    ): Flow<PagingData<WorkCard>> = worksPager(WorksFilter(query = search, sort = sort))

    /** 搜索/筛选分页：FTS + SQL 过滤与排序。 */
    fun worksPager(filter: WorksFilter): Flow<PagingData<WorkCard>> =
        Pager(
            config = PagingConfig(
                pageSize = 30,
                initialLoadSize = 60,
                prefetchDistance = 10,
                enablePlaceholders = false,
            ),
        ) {
            db.worksCardDao().pagingWorks(WorksCardSql.paging(filter))
        }.flow.map { data -> data.map { it.toWorkCard() } }

    /** 首页「随机推荐」。 */
    suspend fun randomWorks(limit: Int = 5): List<WorkCard> =
        db.worksCardDao().cards(WorksCardSql.random(limit)).map { it.toWorkCard() }

    /** 分类行用的有界集合（按最近添加取 [limit] 部）。 */
    suspend fun recentWorks(limit: Int = 200): List<WorkCard> =
        db.worksCardDao().cards(WorksCardSql.limited(WorksSort.RECENT, limit))
            .map { it.toWorkCard() }

    /** 推荐理由用的收藏样本（有界）。 */
    suspend fun favoriteWorks(limit: Int = 20): List<WorkCard> =
        db.worksCardDao().cards(WorksCardSql.favorites(limit)).map { it.toWorkCard() }
}

/** 把手工覆盖并进条目：只改显示/检索用的片名与年份，不动路径。 */
fun MediaItemEntity.withTitleOverride(override: TitleOverride?): MediaItemEntity =
    if (override == null) this else copy(parsedTitle = override.title, year = override.year)
