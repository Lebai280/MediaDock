package com.lovelev.mediadock.data.smb

import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** 添加数据源时的 SMB 连通性测试：**单次**连接并列根目录，不重试。 */
object SmbTester {

    suspend fun listRoot(
        hostRaw: String,
        shareName: String,
        username: String,
        password: String,
    ): List<String> = withContext(Dispatchers.IO) {
        val host = SmbUrl.hostOf(hostRaw) ?: throw IOException("主机地址无效")
        val cleanHost = host.substringBeforeLast(':')
        val port = host.substringAfterLast(':', "445").toIntOrNull() ?: 445
        val share = shareName.trim('/')
        if (share.isBlank()) throw IOException("请填写共享名")
        val client = SMBClient()
        var session: com.hierynomus.smbj.session.Session? = null
        var disk: DiskShare? = null
        try {
            session = client.connect(cleanHost, port)
                .authenticate(AuthenticationContext(username, password.toCharArray(), ""))
            disk = session.connectShare(share) as? DiskShare
                ?: throw IOException("无法打开共享 $share")
            disk.list("")
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .map { it.fileName }
        } catch (e: SMBApiException) {
            if (e.status == NtStatus.STATUS_ACCESS_DENIED ||
                e.status == NtStatus.STATUS_LOGON_FAILURE
            ) {
                throw IOException("账号或密码不正确")
            }
            throw IOException("SMB 错误：${e.status}", e)
        } finally {
            runCatching { disk?.close() }
            runCatching { session?.close() }
            runCatching { client.close() }
        }
    }
}
