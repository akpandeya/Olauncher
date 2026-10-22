package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.wellbeing.PauseSnooze
import app.olauncher.data.Prefs.Companion.COMMIT_LOCK
import app.olauncher.data.Prefs.Companion.DEFAULT_QUIET_RELEASE_TIMES
import app.olauncher.data.Prefs.Companion.PAUSE_ALLOWANCE_MIN
import app.olauncher.data.Prefs.Companion.PAUSE_APP_SNOOZE
import app.olauncher.data.Prefs.Companion.PAUSE_ASK_WHY
import app.olauncher.data.Prefs.Companion.PAUSE_ESCALATE
import app.olauncher.data.Prefs.Companion.PAUSE_LOG_DAY
import app.olauncher.data.Prefs.Companion.PAUSE_NOT_NOW_TODAY
import app.olauncher.data.Prefs.Companion.PAUSE_OPENED_TODAY
import app.olauncher.data.Prefs.Companion.PAUSE_SNOOZE_UNTIL
import app.olauncher.data.Prefs.Companion.PAUSE_WHY_BORED_TODAY
import app.olauncher.data.Prefs.Companion.PAUSE_WHY_LOOKUP_TODAY
import app.olauncher.data.Prefs.Companion.PAUSE_WHY_REPLY_TODAY
import app.olauncher.data.Prefs.Companion.QUIET_ENABLED
import app.olauncher.data.Prefs.Companion.QUIET_RELEASE_TIMES

/** Settings group: Pause (see [Prefs]). */
interface PausePrefs {
    var distractingApps: Set<String>

    var pauseEnabled: Boolean

    var quietEnabled: Boolean

    var quietReleaseTimes: String

    var firstPauseEnable: Boolean

    var pauseSeconds: Int

    var pauseAllowanceMin: Int

    var pauseEscalate: Boolean

    var pauseSnoozeUntil: Long

    var pauseAppSnooze: Map<String, Long>

    var pauseAskWhy: Boolean

    var commitLock: Boolean

    var pauseLogDay: Long

    var pauseNotNowToday: Int

    var pauseOpenedToday: Int

    var pauseWhyReplyToday: Int

    var pauseWhyLookupToday: Int

    var pauseWhyBoredToday: Int
}

internal class PausePrefsStore(private val prefs: SharedPreferences) : PausePrefs {
    private val DISTRACTING_APPS = "DISTRACTING_APPS"
    private val PAUSE_ENABLED = "PAUSE_ENABLED"
    private val FIRST_PAUSE_ENABLE = "FIRST_PAUSE_ENABLE"
    private val PAUSE_SECONDS = "PAUSE_SECONDS"

    override var distractingApps: Set<String>
        get() = prefs.getStringSet(DISTRACTING_APPS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(DISTRACTING_APPS, value).apply() }

    override var pauseEnabled: Boolean
        get() = prefs.getBoolean(PAUSE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(PAUSE_ENABLED, value).apply() }

    override var quietEnabled: Boolean
        get() = prefs.getBoolean(QUIET_ENABLED, false)
        set(value) = prefs.edit { putBoolean(QUIET_ENABLED, value).apply() }

    override var quietReleaseTimes: String
        get() = prefs.getString(QUIET_RELEASE_TIMES, DEFAULT_QUIET_RELEASE_TIMES) ?: DEFAULT_QUIET_RELEASE_TIMES
        set(value) = prefs.edit { putString(QUIET_RELEASE_TIMES, value).apply() }

    override var firstPauseEnable: Boolean
        get() = prefs.getBoolean(FIRST_PAUSE_ENABLE, true)
        set(value) = prefs.edit { putBoolean(FIRST_PAUSE_ENABLE, value).apply() }

    override var pauseSeconds: Int
        get() = prefs.getInt(PAUSE_SECONDS, 5)
        set(value) = prefs.edit { putInt(PAUSE_SECONDS, value).apply() }

    override var pauseAllowanceMin: Int
        get() = prefs.getInt(PAUSE_ALLOWANCE_MIN, 0)
        set(value) = prefs.edit { putInt(PAUSE_ALLOWANCE_MIN, value).apply() }

    override var pauseEscalate: Boolean
        get() = prefs.getBoolean(PAUSE_ESCALATE, false)
        set(value) = prefs.edit { putBoolean(PAUSE_ESCALATE, value).apply() }

    override var pauseSnoozeUntil: Long
        get() = prefs.getLong(PAUSE_SNOOZE_UNTIL, 0L)
        set(value) = prefs.edit { putLong(PAUSE_SNOOZE_UNTIL, value).apply() }

    override var pauseAppSnooze: Map<String, Long>
        get() = PauseSnooze.decode(prefs.getStringSet(PAUSE_APP_SNOOZE, emptySet())?.toSet() ?: emptySet())
        set(value) = prefs.edit { putStringSet(PAUSE_APP_SNOOZE, PauseSnooze.encode(value)).apply() }

    override var pauseAskWhy: Boolean
        get() = prefs.getBoolean(PAUSE_ASK_WHY, false)
        set(value) = prefs.edit { putBoolean(PAUSE_ASK_WHY, value).apply() }

    override var commitLock: Boolean
        get() = prefs.getBoolean(COMMIT_LOCK, false)
        set(value) = prefs.edit { putBoolean(COMMIT_LOCK, value).apply() }

    override var pauseLogDay: Long
        get() = prefs.getLong(PAUSE_LOG_DAY, 0L)
        set(value) = prefs.edit { putLong(PAUSE_LOG_DAY, value).apply() }

    override var pauseNotNowToday: Int
        get() = prefs.getInt(PAUSE_NOT_NOW_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_NOT_NOW_TODAY, value).apply() }

    override var pauseOpenedToday: Int
        get() = prefs.getInt(PAUSE_OPENED_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_OPENED_TODAY, value).apply() }

    override var pauseWhyReplyToday: Int
        get() = prefs.getInt(PAUSE_WHY_REPLY_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_WHY_REPLY_TODAY, value).apply() }

    override var pauseWhyLookupToday: Int
        get() = prefs.getInt(PAUSE_WHY_LOOKUP_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_WHY_LOOKUP_TODAY, value).apply() }

    override var pauseWhyBoredToday: Int
        get() = prefs.getInt(PAUSE_WHY_BORED_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_WHY_BORED_TODAY, value).apply() }
}
