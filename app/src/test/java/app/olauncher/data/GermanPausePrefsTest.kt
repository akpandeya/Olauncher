package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanPausePrefsTest {

    @Test
    fun `german pause word preference keys match constants`() {
        assertEquals("GERMAN_PAUSE_WORD", Prefs.GERMAN_PAUSE_WORD)
        assertEquals("GERMAN_PAUSE_LAST_WORD_ID", Prefs.GERMAN_PAUSE_LAST_WORD_ID)
    }
}
