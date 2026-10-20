package com.lovelev.mediadock.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialRouteTest {

    @Test
    fun `enabled source starts at library`() {
        assertEquals(Routes.LIBRARY, initialRoute(hasEnabledSource = true))
    }

    @Test
    fun `no enabled source starts at add source`() {
        assertEquals(Routes.ADD_SOURCE, initialRoute(hasEnabledSource = false))
    }
}
