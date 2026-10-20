package com.lovelev.mediadock.data.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.lovelev.mediadock.data.source.SourceConfig
import com.lovelev.mediadock.data.webdav.WebDavEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** 认证失败：浏览页据此进入 Blocked（不给重试入口，改为编辑源）。 */
class SmbAuthException(message: String) : IOException(message)

/**
 * 浏览页用的 SMB 目录列举：每次导航单次连接、列完即关。
 *
 * 与扫描用的 [SmbDirectoryLister] 分开：浏览是用户驱动的低频操作，
 * 不缓存连接更简单，也不会留下长期会话。
 */
object SmbBrowser {

    suspend fun list(source: SourceConfig, remoteKey: String): List<WebDavEntry> =
        withContext(Dispatchers.IO) {
            val host = SmbUrl.hostOf(source.url) ?: throw IOException("SMB 主机地址无效")
            val cleanHost = host.substringBeforeLast(':')
            val port = host.substringAfterLast(':', "445").toIntOrNull() ?: 445
            val shareName = source.share.trim('/')
            if (shareName.isBlank()) throw IOException("未填写共享名")

            val client = SMBClient()
            var session: com.hierynomus.smbj.session.Session? = null
            var share: DiskShare? = null
            try {
                session = client.connect(cleanHost, port)
                    .authenticate(AuthenticationContext(source.username, source.password.toCharArray(), ""))
                share = session.connectShare(shareName) as? DiskShare
                    ?: throw IOException("无法打开共享 $shareName")
                share.list(remoteKey)
                    .filterNot { it.fileName == "." || it.fileName == ".." }
                    .map { info ->
                        WebDavEntry(
                            path = if (remoteKey.isEmpty()) {
                                info.fileName
                            } else {
                                "$remoteKey/${info.fileName}"
                            },
                            name = info.fileName,
                            isDirectory = (info.fileAttributes and FILE_ATTRIBUTE_DIRECTORY) != 0L,
                            size = info.endOfFile,
                            lastModified = null,
                            contentType = null,
                        )
                    }
            } catch (e: SMBApiException) {
                if (e.status == NtStatus.STATUS_ACCESS_DENIED ||
                    e.status == NtStatus.STATUS_LOGON_FAILURE
                ) {
                    throw SmbAuthException("账号或密码不正确")
                }
                throw IOException("SMB 错误：${e.status}", e)
            } finally {
                runCatching { share?.close() }
                runCatching { session?.close() }
                runCatching { client.close() }
            }
        }

    private const val FILE_ATTRIBUTE_DIRECTORY = 0x10L
}
