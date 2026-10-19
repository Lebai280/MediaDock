package com.lovelev.mediadock.data.scan

import com.lovelev.mediadock.core.data.scan.DirectoryLister
import com.lovelev.mediadock.core.data.scan.DirectoryListing
import com.lovelev.mediadock.core.data.scan.RemoteEntry
import com.lovelev.mediadock.data.prefs.StoredSource
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavException
import com.lovelev.mediadock.data.webdav.WebDavUrl

/**
 * WebDAV 目录列举：把 [WebDavClient.propfindWithCache] 适配成扫描引擎的 [DirectoryLister]。
 *
 * 凭据每次从 [source] 读取（用户重新登录后无需重建 Worker）。
 */
class WebDavDirectoryLister(
    private val client: WebDavClient,
    private val source: () -> StoredSource?,
) : DirectoryLister {

    override suspend fun list(
        remoteKey: String,
        etag: String?,
        lastModified: String?,
    ): DirectoryListing {
        val saved = source() ?: throw WebDavException.Config("数据源不可用，请重新添加")
        val root = WebDavUrl.rootCollection(saved.url, saved.rootPath)
            ?: throw WebDavException.Config("数据源地址无效，请重新填写")
        val collection = WebDavUrl.child(root, remoteKey, isCollection = true)
        val auth = WebDavClient.basicAuth(saved.username, saved.password)
        return when (val result = client.propfindWithCache(collection, root, auth, etag, lastModified)) {
            WebDavClient.PropfindResult.NotModified -> DirectoryListing(notModified = true)
            is WebDavClient.PropfindResult.Changed -> DirectoryListing(
                entries = result.listing.entries.map {
                    RemoteEntry(
                        path = it.path,
                        name = it.name,
                        isDirectory = it.isDirectory,
                        sizeBytes = it.size,
                        lastModified = it.lastModified,
                        etag = it.etag,
                    )
                },
                etag = result.listing.collectionEtag,
                lastModified = result.listing.collectionLastModified,
            )
        }
    }
}
