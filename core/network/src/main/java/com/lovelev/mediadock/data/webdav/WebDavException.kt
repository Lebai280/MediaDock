package com.lovelev.mediadock.data.webdav

/**
 * WebDAV 交互的可分类错误。
 *
 * 目标服务器（OpenList/AList）语义：
 *  - 401 密码错误、429 账号被锁定、404 路径失效。
 *  - **401/429 不得重试**：OpenList 的失败锁定按来源 IP 计数，而 nginx 反向代理使所有客户端
 *    显示为同一 IP，任一客户端反复试错可能导致所有 WebDAV 客户端被锁定。
 */
sealed class WebDavException(
    val userMessage: String,
    cause: Throwable? = null,
) : Exception(userMessage, cause) {

    /** 401：凭据不正确。**调用方不得重试**。 */
    class Unauthorized : WebDavException(
        "账号或密码不正确。请确认后手动重试；请勿反复尝试，" +
            "重复失败将触发服务器锁定，导致其他设备无法访问。",
    )

    /** 429：已被服务器锁定。 */
    class Locked : WebDavException(
        "服务器已暂时锁定登录（通常由于此前有客户端密码错误）。" +
            "请等待一段时间，期间请勿重复尝试。",
    )

    /** 404：路径不存在（不是网络问题）。 */
    class NotFound : WebDavException(
        "地址或路径不存在。请检查 WebDAV 地址是否以 /dav 结尾。",
    )

    class Http(val code: Int, detail: String?) : WebDavException(
        "服务器返回 HTTP $code${if (detail.isNullOrBlank()) "" else "：$detail"}",
    )

    class Network(cause: Throwable?) : WebDavException(
        "无法连接服务器：${cause?.message ?: "网络错误"}",
        cause,
    )

    /** 响应不是合法的 multistatus XML。 */
    class Malformed(detail: String) : WebDavException("服务器响应无法解析：$detail")

    /** 本地配置缺失（地址/凭据），不是服务器 401。 */
    class Config(message: String) : WebDavException(message)
}
