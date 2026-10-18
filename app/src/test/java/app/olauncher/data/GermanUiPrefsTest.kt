package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanUiPrefsTest {

    @Test
    fun `german ui preference key matches constant`() {
        assertEquals("GERMAN_UI", Prefs.GERMAN_UI)
    }
}
