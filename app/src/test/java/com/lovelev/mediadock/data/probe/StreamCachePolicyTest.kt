package com.lovelev.mediadock.data.probe

import com.lovelev.mediadock.data.local.StreamInfoEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamCachePolicyTest {

    @Test
    fun `fresh when schema age and size agree`() {
        val entity = entity(size = 100, schema = StreamCachePolicy.SCHEMA_VERSION, probedAt = 1_000)
        assertTrue(StreamCachePolicy.isFresh(entity, knownSize = 100, now = 2_000))
        assertTrue(StreamCachePolicy.isFresh(entity, knownSize = null, now = 2_000))
    }

    @Test
    fun `stale on schema age or size mismatch`() {
        assertFalse(
            StreamCachePolicy.isFresh(
                entity(size = 100, schema = 0, probedAt = 1_000),
                knownSize = 100,
                now = 2_000,
            ),
        )
        assertFalse(
            StreamCachePolicy.isFresh(
                entity(size = 100, schema = StreamCachePolicy.SCHEMA_VERSION, probedAt = 1_000),
                knownSize = 100,
                now = 1_000 + StreamCachePolicy.TTL_MS + 1,
            ),
        )
        assertFalse(
            StreamCachePolicy.isFresh(
                entity(size = 100, schema = StreamCachePolicy.SCHEMA_VERSION, probedAt = 1_000),
                knownSize = 101,
                now = 2_000,
            ),
        )
    }

    private fun entity(size: Long?, schema: Int, probedAt: Long) = StreamInfoEntity(
        path = "p",
        sizeBytes = size,
        schemaVersion = schema,
        probedAt = probedAt,
        payloadJson = "{}",
    )
}
