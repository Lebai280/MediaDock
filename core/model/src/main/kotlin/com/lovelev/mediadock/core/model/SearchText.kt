package com.lovelev.mediadock.core.model

/**
 * FTS4 的索引文本与查询构造（S2 / ADR-024）。
 *
 * - 索引：CJK 字符之间补空格，`unicode61` 才能逐字命中；
 * - 查询：CJK 词转为逐字短语（`"芙 莉 莲"`），拉丁词按前缀（`"abc"*`）。
 * 目标机不支持 FTS5 trigram，因此不得改成 trigram 方案。
 */
object SearchText {

    /** 把标题/类型/演员拼成索引文本。 */
    fun index(vararg parts: String?): String =
        parts.filterNotNull()
            .joinToString(" ")
            .let(::cjkSpace)

    /** 空查询返回 null（调用方不要走 FTS）。 */
    fun query(raw: String): String? {
        val terms = raw.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return null
        return terms.joinToString(" ") { term ->
            if (term.any(::isCjk)) {
                val chars = term.filter(::isCjk).toList().joinToString(" ")
                if (chars.isEmpty()) quote(term) else "\"$chars\""
            } else {
                // FTS4 的前缀必须是不带引号的 token；带引号会被当成精确短语。
                if (PLAIN_TERM.matches(term)) "$term*" else quote(term)
            }
        }
    }

    private fun cjkSpace(text: String): String {
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            if (isCjk(ch)) sb.append(' ').append(ch).append(' ') else sb.append(ch)
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun quote(term: String): String = "\"${term.replace("\"", "\"\"")}\""

    private fun isCjk(ch: Char): Boolean =
        ch.code in 0x3400..0x4DBF || ch.code in 0x4E00..0x9FFF

    private val PLAIN_TERM = Regex("^[\\p{L}\\p{N}_]+$")
}
