package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.helper.HomeSets
import app.olauncher.wellbeing.SystemGrey
import app.olauncher.wellbeing.WindDown
import app.olauncher.data.Prefs.Companion.SYSTEM_GREY_SAVED
import app.olauncher.data.Prefs.Companion.SYSTEM_GREY_SAVED_ENABLED
import app.olauncher.data.Prefs.Companion.SYSTEM_GREY_SAVED_MODE
import app.olauncher.data.Prefs.Companion.WIND_DOWN
import app.olauncher.data.Prefs.Companion.WIND_DOWN_END
import app.olauncher.data.Prefs.Companion.WIND_DOWN_GREY
import app.olauncher.data.Prefs.Companion.WIND_DOWN_GREY_APPLIED
import app.olauncher.data.Prefs.Companion.WIND_DOWN_START
import app.olauncher.data.Prefs.Companion.WIND_DOWN_SYSTEM_GREY

/** Settings group: WindDown (see [Prefs]). */
interface WindDownPrefs {
    var windDown: Boolean

    var windDownStart: Int

    var windDownEnd: Int

    var windDownGrey: Boolean

    /** True while the grey wallpaper is what is currently applied. */
    var windDownGreyApplied: Boolean

    var windDownSystemGrey: Boolean

    /** The daltonizer values we replaced with grey, or null when we hold nothing to restore. */
    var systemGreySaved: SystemGrey.Daltonizer?
}

internal class WindDownPrefsStore(private val prefs: SharedPreferences) : WindDownPrefs {
    override var windDown: Boolean
        get() = prefs.getBoolean(WIND_DOWN, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN, value) }

    override var windDownStart: Int
        get() = prefs.getInt(WIND_DOWN_START, WindDown.DEFAULT_START)
        set(value) = prefs.edit { putInt(WIND_DOWN_START, HomeSets.normalize(value)) }

    override var windDownEnd: Int
        get() = prefs.getInt(WIND_DOWN_END, WindDown.DEFAULT_END)
        set(value) = prefs.edit { putInt(WIND_DOWN_END, HomeSets.normalize(value)) }

    override var windDownGrey: Boolean
        get() = prefs.getBoolean(WIND_DOWN_GREY, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN_GREY, value) }

    /** True while the grey wallpaper is what is currently applied. */
    override var windDownGreyApplied: Boolean
        get() = prefs.getBoolean(WIND_DOWN_GREY_APPLIED, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN_GREY_APPLIED, value) }

    override var windDownSystemGrey: Boolean
        get() = prefs.getBoolean(WIND_DOWN_SYSTEM_GREY, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN_SYSTEM_GREY, value) }

    /** The daltonizer values we replaced with grey, or null when we hold nothing to restore. */
    override var systemGreySaved: SystemGrey.Daltonizer?
        get() = if (prefs.getBoolean(SYSTEM_GREY_SAVED, false)) {
            SystemGrey.Daltonizer(
                prefs.getInt(SYSTEM_GREY_SAVED_ENABLED, 0),
                prefs.getInt(SYSTEM_GREY_SAVED_MODE, SystemGrey.MODE_OFF),
            )
        } else null
        set(value) = prefs.edit {
            if (value == null) {
                putBoolean(SYSTEM_GREY_SAVED, false)
            } else {
                putInt(SYSTEM_GREY_SAVED_ENABLED, value.enabled)
                putInt(SYSTEM_GREY_SAVED_MODE, value.mode)
                putBoolean(SYSTEM_GREY_SAVED, true)
            }
        }
}
