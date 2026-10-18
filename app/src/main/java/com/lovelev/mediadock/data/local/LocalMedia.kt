package com.lovelev.mediadock.data.local

/**
 * 本地媒体在库里的路径编码。
 *
 * 媒体库的 `path` 列同时装三种来源，靠前缀/协议区分 —— **刻意不新增数据库列**：
 * 那要写一次迁移（而迁移是这个项目风险最高的环节，见 WORKLOG 问题 39/45），
 * 而这里纯粹是个字符串约定，写错也只是扫描结果不对，碰不到用户数据。
 *
 * | 来源 | 存进 `path` 的形式 | 播放时怎么变成地址 |
 * |---|---|---|
 * | WebDAV | 相对数据源根的路径（**没有**前缀） | 用数据源根拼 |
 * | SAF 选的文件夹 | `local/<相对目录树根的路径>` | 用「树 URI + 相对路径」现拼 |
 * | 系统媒体库 | `content://media/...` 原样 | 直接就是地址 |
 *
 * **SAF 那条为什么存相对路径而不是 URI**：SAF 的 document URI 依赖那次授权拿到的树，
 * 而 `content://media/...` 这种是系统给的稳定 id、跨重启不变。所以一个要现拼、一个直接存。
 */
object LocalMedia {

    /** SAF 条目的前缀。 */
    const val SAF_PREFIX = "local/"

    /** 系统媒体库条目前缀（就是它自己的 URI 协议）。 */
    const val CONTENT_SCHEME = "content://"

    /** 传给 DAO 的两个 LIKE 模式，用来「只删本地那一半」。 */
    val SAF_LIKE = "$SAF_PREFIX%"
    val CONTENT_LIKE = "$CONTENT_SCHEME%"

    /** 是不是本地条目（两种来源都算）。WebDAV 的相对路径两条都不匹配。 */
    fun isLocal(path: String): Boolean =
        path.startsWith(SAF_PREFIX) || path.startsWith(CONTENT_SCHEME)

    /** 是不是「本身就是地址」的那种（媒体库）。 */
    fun isContentUri(path: String): Boolean = path.startsWith(CONTENT_SCHEME)

    /** 相对目录树根的路径 → 库里的路径。 */
    fun encode(relative: String): String = SAF_PREFIX + relative.trimStart('/')

    /** 扫到的 key → 库里的路径。媒体库的 URI 原样存。 */
    fun toStoredPath(key: String): String =
        if (isContentUri(key)) key else encode(key)

    /** 库里的路径 → 相对目录树根的路径。不是 SAF 条目就返回 null。 */
    fun decode(path: String): String? =
        if (path.startsWith(SAF_PREFIX)) path.removePrefix(SAF_PREFIX) else null

    /**
     * 把「树 document id」和相对路径拼成子项的 document id。
     *
     * SAF 的约定：树根的 document id 形如 `primary:Movies`，
     * 它下面的 `sub/a.mp4` 就是 `primary:Movies/sub/a.mp4`。
     * 纯字符串拼接，**不做 URI 编码** —— 交给 `DocumentsContract` 去做。
     */
    fun childDocumentId(treeDocumentId: String, relative: String): String {
        val trimmed = relative.trim('/')
        return if (trimmed.isEmpty()) treeDocumentId else "$treeDocumentId/$trimmed"
    }
}
