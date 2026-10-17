package com.lovelev.mediadock.player

import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * 401 / 429 的媒体流重试护栏。
 *
 * 这条规则直接对应项目的硬约束（OpenList 按来源 IP 计登录失败次数），
 * 所以用单测把它锁住，而不是只写在注释里。
 */
class WebDavLoadErrorPolicyTest {

    /**
     * 只关心响应码；dataSpec / 响应体在这里用不到。
     *
     * 普通 JVM 单测里 `Uri.parse` 是 Android 桩（Stub!），没法构造真实的 `DataSpec`，
     * 但 [InvalidResponseCodeException] 的构造器只是把 dataSpec 存起来、不会解引用，
     * 所以用反射传 null 是安全的。生产代码不依赖这一点。
     */
    private fun httpError(code: Int): InvalidResponseCodeException {
        val ctor = InvalidResponseCodeException::class.java.getConstructor(
            Int::class.javaPrimitiveType,
            String::class.java,
            IOException::class.java,
            Map::class.java,
            androidx.media3.datasource.DataSpec::class.java,
            ByteArray::class.java,
        )
        return ctor.newInstance(
            code,
            "test",
            null,
            emptyMap<String, List<String>>(),
            null,
            null,
        )
    }

    @Test
    fun `401 判定为不再重试`() {
        assertTrue(WebDavLoadErrorPolicy.isAuthBlocked(httpError(401)))
    }

    @Test
    fun `429 判定为不再重试`() {
        assertTrue(WebDavLoadErrorPolicy.isAuthBlocked(httpError(429)))
    }

    @Test
    fun `500 交给默认策略处理`() {
        assertFalse(WebDavLoadErrorPolicy.isAuthBlocked(httpError(500)))
    }

    @Test
    fun `被外层异常包住的 401 也能识别`() {
        val wrapped = IOException("load failed", httpError(401))
        assertTrue(WebDavLoadErrorPolicy.isAuthBlocked(wrapped))
    }

    @Test
    fun `普通网络错误不误判`() {
        assertFalse(WebDavLoadErrorPolicy.isAuthBlocked(IOException("timeout")))
    }

    @Test
    fun `没有异常时不误判`() {
        assertFalse(WebDavLoadErrorPolicy.isAuthBlocked(null))
    }
}
