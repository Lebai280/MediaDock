package com.lovelev.mediadock.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HdrClassifierTest {

    @Test
    fun `dolby vision wins over everything`() {
        assertEquals(
            HdrFormat.DOLBY_VISION,
            HdrClassifier.classify(
                isDolbyVision = true,
                isHdr10PlusProfile = true,
                isHlg = true,
                isPq = true,
                hasHdrStaticInfo = true,
                hasColorInfo = true,
            ),
        )
    }

    @Test
    fun `hdr10 plus profile is detected before transfer`() {
        assertEquals(
            HdrFormat.HDR10_PLUS,
            HdrClassifier.classify(
                isDolbyVision = false,
                isHdr10PlusProfile = true,
                isHlg = false,
                isPq = true,
                hasHdrStaticInfo = false,
                hasColorInfo = true,
            ),
        )
    }

    @Test
    fun `hlg and pq classify correctly`() {
        assertEquals(
            HdrFormat.HLG,
            HdrClassifier.classify(false, false, true, false, false, true),
        )
        assertEquals(
            HdrFormat.HDR10,
            HdrClassifier.classify(false, false, false, true, false, true),
        )
    }

    @Test
    fun `static info alone means hdr10`() {
        assertEquals(
            HdrFormat.HDR10,
            HdrClassifier.classify(false, false, false, false, true, true),
        )
    }

    @Test
    fun `no color info is unknown and sdr color info is sdr`() {
        assertEquals(
            HdrFormat.UNKNOWN,
            HdrClassifier.classify(false, false, false, false, false, false),
        )
        assertEquals(
            HdrFormat.SDR,
            HdrClassifier.classify(false, false, false, false, false, true),
        )
    }
}
