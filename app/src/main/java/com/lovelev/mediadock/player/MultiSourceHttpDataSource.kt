package com.lovelev.mediadock.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.webdav.WebDavClient
import okhttp3.OkHttpClient

/**
 * 给媒体流按**请求地址匹配对应的数据源**，再补上该源的鉴权头。
 *
 * 两个刻意的设计：
 *
 * 1. **逐次请求解析，而不是建工厂时写死。**
 *    凭据是异步读出来的；写死会有「播放器已 prepare、凭据还没读出来」的空窗，
 *    那一下会发出未鉴权请求，在 OpenList 上等于记一次登录失败。取不到就什么都不加，
 *    由播放器报错，绝不放未鉴权的请求出去。
 *
 * 2. **按根路径最长前缀匹配，而不是只比主机。**
 *    WebDAV 多个账号可以共用同一个域名（不同根路径），Emby 也可能与 WebDAV 同主机。
 *    只有 URL 落在某个源自己的根下时才发送它的凭据；未匹配到任何源就不加头。
 */
class MultiSourceHttpDataSource(
    private val client: OkHttpClient,
    private val sources: () -> List<SourceConfig>,
) : DataSource {

    private val delegate: OkHttpDataSource = OkHttpDataSource.Factory(client).createDataSource()

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val source = SourceUrlMatcher.bestMatch(
            sources(),
            SourceUrlMatcher.UrlParts(
                scheme = uri.scheme,
                host = uri.host,
                port = uri.port,
                path = uri.encodedPath.orEmpty(),
            ),
        )
        source?.let {
            when (it.type) {
                SourceType.WEBDAV -> delegate.setRequestProperty(
                    "Authorization",
                    WebDavClient.basicAuth(it.username, it.password),
                )
                SourceType.EMBY, SourceType.JELLYFIN -> {
                    val token = it.apiKey?.takeIf { key -> key.isNotBlank() } ?: it.password
                    if (token.isNotBlank()) delegate.setRequestProperty("X-Emby-Token", token)
                }
                else -> Unit
            }
        }
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun addTransferListener(transferListener: TransferListener) {
        delegate.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = delegate.uri

    override fun close() {
        delegate.close()
    }

    class Factory(
        private val client: OkHttpClient,
        private val sources: () -> List<SourceConfig>,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = MultiSourceHttpDataSource(client, sources)
    }
}
