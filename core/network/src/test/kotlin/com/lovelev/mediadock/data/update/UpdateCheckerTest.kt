package com.lovelev.mediadock.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `远端版本号更大才算有新版本`() {
        assertTrue(UpdateChecker.isNewer(current = 1, remote = 2))
        assertTrue(UpdateChecker.isNewer(current = 1, remote = 10))
    }

    @Test
    fun `相同或更小的版本号不算新版本`() {
        assertFalse(UpdateChecker.isNewer(current = 2, remote = 2))
        assertFalse(UpdateChecker.isNewer(current = 3, remote = 2))
        assertFalse(UpdateChecker.isNewer(current = 1, remote = 0))
    }

    @Test
    fun `解析站点给的完整 JSON`() {
        val manifest = UpdateChecker.parseManifest(
            """
            { "versionCode": 2, "versionName": "0.2.0",
              "url": "https://lovelev.eu.cc/mediadock/MediaDock-0.2.0.apk",
              "notes": "本次更新：加了分类行" }
            """.trimIndent(),
        )
        assertEquals(2, manifest?.versionCode)
        assertEquals("0.2.0", manifest?.versionName)
        assertEquals("https://lovelev.eu.cc/mediadock/MediaDock-0.2.0.apk", manifest?.url)
        assertEquals("本次更新：加了分类行", manifest?.notes)
    }

    @Test
    fun `多出来的字段不影响解析（站点以后加字段不会把 App 弄崩）`() {
        val manifest = UpdateChecker.parseManifest(
            """{ "versionCode": 3, "versionName": "0.3.0", "url": "https://a/b.apk",
                 "notes": null, "publishedAt": "2026-10-20", "minSdk": 26 }""",
        )
        assertEquals(3, manifest?.versionCode)
        assertNull(manifest?.notes)
    }

    @Test
    fun `缺字段时有默认值 不会崩`() {
        val manifest = UpdateChecker.parseManifest("""{ "versionCode": 4 }""")
        assertEquals(4, manifest?.versionCode)
        assertEquals("", manifest?.url)
    }

    @Test
    fun `不是 JSON 就返回 null（界面据此报「看不懂」，不会假成功）`() {
        assertNull(UpdateChecker.parseManifest("<html>404 Not Found</html>"))
        assertNull(UpdateChecker.parseManifest(""))
    }
}
