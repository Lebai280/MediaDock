package com.lovelev.mediadock.data.source

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lovelev.mediadock.core.model.SourceType
import com.lovelev.mediadock.data.prefs.SecureCredentialStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 多源注册表：加密往返、按 id 删除、类型保留（仅 .debug 包）。 */
@RunWith(AndroidJUnit4::class)
class SourceRegistryTest {

    private lateinit var store: SecureCredentialStore

    @Before
    fun setUp() {
        store = SecureCredentialStore(InstrumentationRegistry.getInstrumentation().targetContext)
        runBlocking { store.clearSource() }
    }

    @After
    fun tearDown() = runBlocking {
        store.clearSource()
    }

    @Test
    fun savesLoadsAndRemovesBySourceId() = runBlocking {
        store.saveSource(
            SourceConfig(
                id = "webdav-a",
                type = SourceType.WEBDAV,
                name = "主库",
                url = "https://example.com/dav",
                username = "u1",
                password = "p1",
                rootPath = "/a",
            ),
        )
        store.saveSource(
            SourceConfig(
                id = "smb-1",
                type = SourceType.SMB,
                name = "NAS",
                url = "192.168.1.10:445",
                username = "u2",
                password = "p2",
                share = "media",
            ),
        )

        val list = store.loadSources()
        assertEquals(2, list.size)
        val webdav = list.first { it.id == "webdav-a" }
        assertEquals("p1", webdav.password)
        assertEquals("/a", webdav.rootPath)
        assertEquals(SourceType.SMB, list.first { it.id == "smb-1" }.type)

        store.removeSource("smb-1")
        val remaining = store.loadSources()
        assertEquals(1, remaining.size)
        assertNotNull(remaining.firstOrNull { it.id == "webdav-a" })
        assertNull(remaining.firstOrNull { it.id == "smb-1" })
    }

    @Test
    fun disabledSourceIsPreserved() = runBlocking {
        store.saveSource(
            SourceConfig(
                id = "webdav-b",
                type = SourceType.WEBDAV,
                name = "副库",
                url = "https://example.com/dav",
                username = "u",
                password = "p",
            ),
        )
        store.setSourceEnabled("webdav-b", false)
        val source = store.loadSources().first { it.id == "webdav-b" }
        assertEquals(false, source.enabled)
    }
}
