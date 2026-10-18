package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BatteryPrefsTest {

    @Test
    fun `battery mode preference keys and values match constants`() {
        assertEquals("BATTERY_MODE", Prefs.BATTERY_MODE)
        assertEquals(0, Prefs.BATTERY_MODE_AUTO)
        assertEquals(1, Prefs.BATTERY_MODE_ALWAYS)
        assertEquals(2, Prefs.BATTERY_MODE_OFF)
    }
}
