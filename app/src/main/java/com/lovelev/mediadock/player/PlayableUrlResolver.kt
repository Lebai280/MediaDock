package com.lovelev.mediadock.player

import android.net.Uri
import android.provider.DocumentsContract
import com.lovelev.mediadock.core.model.SourceId
import com.lovelev.mediadock.core.model.SourcePaths
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.local.LocalMedia
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import com.lovelev.mediadock.data.smb.SmbUrl
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.webdav.WebDavUrl

/** 库路径 → 播放地址。播放页与 StreamInfo 探针共用，保证两处解析完全一致。 */
class PlayableUrlResolver(
    private val credentials: SecureCredentialStore,
) {

    /** 裸路径归旧 WebDAV 源；限定路径按前缀找源。 */
    fun sourceFor(
        path: String,
        byId: Map<String, SourceConfig>,
        known: Set<String>,
    ): SourceConfig? {
        val parsed = SourcePaths.parseKnown(path, known)
        return if (parsed != null) byId[parsed.sourceId.value] else byId[SourceId.WEBDAV.value]
    }

    /**
     * 一条队列项 → 真正要播的地址。
     *
     * 本地条目的地址是**现拼**的：库里存的是「相对目录树根的路径」，
     * SAF 的 document URI 依赖那次授权拿到的树，存下来重启就会失效（见 [LocalMedia]）。
     */
    suspend fun resolve(
        path: String,
        byId: Map<String, SourceConfig>,
        known: Set<String>,
    ): String? = resolve(path, sourceFor(path, byId, known), known)

    private suspend fun resolve(
        path: String,
        source: SourceConfig?,
        known: Set<String>,
    ): String? {
        // 系统媒体库的条目存的就是它自己的 content:// URI —— 跨重启稳定，直接当地址用
        if (LocalMedia.isContentUri(path)) return path
        val relative = LocalMedia.decode(path)
        if (relative != null) {
            val tree = credentials.loadLocalTree() ?: return null
            return buildLocalUri(Uri.parse(tree), relative)?.toString()
        }
        val key = SourcePaths.parseKnown(path, known)?.key ?: path
        return when (source?.type) {
            SourceType.WEBDAV -> WebDavUrl.rootCollection(source.url, source.rootPath)
                ?.let { WebDavUrl.child(it, key).toString() }
            SourceType.SMB -> SmbUrl.build(source, key)
            SourceType.EMBY, SourceType.JELLYFIN -> {
                val itemId = key.removePrefix("item:")
                if (itemId.isBlank()) null
                else "${source.url.trimEnd('/')}/Videos/$itemId/stream?static=true"
            }
            else -> null
        }
    }

    private fun buildLocalUri(treeUri: Uri, relative: String): Uri? = runCatching {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            LocalMedia.childDocumentId(treeId, relative),
        )
    }.getOrNull()
}
