package app.olauncher.data

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import app.olauncher.helper.HapticTuning
import app.olauncher.data.Prefs.Companion.ACCENT
import app.olauncher.data.Prefs.Companion.FONT_FAMILY
import app.olauncher.data.Prefs.Companion.HAPTICS
import app.olauncher.data.Prefs.Companion.HAPTIC_DURATION_MS
import app.olauncher.data.Prefs.Companion.HAPTIC_STRENGTH

/** Settings group: Appearance (see [Prefs]). */
interface AppearancePrefs {
    var appTheme: Int

    var textSizeScale: Float

    var fontFamily: Int

    var boldFont: Boolean

    var accent: Int

    var haptics: Boolean

    var hapticStrength: Int

    var hapticDurationMs: Int
}

internal class AppearancePrefsStore(private val prefs: SharedPreferences) : AppearancePrefs {
    private val APP_THEME = "APP_THEME"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val BOLD_FONT = "BOLD_FONT"

    override var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    override var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value).apply() }

    override var fontFamily: Int
        get() {
            val value = prefs.getInt(FONT_FAMILY, Constants.FontFamily.SYSTEM)
            return when (value) {
                Constants.FontFamily.INTER,
                Constants.FontFamily.PLEX -> value
                else -> Constants.FontFamily.SYSTEM
            }
        }
        set(value) = prefs.edit { putInt(FONT_FAMILY, value).apply() }

    override var boldFont: Boolean
        get() = prefs.getBoolean(BOLD_FONT, false)
        set(value) = prefs.edit { putBoolean(BOLD_FONT, value).apply() }

    override var accent: Int
        get() = prefs.getInt(ACCENT, Accent.OFF.id)
        set(value) = prefs.edit { putInt(ACCENT, Accent.fromId(value).id).apply() }

    override var haptics: Boolean
        get() = prefs.getBoolean(HAPTICS, true)
        set(value) = prefs.edit { putBoolean(HAPTICS, value).apply() }

    override var hapticStrength: Int
        get() = HapticTuning.clampStrength(prefs.getInt(HAPTIC_STRENGTH, HapticTuning.DEFAULT_STRENGTH))
        set(value) = prefs.edit { putInt(HAPTIC_STRENGTH, HapticTuning.clampStrength(value)).apply() }

    override var hapticDurationMs: Int
        get() = HapticTuning.clampDurationMs(prefs.getInt(HAPTIC_DURATION_MS, HapticTuning.DEFAULT_DURATION_MS))
        set(value) = prefs.edit { putInt(HAPTIC_DURATION_MS, HapticTuning.clampDurationMs(value)).apply() }
}
