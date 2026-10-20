package com.lovelev.mediadock.data.tmdb

/**
 * TMDB 搜索结果的打分与选取（纯函数，便于单测）。
 *
 * 旧逻辑直接取「第一条有海报的结果」，短标题/年份偏差时会挑错。现在按
 * 标题相似度 + 年份接近度打分，且**没有标题关联的候选直接淘汰**，避免乱匹配。
 */
internal object TmdbScoring {

    /** 标题归一化：小写、只保留字母数字（去掉空格/标点/全角符号）。 */
    fun normalize(raw: String): String = buildString {
        for (ch in raw.lowercase()) {
            if (ch.isLetterOrDigit()) append(ch)
        }
    }

    /** 标题关联分；0 表示完全不相关。 */
    private fun titleScore(query: String, result: SearchResult): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val titles = listOfNotNull(
            result.title,
            result.name,
            result.original_title,
            result.original_name,
        ).map(::normalize).filter { it.isNotEmpty() }
        var best = 0
        for (t in titles) {
            val score = when {
                t == q -> 100
                t.startsWith(q) -> 70
                q.length >= 2 && q.startsWith(t) -> 60
                t.contains(q) -> 50
                t.length >= 2 && q.contains(t) -> 40
                else -> 0
            }
            if (score > best) best = score
        }
        return best
    }

    fun score(query: String, year: Int?, result: SearchResult): Int {
        val title = titleScore(query, result)
        if (title == 0) return 0
        var total = title
        val resultYear = (result.release_date ?: result.first_air_date)
            ?.take(4)?.toIntOrNull()
        if (year != null && resultYear != null) {
            total += when (resultYear - year) {
                0 -> 40
                -1, 1 -> 25
                -2, 2 -> 10
                else -> -20
            }
        }
        if (result.poster_path != null) total += 3
        total += ((result.popularity ?: 0.0) / 100.0).toInt().coerceIn(0, 5)
        return total
    }

    /** 最低可接受分：必须与查询标题有直接关联。 */
    private const val MIN_SCORE = 40

    fun pick(query: String, year: Int?, results: Collection<SearchResult>): SearchResult? =
        results
            .map { it to score(query, year, it) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second >= MIN_SCORE }
            ?.first

    /**
     * 标题变体（用于首轮无结果时补搜）：
     * 去掉「第N季 / Season N / S01」后缀、去掉【】（）内容、取冒号前的部分。
     */
    fun queryVariants(title: String): List<String> {
        val base = title.trim()
        if (base.isEmpty()) return emptyList()
        val variants = linkedSetOf(base)
        val noSeason = base.replace(
            Regex("(第\\s*[0-9一二三四五六七八九十]+\\s*[季部]|season\\s*\\d+|s\\d{1,2})$",
                RegexOption.IGNORE_CASE),
            "",
        ).trim()
        if (noSeason.isNotEmpty()) variants += noSeason
        val noBrackets = base
            .replace(Regex("[【\\[（(][^】\\]）)]*[】\\]）)]"), "")
            .trim()
        if (noBrackets.isNotEmpty()) variants += noBrackets
        val beforeColon = base.substringBefore('：').substringBefore(':').trim()
        if (beforeColon.length >= 2 && beforeColon != base) variants += beforeColon
        return variants.toList()
    }
}
