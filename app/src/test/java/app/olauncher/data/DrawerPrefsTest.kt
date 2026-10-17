package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerPrefsTest {

    @Test
    fun `drawer bottom search preference key matches constant`() {
        assertEquals("DRAWER_BOTTOM_SEARCH", Prefs.DRAWER_BOTTOM_SEARCH)
    }
}
