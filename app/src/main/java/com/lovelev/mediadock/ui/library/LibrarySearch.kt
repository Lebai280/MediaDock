package com.lovelev.mediadock.ui.library

import com.lovelev.mediadock.data.local.MediaItemEntity
import com.lovelev.mediadock.data.local.TitleDetail

/**
 * 首页搜索的过滤规则。**纯逻辑，另有单测（LibrarySearchTest）** —— 改规则先看那个。
 *
 * 匹配范围：**片名（含手工改名）/ 类型 / 国家 / 演职人员**。
 * 查询词按空格切成多个关键词，**全中**才算命中（支持「日本 动画」这种叠加）。
 *
 * 类型 / 国家 / 演员来自详情表 —— 那张表是「打开详情页才拉」的懒加载，所以不一定齐；
 * 用首页 ⋯ 菜单里的「补齐影片信息」补齐后，这三项才全库可用。
 */
internal fun filterLibraryItems(
    items: List<MediaItemEntity>,
    details: List<TitleDetail>,
    rawQuery: String,
): List<MediaItemEntity> {
    val keywords = rawQuery.trim().lowercase().split(' ').filter { it.isNotBlank() }
    if (keywords.isEmpty()) return items

    val byPath = details.associateBy { it.path }
    return items.filter { item ->
        val detail = byPath[item.path]
        val haystack = buildString {
            append(item.parsedTitle).append(' ').append(item.name)
            detail?.let {
                append(' ').append(it.genres).append(' ').append(it.country.orEmpty())
                // 演员名就在这段 JSON 里，直接当文本找，省得为了搜索去解析每一条
                append(' ').append(it.castJson)
            }
        }.lowercase()
        keywords.all { haystack.contains(it) }
    }
}
