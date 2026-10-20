package com.lovelev.mediadock.data.probe

import com.lovelev.mediadock.core.model.ProbeStatus
import com.lovelev.mediadock.data.local.StreamInfoEntity

object StreamCachePolicy {
    const val SCHEMA_VERSION = 3
    const val TTL_MS = 30L * 24 * 60 * 60 * 1000
    const val NEGATIVE_TTL_MS = 5L * 60 * 1000

    fun isFresh(entity: StreamInfoEntity, knownSize: Long?, now: Long): Boolean {
        if (entity.schemaVersion != SCHEMA_VERSION) return false
        if (now - entity.probedAt > TTL_MS) return false
        if (knownSize != null && entity.sizeBytes != null && knownSize != entity.sizeBytes) return false
        return true
    }

    fun cacheable(status: ProbeStatus): Boolean =
        status == ProbeStatus.OK || status == ProbeStatus.UNSUPPORTED
}
