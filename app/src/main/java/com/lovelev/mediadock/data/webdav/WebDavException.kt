package com.lovelev.mediadock.data.webdav

/**
 * WebDAV 交互的可分类错误。
 *
 * 注意目标服务器（OpenList/AList）的语义：
 *  - 401 密码错、429 账号被锁定、404 路径失效。
 *  - **401/429 绝不重试**：OpenList 的失败锁定按来源 IP 计数，而 nginx 反代会让所有客户端
 *    都显示成同一个 IP，所以任何一个客户端反复试错会把所有人的 WebDAV 一起锁死。
 */
sealed class WebDavException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    /** 401：凭据不正确。**调用方不得重试**。 */
    class Unauthorized : WebDavException(
        "账号或密码不正确。请确认后手动再试 —— 不要反复重试：" +
            "重复失败会触发服务器锁定，导致其他设备也无法访问。",
    )

    /** 429：已被服务器锁定。 */
    class Locked : WebDavException(
        "服务器已暂时锁定登录（通常是之前有客户端输错密码）。" +
            "请等待一段时间，期间不要重复尝试。",
    )

    /** 404：路径不存在（不是网络问题）。 */
    class NotFound : WebDavException(
        "地址或路径不存在。请检查 WebDAV 地址是否以 /dav 结尾。",
    )

    class Http(val code: Int, detail: String?) : WebDavException(
        "服务器返回 HTTP $code${if (detail.isNullOrBlank()) "" else "：$detail"}",
    )

    class Network(cause: Throwable?) : WebDavException(
        "连不上服务器：${cause?.message ?: "网络错误"}",
        cause,
    )

    /** 响应不是合法的 multistatus XML。 */
    class Malformed(detail: String) : WebDavException("服务器响应无法解析：$detail")
}
