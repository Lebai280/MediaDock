package com.lovelev.mediadock.data.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import java.io.IOException
import java.util.EnumSet

/**
 * Media3 的 SMB 数据源：按 `smb://host/share/path` 直读共享，支持 Range 随机读。
 *
 * 每次 open 建立一条连接（Media3 会复用同一个 DataSource 实例做顺序读），
 * close 时释放文件/共享/会话/客户端。认证失败只抛 IOException，不做任何重试。
 */
class SmbDataSource(
    private val sources: () -> List<SourceConfig>,
) : DataSource {

    private var client: SMBClient? = null
    private var session: com.hierynomus.smbj.session.Session? = null
    private var share: DiskShare? = null
    private var file: File? = null
    private var position = 0L
    private var size = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        close()
        val parsed = SmbUrl.parse(dataSpec.uri.toString())
            ?: throw IOException("SMB 地址无效")
        val source = sources().firstOrNull {
            it.type == SourceType.SMB && it.credentialOk && SmbUrl.matches(it, parsed)
        } ?: throw IOException("SMB 数据源凭据不可用")

        val c = SMBClient()
        val s = c.connect(parsed.host, parsed.port)
            .authenticate(AuthenticationContext(source.username, source.password.toCharArray(), ""))
        val sh = s.connectShare(parsed.share) as? DiskShare
            ?: throw IOException("无法打开共享 ${parsed.share}")
        val f = sh.openFile(
            parsed.path,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
        )
        client = c
        session = s
        share = sh
        file = f
        size = f.length
        position = if (dataSpec.position != C.TIME_UNSET) dataSpec.position else 0L
        uri = dataSpec.uri
        return size
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val f = file ?: return C.RESULT_END_OF_INPUT
        if (position >= size) return C.RESULT_END_OF_INPUT
        val n = f.read(buffer, position, length, offset)
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        return n
    }

    override fun getUri(): Uri? = uri

    override fun addTransferListener(transferListener: TransferListener) {
        // SMB 读取不经过 OkHttp，没有可转发的传输事件
    }

    override fun close() {
        runCatching { file?.close() }
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { client?.close() }
        file = null
        share = null
        session = null
        client = null
        uri = null
        position = 0
        size = 0
    }

    class Factory(
        private val sources: () -> List<SourceConfig>,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(sources)
    }
}
