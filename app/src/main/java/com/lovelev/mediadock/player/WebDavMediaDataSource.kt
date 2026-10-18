package com.lovelev.mediadock.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.lovelev.mediadock.data.prefs.StoredSource
import com.lovelev.mediadock.data.webdav.WebDavClient
import com.lovelev.mediadock.data.webdav.WebDavUrl
import okhttp3.OkHttpClient
import java.io.IOException

/**
 * 给媒体流补上 WebDAV 的 Basic 鉴权。
 *
 * 两个刻意的设计：
 *
 * 1. **逐次请求补头，而不是建工厂时写死。**
 *    凭据是从 DataStore 异步读出来的（[com.lovelev.mediadock.data.prefs.SecureCredentialStore]），
 *    写死在工厂里就存在「播放器已经 prepare、凭据还没读出来」的空窗 ——
 *    那一下会发出**未鉴权**请求，在 OpenList 上等于记一次登录失败。
 *    改成 open() 时才取，就没有任何时序假设：取不到就抛错，绝不放未鉴权的请求出去。
 *
 * 2. **只给数据源那个主机的请求带头。**
 *    万一以后控件/会话被其他组件驱动去打开别的地址，密码也不会跟着发出去。
 *    （同主机内的 302 跳转由 OkHttp 自己处理：跨主机重定向时它会主动摘掉
 *    Authorization，这点正好和这里一致。）
 */
class WebDavMediaDataSource(
    private val client: OkHttpClient,
    private val credentials: () -> StoredSource?,
) : DataSource {

    private val delegate: OkHttpDataSource = OkHttpDataSource.Factory(client).createDataSource()

    override fun open(dataSpec: DataSpec): Long {
        val source = credentials()
            ?: throw IOException("数据源凭据不可用，无法播放")

        if (dataSpec.uri.host == WebDavUrl.parse(source.url)?.host) {
            delegate.setRequestProperty(
                "Authorization",
                WebDavClient.basicAuth(source.username, source.password),
            )
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
        private val credentials: () -> StoredSource?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = WebDavMediaDataSource(client, credentials)
    }
}
