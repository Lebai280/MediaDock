package com.lovelev.mediadock.data.source

import com.lovelev.mediadock.core.model.SourceType

/**
 * 一个数据源的完整配置（含解密后的凭据，**只在内存中使用**）。
 *
 * 密码/API Key 落盘前由 [com.lovelev.mediadock.data.prefs.SecureCredentialStore] 加密；
 * 日志、分享、诊断包都不得输出这些字段。
 */
data class SourceConfig(
    val id: String,
    val type: SourceType,
    val name: String,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    /** WebDAV 根路径。 */
    val rootPath: String = "",
    /** SMB 共享名。 */
    val share: String = "",
    /** Emby/Jellyfin 的 API Key（可选，二选一登录）。 */
    val apiKey: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    /** 凭据解密失败时为 false：界面提示重新填写，扫描/播放必须跳过。 */
    val credentialOk: Boolean = true,
) {
    val isFileSource: Boolean
        get() = type == SourceType.WEBDAV || type == SourceType.SMB

    val isCatalogSource: Boolean
        get() = type == SourceType.EMBY || type == SourceType.JELLYFIN

    /** 需要（或可以）进入扫描/同步流程的源。 */
    val isScannable: Boolean
        get() = isFileSource || isCatalogSource
}
