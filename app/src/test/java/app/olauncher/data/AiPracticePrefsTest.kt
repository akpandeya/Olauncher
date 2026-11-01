package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The AI practice pref keys and defaults (#602, 9.9): add-only — the names and
 * defaults must not drift, the switch ships off and the provider ships Melious.
 */
class AiPracticePrefsTest {

    @Test
    fun `ai practice preference keys match constants`() {
        assertEquals("AI_PRACTICE_ENABLED", Prefs.AI_PRACTICE_ENABLED)
        assertEquals("AI_PROVIDER", Prefs.AI_PROVIDER)
        assertEquals("AI_BASE_URL", Prefs.AI_BASE_URL)
        assertEquals("AI_MODEL", Prefs.AI_MODEL)
        assertEquals("AI_KEY_REJECTED", Prefs.AI_KEY_REJECTED)
        assertEquals("AI_BILLING_BLOCKED", Prefs.AI_BILLING_BLOCKED)
    }

    @Test
    fun `the switch ships off, the provider ships Melious with the confirmed base URL`() {
        assertEquals(false, Prefs.AI_PRACTICE_ENABLED_DEFAULT)
        assertEquals("melious", Prefs.AI_PROVIDER_MELIOUS)
        assertEquals("https://api.melious.ai", Prefs.AI_BASE_URL_DEFAULT)
    }

    @Test
    fun `the daily word cap defaults to 100`() {
        assertEquals(100, Prefs.AI_DAILY_WORD_CAP_DEFAULT)
        assertEquals("AI_DAILY_WORD_CAP", Prefs.AI_DAILY_WORD_CAP)
    }
}
