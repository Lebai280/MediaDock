package com.lovelev.mediadock.data.webdav

import com.lovelev.mediadock.data.prefs.StoredSource
import com.lovelev.mediadock.data.subtitle.OpenSubtitlesCredentials
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
 * TMDB 密钥：xxxx            （有才写）
 * 字幕 API Key：xxxx         （有才写）
 * 字幕账号：demo             （有才写）
 * 字幕密码：xxxx             （有才写）
 * ```
 *
 * 解析是**宽容**的：标签认 地址/服务器/链接/url、账号/用户名/用户名/user、密码/pass/密码 等中英写法，
 * 也认 `https://user:pass@host/dav` 这种把账号密码塞在 URL 里的写法。
 * 字幕行在解析数据源前会被整行剥掉，避免「字幕账号」被当成 WebDAV 账号。
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

    /** 字幕源凭据（OpenSubtitles）：`字幕 API Key：xxx` / `字幕账号：xxx` / `字幕密码：xxx`。 */
    private val LABELED_SUB_KEY = Regex(
        """(?:字幕\s*(?:api\s*key|密钥|key)|opensubtitles\s*(?:api\s*key|key))\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )
    private val LABELED_SUB_USER = Regex(
        """(?:字幕\s*(?:账号|帐号|用户名)|opensubtitles\s*(?:user(?:name)?|account))\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )
    private val LABELED_SUB_PASS = Regex(
        """(?:字幕\s*(?:密码|口令)|opensubtitles\s*(?:pass(?:word)?|pwd))\s*[：:=]\s*(\S+)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 生成给朋友发的文本。
     *
     * [tmdbSecret]、[subtitleApiKey] / [subtitleUser] / [subtitlePassword] 都是**可选**的：
     * 它们属于全局设置（不随数据源走），但一起发过去朋友装上就能直接刮削、搜字幕，
     * 不用再问你一遍。没有就不写对应的行。
     */
    fun build(
        url: String,
        username: String,
        password: String,
        rootPath: String,
        tmdbSecret: String? = null,
        subtitleApiKey: String? = null,
        subtitleUser: String? = null,
        subtitlePassword: String? = null,
    ): String = buildString {
        appendLine("Mooring 数据源")
        appendLine("地址：$url")
        appendLine("账号：$username")
        appendLine("密码：$password")
        if (rootPath.isNotBlank()) appendLine("根路径：$rootPath")
        if (!tmdbSecret.isNullOrBlank()) appendLine("TMDB 密钥：$tmdbSecret")
        if (!subtitleApiKey.isNullOrBlank()) appendLine("字幕 API Key：$subtitleApiKey")
        if (!subtitleUser.isNullOrBlank()) appendLine("字幕账号：$subtitleUser")
        if (!subtitlePassword.isNullOrBlank()) appendLine("字幕密码：$subtitlePassword")
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
     * 从分享文本里取字幕源凭据（可选）。至少要有 API Key 才返回 ——
     * 只有账号密码时搜不了字幕，没有意义。
     */
    fun parseSubtitle(text: String): OpenSubtitlesCredentials? {
        val key = LABELED_SUB_KEY.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        if (key.isBlank()) return null
        return OpenSubtitlesCredentials(
            apiKey = key,
            username = LABELED_SUB_USER.find(text)?.groupValues?.get(1)?.trim().orEmpty(),
            password = LABELED_SUB_PASS.find(text)?.groupValues?.get(1)?.trim().orEmpty(),
        )
    }

    /**
     * 字幕行含「字幕账号 / 字幕密码」，直接丢给 [parse] 的「账号 / 密码」正则会串味
     * （把字幕账号当成 WebDAV 账号）。解析数据源前先把这几种行整行剥掉。
     */
    private fun stripSubtitleLines(text: String): String =
        text.lineSequence()
            .filterNot { SUBTITLE_LINE.containsMatchIn(it) }
            .joinToString("\n")

    private val SUBTITLE_LINE = Regex(
        """(?:字幕\s*(?:api\s*key|密钥|key|账号|帐号|用户名|密码|口令)|opensubtitles\s*\S*(?:\s*key|\s*user|\s*pass\S*)?)\s*[：:=]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 认出一段分享文本。**地址和密码都得有**才算 —— 剪贴板中的任意链接不得被当作配置。
     * 认不出来返回 null（界面不得修改用户输入）。
     */
    fun parse(text: String): StoredSource? {
        if (text.isBlank()) return null
        // 字幕凭据单独解析：先剥掉字幕行，避免「字幕账号」被当成 WebDAV 账号
        val body = stripSubtitleLines(text)

        // 1) 地址：优先带协议的；否则看带标签的那行（可能是裸 host）
        var url = URL_PATTERN.find(body)?.value
        if (url == null) {
            val labeled = LABELED_URL.find(body)?.groupValues?.get(1) ?: return null
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
        LABELED_USER.find(body)?.groupValues?.get(1)?.let { username = it }
        LABELED_PASSWORD.find(body)?.groupValues?.get(1)?.let { password = it }
        val rootPath = LABELED_ROOT.find(body)?.groupValues?.get(1).orEmpty()

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
