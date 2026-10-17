package com.lovelev.mediadock.data.webdav

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * 401 / 429 **不重试**的回归护栏。
 *
 * 为什么必须锁住：OpenList 的登录失败锁定按来源 IP 计数，而 nginx 反代后所有客户端
 * 都显示成 127.0.0.1。任何一个客户端对 401 重试，都会把所有人的 WebDAV 一起锁死。
 * 这里用一个本地 HTTP 服务数请求次数，断言「一次失败只发一次请求」。
 */
class WebDavClientTest {

    private lateinit var server: HttpServer
    private val requestCount = AtomicInteger()
    private val client = WebDavClient(OkHttpClient()) // 刻意不设 Authenticator：设了才会对 401 自动重发

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun startServer(status: Int) {
        server.createContext("/dav/") { exchange ->
            requestCount.incrementAndGet()
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
    }

    private fun propfindAgainst(status: Int, expected: Class<out WebDavException>) {
        startServer(status)
        val root = WebDavUrl.parse("http://127.0.0.1:${server.address.port}/dav/")!!

        runBlocking {
            try {
                client.propfind(collection = root, root = root, basicAuthHeader = "Basic dGVzdDp0ZXN0")
                fail("HTTP $status 应该抛 ${expected.simpleName}")
            } catch (e: WebDavException) {
                if (!expected.isInstance(e)) {
                    throw AssertionError("期望 ${expected.simpleName}，实际是 ${e::class.java.simpleName}", e)
                }
            }
        }

        assertEquals(
            "HTTP $status 只能发一次请求：重试会触发服务器登录锁定，连累其他设备",
            1,
            requestCount.get(),
        )
    }

    @Test
    fun `401 只请求一次并抛 Unauthorized`() {
        propfindAgainst(401, WebDavException.Unauthorized::class.java)
    }

    @Test
    fun `429 只请求一次并抛 Locked`() {
        propfindAgainst(429, WebDavException.Locked::class.java)
    }

    @Test
    fun `500 也不自动重试`() {
        propfindAgainst(500, WebDavException.Http::class.java)
    }
}
