package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PausePrefsTest {

    @Test
    fun `pause policy preference keys match constants`() {
        assertEquals("PAUSE_ALLOWANCE_MIN", Prefs.PAUSE_ALLOWANCE_MIN)
        assertEquals("PAUSE_ESCALATE", Prefs.PAUSE_ESCALATE)
    }
}
