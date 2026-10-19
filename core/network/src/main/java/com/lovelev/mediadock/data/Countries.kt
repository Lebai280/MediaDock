package com.lovelev.mediadock.data

/**
 * 制片国家/地区：iso-3166-1 → 中文名。
 *
 * **为什么要有这张表**：TMDB 的详情接口即使 `language=zh-CN`，国家名也**只返回英文**
 * （实测 `United States of America`），而用户要的详情页是「日本 / 犯罪,剧情」这种中文。
 * 表里没有的就退回接口给的英文名，不编。
 */
object Countries {

    private val NAMES = mapOf(
        "US" to "美国", "CN" to "中国大陆", "HK" to "中国香港", "TW" to "中国台湾",
        "JP" to "日本", "KR" to "韩国", "GB" to "英国", "FR" to "法国", "DE" to "德国",
        "IT" to "意大利", "ES" to "西班牙", "PT" to "葡萄牙", "NL" to "荷兰", "BE" to "比利时",
        "CH" to "瑞士", "AT" to "奥地利", "SE" to "瑞典", "NO" to "挪威", "DK" to "丹麦",
        "FI" to "芬兰", "IS" to "冰岛", "IE" to "爱尔兰", "PL" to "波兰", "CZ" to "捷克",
        "HU" to "匈牙利", "RO" to "罗马尼亚", "GR" to "希腊", "RU" to "俄罗斯", "UA" to "乌克兰",
        "TR" to "土耳其", "IL" to "以色列", "IR" to "伊朗", "IN" to "印度", "TH" to "泰国",
        "VN" to "越南", "MY" to "马来西亚", "SG" to "新加坡", "ID" to "印度尼西亚",
        "PH" to "菲律宾", "CA" to "加拿大", "MX" to "墨西哥", "BR" to "巴西", "AR" to "阿根廷",
        "CL" to "智利", "CO" to "哥伦比亚", "AU" to "澳大利亚", "NZ" to "新西兰",
        "ZA" to "南非", "EG" to "埃及", "MA" to "摩洛哥", "SA" to "沙特阿拉伯",
        "AE" to "阿联酋", "PK" to "巴基斯坦", "BD" to "孟加拉国", "NP" to "尼泊尔",
    )

    /** [isoCodes] 里第一个认得的转成中文；都不认得就返回 [fallback]（接口给的英文名）。 */
    fun label(isoCodes: List<String>, fallback: String?): String? {
        isoCodes.forEach { code -> NAMES[code.uppercase()]?.let { return it } }
        return fallback?.takeIf { it.isNotBlank() }
    }
}
