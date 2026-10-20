package com.lovelev.mediadock.data.webdav

import com.lovelev.mediadock.data.prefs.StoredSource
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 「分享给朋友」用的数据源配置：一段**人能看懂、程序也能认**的文本。
 *
 * 场景：用户把播放器给朋友用，朋友手输 地址/账号/密码 很容易错。于是这边生成一段文本，
 * 朋友复制过去、打开「添加数据源」页，App **自动识别剪贴板并填好**（见 AddSourceScreen）。
 *
 * 生成的样子：
 * ```
 * Mooring 数据源
 * 地址：https://files.example.com/dav
 * 账号：demo
 * 密码：xxxx-xxxx
 * 根路径：/movies            （有才写）
 * ```
 *
 * 解析是**宽容**的：标签认 地址/服务器/链接/url、账号/用户名/用户名/user、密码/pass/密码 等中英写法，
 * 也认 `https://user:pass@host/dav` 这种把账号密码塞在 URL 里的写法。
 * 纯逻辑，**有单测**（ShareConfigTest）—— 改规则先看那个。
 */
internal object ShareConfig {

    private val URL_PATTERN = Regex("""https?://[^\s"'<>（）()【】,，。、;；]+""", RegexOption.IGNORE_CASE)

    /** `地址：xxx` / `url: xxx` 这种带标签的，值是没带协议的裸地址时补 https。 */
    private val LABELED_URL = Regex(
        """(?:地址|服务器|链接|url|server)\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )
    private val LABELED_USER = Regex(
        """(?:用户名|账号|帐号|用户|账户|user(?:name)?|login|account)\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )
    private val LABELED_PASSWORD = Regex(
        """(?:密码|口令|pass(?:word)?|pwd)\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )
    private val LABELED_ROOT = Regex(
        """(?:根路径|root(?:\s*path)?)\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )

    /** TMDB 凭据：`TMDB 密钥：xxx` / `tmdb api key: xxx`。值是 JWT 或 32 位 Key，`\S+` 都吃得下。 */
    private val LABELED_TMDB = Regex(
        """tmdb(?:\s*(?:密钥|api\s*key|key|token))?\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 生成给朋友发的文本。
     *
     * [tmdbSecret] 是**可选**的：TMDB 属于全局设置（不随数据源走），但一起发过去朋友装上就能刮削，
     * 不用再问你一遍。没有就不写那一行。
     */
    fun build(
        url: String,
        username: String,
        password: String,
        rootPath: String,
        tmdbSecret: String? = null,
    ): String = buildString {
        appendLine("Mooring 数据源")
        appendLine("地址：$url")
        appendLine("账号：$username")
        appendLine("密码：$password")
        if (rootPath.isNotBlank()) appendLine("根路径：$rootPath")
        if (!tmdbSecret.isNullOrBlank()) appendLine("TMDB 密钥：$tmdbSecret")
    }

    /**
     * 从分享文本里取 TMDB 密钥（可选，取不到返回 null）。
     *
     * **只在 [parse] 认得这段文本时才该用它的结果** —— 否则剪贴板里任意包含「tmdb」字样的
     * 文本就会把密钥写进设置。
     */
    fun parseTmdb(text: String): String? =
        LABELED_TMDB.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    /**
     * 认出一段分享文本。**地址和密码都得有**才算 —— 剪贴板中的任意链接不得被当作配置。
     * 认不出来返回 null（界面不得修改用户输入）。
     */
    fun parse(text: String): StoredSource? {
        if (text.isBlank()) return null

        // 1) 地址：优先带协议的；否则看带标签的那行（可能是裸 host）
        var url = URL_PATTERN.find(text)?.value
        if (url == null) {
            val labeled = LABELED_URL.find(text)?.groupValues?.get(1) ?: return null
            url = if (labeled.startsWith("http", ignoreCase = true)) labeled else "https://$labeled"
        }
        url = url.trimEnd('.', ',', ';', '，', '。', '、')

        // 2) 账号密码可能塞在 URL 里（https://user:pass@host/...）→ 拆出来，URL 里不留
        var username: String? = null
        var password: String? = null
        val schemeEnd = url.indexOf("://") + 3
        val authorityEnd = url.indexOf('/', schemeEnd).let { if (it < 0) url.length else it }
        val authority = url.substring(schemeEnd, authorityEnd)
        if (authority.contains('@')) {
            val userInfo = authority.substringBeforeLast('@')
            username = userInfo.substringBefore(':').takeIf { it.isNotBlank() }?.let(::decode)
            password = userInfo.substringAfter(':', "").takeIf { it.isNotBlank() }?.let(::decode)
            url = url.substring(0, schemeEnd) + authority.substringAfterLast('@') + url.substring(authorityEnd)
        }

        // 3) 带标签的写法覆盖/补全
        LABELED_USER.find(text)?.groupValues?.get(1)?.let { username = it }
        LABELED_PASSWORD.find(text)?.groupValues?.get(1)?.let { password = it }
        val rootPath = LABELED_ROOT.find(text)?.groupValues?.get(1).orEmpty()

        // 地址没协议就补 https（和 WebDavUrl.parse 的约定一致）
        if (!url.startsWith("http", ignoreCase = true)) url = "https://$url"

        val user = username.orEmpty().trim()
        val pass = password.orEmpty().trim()
        // 没密码就不认 —— 分享的核心就是免输密码；只有链接的话不算配置
        if (user.isBlank() && pass.isBlank()) return null
        if (pass.isBlank()) return null

        return StoredSource(
            url = url,
            username = user,
            password = pass,
            rootPath = rootPath.trim(),
        )
    }

    /** 生成时把账号密码里的特殊字符转义一下，塞进 URL 用。 */
    fun embedInUrl(url: String, username: String, password: String): String {
        val host = url.substringAfter("://")
        return url.substringBefore("://") + "://" + encode(username) + ":" + encode(password) + "@" + host
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
