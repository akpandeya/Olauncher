package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PausePrefsTest {

    @Test
    fun `pause policy preference keys match constants`() {
        assertEquals("PAUSE_ALLOWANCE_MIN", Prefs.PAUSE_ALLOWANCE_MIN)
        assertEquals("PAUSE_ESCALATE", Prefs.PAUSE_ESCALATE)
        assertEquals("PAUSE_ASK_WHY", Prefs.PAUSE_ASK_WHY)
        assertEquals("PAUSE_LOG_DAY", Prefs.PAUSE_LOG_DAY)
        assertEquals("PAUSE_NOT_NOW_TODAY", Prefs.PAUSE_NOT_NOW_TODAY)
        assertEquals("PAUSE_OPENED_TODAY", Prefs.PAUSE_OPENED_TODAY)
        assertEquals("PAUSE_WHY_REPLY_TODAY", Prefs.PAUSE_WHY_REPLY_TODAY)
        assertEquals("PAUSE_WHY_LOOKUP_TODAY", Prefs.PAUSE_WHY_LOOKUP_TODAY)
        assertEquals("PAUSE_WHY_BORED_TODAY", Prefs.PAUSE_WHY_BORED_TODAY)
    }
}
