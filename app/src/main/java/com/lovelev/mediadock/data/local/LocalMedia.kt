package com.lovelev.mediadock.data.local

/**
 * 本地媒体在库里的路径编码。
 *
 * 媒体库的 `path` 列现在同时装两种来源：WebDAV 的相对路径、以及本地文件。
 * 本地条目统一加 [PREFIX] 前缀区分 —— **刻意不新增数据库列**：
 * 那要写一次迁移（而迁移是这个项目最容易出事的地方，见 WORKLOG 坑 39/45），
 * 而这里纯粹是个字符串约定，写错也只是扫描结果不对，不会碰用户数据。
 *
 * ⚠️ 库里存的是**相对目录树根**的路径，不是 `content://` URI。
 * SAF 给的 document URI 依赖那次授权拿到的树，不能当持久主键用；
 * 播放时用「树 URI + 相对路径」现拼（见 [buildDocumentUri]）。
 */
object LocalMedia {

    const val PREFIX = "local/"

    fun isLocal(path: String): Boolean = path.startsWith(PREFIX)

    /** 相对目录树根的路径 → 库里的路径。 */
    fun encode(relative: String): String = PREFIX + relative.trimStart('/')

    /** 库里的路径 → 相对目录树根的路径。不是本地条目就返回 null。 */
    fun decode(path: String): String? =
        if (isLocal(path)) path.removePrefix(PREFIX) else null

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
