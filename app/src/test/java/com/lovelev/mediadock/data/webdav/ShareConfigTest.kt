package com.lovelev.mediadock.data.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareConfigTest {

    @Test
    fun `自己生成的文本能解回来`() {
        val text = ShareConfig.build(
            url = "https://files.example.com/dav",
            username = "demo",
            password = "pw-DEMO-1234",
            rootPath = "/movies",
        )
        val parsed = ShareConfig.parse(text)
        assertEquals("https://files.example.com/dav", parsed?.url)
        assertEquals("demo", parsed?.username)
        assertEquals("pw-DEMO-1234", parsed?.password)
        assertEquals("/movies", parsed?.rootPath)
    }

    @Test
    fun `没根路径时生成的文本不带那行 也能解`() {
        val text = ShareConfig.build("https://a.com/dav", "u", "p", "")
        assertEquals("", ShareConfig.parse(text)?.rootPath)
        assertEquals("u", ShareConfig.parse(text)?.username)
    }

    @Test
    fun `认 URL 里塞账号密码的写法`() {
        val parsed = ShareConfig.parse("https://demo:pw123@files.example.com/dav")
        assertEquals("https://files.example.com/dav", parsed?.url)
        assertEquals("demo", parsed?.username)
        assertEquals("pw123", parsed?.password)
    }

    @Test
    fun `URL 里的账号密码是百分号编码的 也要解开`() {
        val url = ShareConfig.embedInUrl("https://a.com/dav", "user@mail.com", "p@ss:word")
        val parsed = ShareConfig.parse(url)
        assertEquals("user@mail.com", parsed?.username)
        assertEquals("p@ss:word", parsed?.password)
        assertEquals("https://a.com/dav", parsed?.url)
    }

    @Test
    fun `认朋友手打的自由文本（中文全角冒号 + 多余的话）`() {
        val text = """
            给你我的播放器账号，装好 App 后把这个复制进去：
            地址：files.example.com/dav
            账号：demo
            密码：abc-123
            就用这个，别外传哈
        """.trimIndent()
        val parsed = ShareConfig.parse(text)
        assertEquals("https://files.example.com/dav", parsed?.url)
        assertEquals("demo", parsed?.username)
        assertEquals("abc-123", parsed?.password)
    }

    @Test
    fun `认英文标签`() {
        val text = "url: https://a.com/dav\nusername: bob\npassword: secret"
        val parsed = ShareConfig.parse(text)
        assertEquals("bob", parsed?.username)
        assertEquals("secret", parsed?.password)
    }

    @Test
    fun `光有链接没有密码 不算配置（剪贴板里随便一个链接别乱填）`() {
        assertNull(ShareConfig.parse("https://www.example.com/some/page"))
        assertNull(ShareConfig.parse("看看这个 https://a.com/dav 挺好"))
    }

    @Test
    fun `空白和不相干的文本返回 null`() {
        assertNull(ShareConfig.parse(""))
        assertNull(ShareConfig.parse("   "))
        assertNull(ShareConfig.parse("晚上吃什么"))
    }

    @Test
    fun `没写协议时自动补 https`() {
        assertEquals("https://nas.local:5005/dav", ShareConfig.parse("地址：nas.local:5005/dav\n密码：p")?.url)
    }

    @Test
    fun `地址后面的中文标点会被剔掉`() {
        assertEquals(
            "https://a.com/dav",
            ShareConfig.parse("地址：https://a.com/dav，账号：u\n密码：p")?.url,
        )
    }
}
