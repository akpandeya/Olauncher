package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.GERMAN_CLOCK
import app.olauncher.data.Prefs.Companion.GERMAN_CLOCK_SYSTEM
import app.olauncher.data.Prefs.Companion.GERMAN_ENABLED
import app.olauncher.data.Prefs.Companion.GERMAN_LOOKUP
import app.olauncher.data.Prefs.Companion.GERMAN_PAUSE_LAST_WORD_ID
import app.olauncher.data.Prefs.Companion.GERMAN_PAUSE_WORD
import app.olauncher.data.Prefs.Companion.GERMAN_SCREEN_PICK
import app.olauncher.data.Prefs.Companion.GERMAN_SPELL_SUGGEST
import app.olauncher.data.Prefs.Companion.GERMAN_UI

/** Settings group: German (see [Prefs]). */
interface GermanPrefs {
    var germanEnabled: Boolean

    var germanClock: Int

    var germanLookup: Boolean

    var germanScreenPick: Boolean

    var germanSpellSuggest: Boolean

    var germanPauseWord: Boolean

    var germanPauseLastWordId: Long?

    var germanUi: Boolean

    val isGermanUi: Boolean
}

internal class GermanPrefsStore(private val prefs: SharedPreferences) : GermanPrefs {
    override var germanEnabled: Boolean
        get() = prefs.getBoolean(GERMAN_ENABLED, false)
        set(value) = prefs.edit { putBoolean(GERMAN_ENABLED, value).apply() }

    override var germanClock: Int
        get() = prefs.getInt(GERMAN_CLOCK, GERMAN_CLOCK_SYSTEM)
        set(value) = prefs.edit { putInt(GERMAN_CLOCK, value).apply() }

    override var germanLookup: Boolean
        get() = prefs.getBoolean(GERMAN_LOOKUP, true)
        set(value) = prefs.edit { putBoolean(GERMAN_LOOKUP, value).apply() }

    override var germanScreenPick: Boolean
        get() = prefs.getBoolean(GERMAN_SCREEN_PICK, false)
        set(value) = prefs.edit { putBoolean(GERMAN_SCREEN_PICK, value).apply() }

    override var germanSpellSuggest: Boolean
        get() = prefs.getBoolean(GERMAN_SPELL_SUGGEST, false)
        set(value) = prefs.edit { putBoolean(GERMAN_SPELL_SUGGEST, value).apply() }

    override var germanPauseWord: Boolean
        get() = prefs.getBoolean(GERMAN_PAUSE_WORD, true)
        set(value) = prefs.edit { putBoolean(GERMAN_PAUSE_WORD, value).apply() }

    override var germanPauseLastWordId: Long?
        get() = if (prefs.contains(GERMAN_PAUSE_LAST_WORD_ID)) prefs.getLong(GERMAN_PAUSE_LAST_WORD_ID, 0L) else null
        set(value) {
            prefs.edit {
                if (value == null) {
                    remove(GERMAN_PAUSE_LAST_WORD_ID).apply()
                } else {
                    putLong(GERMAN_PAUSE_LAST_WORD_ID, value).apply()
                }
            }
        }

    override var germanUi: Boolean
        get() = prefs.getBoolean(GERMAN_UI, false)
        set(value) = prefs.edit { putBoolean(GERMAN_UI, value).apply() }

    override val isGermanUi: Boolean
        get() = germanEnabled && germanUi
}
