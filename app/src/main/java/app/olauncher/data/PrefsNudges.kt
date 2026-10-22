package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.HEALTH_CONNECT_ENABLED
import app.olauncher.data.Prefs.Companion.NUDGE_SOCIAL_DAY
import app.olauncher.data.Prefs.Companion.NUDGE_SOCIAL_REPEAT_MIN
import app.olauncher.data.Prefs.Companion.NUDGE_SOCIAL_THRESHOLD_MIN
import app.olauncher.data.Prefs.Companion.NUDGE_WALK_INTERVAL_MIN
import app.olauncher.data.Prefs.Companion.NUDGE_WATER_INTERVAL_MIN
import app.olauncher.data.Prefs.Companion.NUDGE_WINDOW_END_MIN
import app.olauncher.data.Prefs.Companion.NUDGE_WINDOW_START_MIN

/** Settings group: Nudge (see [Prefs]). */
interface NudgePrefs {
    var nudgeWindowStartMin: Int

    var nudgeWindowEndMin: Int

    var nudgeWaterIntervalMin: Int

    var nudgeWalkIntervalMin: Int

    var healthConnectEnabled: Boolean

    var nudgeSocialThresholdMin: Int

    var nudgeSocialRepeatMin: Int

    var nudgeSocialDay: Long
}

internal class NudgePrefsStore(private val prefs: SharedPreferences) : NudgePrefs {
    override var nudgeWindowStartMin: Int
        get() = prefs.getInt(NUDGE_WINDOW_START_MIN, 540)
        set(value) = prefs.edit { putInt(NUDGE_WINDOW_START_MIN, value).apply() }

    override var nudgeWindowEndMin: Int
        get() = prefs.getInt(NUDGE_WINDOW_END_MIN, 1260)
        set(value) = prefs.edit { putInt(NUDGE_WINDOW_END_MIN, value).apply() }

    override var nudgeWaterIntervalMin: Int
        get() = prefs.getInt(NUDGE_WATER_INTERVAL_MIN, 0)
        set(value) = prefs.edit { putInt(NUDGE_WATER_INTERVAL_MIN, value).apply() }

    override var nudgeWalkIntervalMin: Int
        get() = prefs.getInt(NUDGE_WALK_INTERVAL_MIN, 0)
        set(value) = prefs.edit { putInt(NUDGE_WALK_INTERVAL_MIN, value).apply() }

    override var healthConnectEnabled: Boolean
        get() = prefs.getBoolean(HEALTH_CONNECT_ENABLED, false)
        set(value) = prefs.edit { putBoolean(HEALTH_CONNECT_ENABLED, value).apply() }

    override var nudgeSocialThresholdMin: Int
        get() = prefs.getInt(NUDGE_SOCIAL_THRESHOLD_MIN, 0)
        set(value) = prefs.edit { putInt(NUDGE_SOCIAL_THRESHOLD_MIN, value).apply() }

    override var nudgeSocialRepeatMin: Int
        get() = prefs.getInt(NUDGE_SOCIAL_REPEAT_MIN, 15)
        set(value) = prefs.edit { putInt(NUDGE_SOCIAL_REPEAT_MIN, value).apply() }

    override var nudgeSocialDay: Long
        get() = prefs.getLong(NUDGE_SOCIAL_DAY, 0L)
        set(value) = prefs.edit { putLong(NUDGE_SOCIAL_DAY, value).apply() }
}
