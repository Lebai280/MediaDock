package com.lovelev.mediadock.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.lovelev.mediadock.core.data.scan.DirectoryLister
import com.lovelev.mediadock.core.data.scan.DirectoryListing
import com.lovelev.mediadock.core.data.scan.RemoteEntry
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.webdav.WebDavException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException

/**
 * SMB 目录列举：一次扫描复用一条连接，扫描结束由 Worker 调 [close]。
 *
 * 认证失败映射成 [WebDavException.Unauthorized]（引擎会当作该源封锁、不重试）；
 * 网络错误映射成 [WebDavException.Network]（交给 WorkManager 退避）。
 */
class SmbDirectoryLister(
    private val source: () -> SourceConfig?,
) : DirectoryLister, Closeable {

    private var client: SMBClient? = null
    private var session: com.hierynomus.smbj.session.Session? = null
    private var share: DiskShare? = null

    override suspend fun list(
        remoteKey: String,
        etag: String?,
        lastModified: String?,
    ): DirectoryListing = withContext(Dispatchers.IO) {
        val cfg = source()?.takeIf { it.credentialOk && it.type == SourceType.SMB }
            ?: throw WebDavException.Config("数据源不可用，请重新添加")
        try {
            val disk = connect(cfg)
            val entries = disk.list(remoteKey)
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .map { info ->
                    val childKey = if (remoteKey.isEmpty()) {
                        info.fileName
                    } else {
                        "$remoteKey/${info.fileName}"
                    }
                    RemoteEntry(
                        path = childKey,
                        name = info.fileName,
                        isDirectory = (info.fileAttributes and FILE_ATTRIBUTE_DIRECTORY) != 0L,
                        sizeBytes = info.endOfFile,
                        lastModified = null,
                        etag = null,
                    )
                }
            DirectoryListing(entries = entries)
        } catch (e: com.hierynomus.mssmb2.SMBApiException) {
            if (e.status == com.hierynomus.mserref.NtStatus.STATUS_ACCESS_DENIED ||
                e.status == com.hierynomus.mserref.NtStatus.STATUS_LOGON_FAILURE
            ) {
                throw WebDavException.Unauthorized()
            }
            throw WebDavException.Network(IOException("SMB 错误：${e.status}", e))
        } catch (e: IOException) {
            throw WebDavException.Network(e)
        }
    }

    private fun connect(cfg: SourceConfig): DiskShare {
        share?.let { return it }
        val host = SmbUrl.hostOf(cfg.url) ?: throw WebDavException.Config("SMB 主机地址无效")
        val cleanHost = host.substringBeforeLast(':')
        val port = host.substringAfterLast(':', "445").toIntOrNull() ?: 445
        val c = SMBClient()
        val s = c.connect(cleanHost, port)
            .authenticate(AuthenticationContext(cfg.username, cfg.password.toCharArray(), ""))
        val disk = s.connectShare(cfg.share.trim('/')) as? DiskShare
            ?: throw WebDavException.Config("无法打开共享 ${cfg.share}")
        client = c
        session = s
        share = disk
        return disk
    }

    override fun close() {
        runCatching { share?.close() }
        runCatching { session?.close() }
        runCatching { client?.close() }
        share = null
        session = null
        client = null
    }

    private companion object {
        const val FILE_ATTRIBUTE_DIRECTORY = 0x10L
    }
}
