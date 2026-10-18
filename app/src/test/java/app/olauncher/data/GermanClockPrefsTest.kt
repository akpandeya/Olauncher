package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanClockPrefsTest {

    @Test
    fun `german clock preference keys and values match constants`() {
        assertEquals("GERMAN_CLOCK", Prefs.GERMAN_CLOCK)
        assertEquals(0, Prefs.GERMAN_CLOCK_SYSTEM)
        assertEquals(1, Prefs.GERMAN_CLOCK_DEUTSCH)
        assertEquals(2, Prefs.GERMAN_CLOCK_IN_WORTEN)
    }
}
