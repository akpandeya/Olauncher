package app.olauncher.data

import android.content.SharedPreferences
import android.view.Gravity
import androidx.core.content.edit
import app.olauncher.helper.MusicPulse
import app.olauncher.data.Prefs.Companion.BATTERY_MODE
import app.olauncher.data.Prefs.Companion.BATTERY_MODE_AUTO
import app.olauncher.data.Prefs.Companion.BRIGHTNESS_FULL
import app.olauncher.data.Prefs.Companion.SWIPE_RIGHT_ACTION
import app.olauncher.data.Prefs.Companion.SWIPE_RIGHT_ACTION_TODAY
import app.olauncher.data.Prefs.Companion.TEXT_CONTRAST_AUTO
import app.olauncher.data.Prefs.Companion.TODAY_ORDER_SMART
import app.olauncher.data.Prefs.Companion.WASH_AUTO

/** Settings group: HomeLook (see [Prefs]). */
interface HomeLookPrefs {
    var homeAlignment: Int

    var homeBottomAlignment: Boolean

    var homeInfoCombined: Boolean

    var homeContextLine: Boolean

    var homeNextEvent: Boolean

    var homeNotes: Boolean

    /** Notes from `:n` as JSON (see Notes). */
    var notesJson: String

    var intentionText: String

    var intentionDay: Long

    var intentionOnHome: Boolean

    var homeTextContrast: Int

    var homeWashLevel: Int

    var homeTextBrightness: Int

    /** Slow album-colour glow and a progress line while media plays. */
    var homeMusicPulse: Boolean

    /** Music pulse glow strength in percent, 0 to 100. 0 hides the glow. */
    var homeGlowStrength: Int

    /** Weather animation (sun, rain, snow, fog, clouds, storm) drawn over the wallpaper. */
    var homeWeatherEffects: Boolean

    var homeShortcuts: Boolean

    var shortcutCount: Int

    var shortcutSizeStep: Int

    var shortcutSideStart: Boolean

    var shortcutOffsetStep: Int

    var shortcutIconStyle: Int

    var appLabelAlignment: Int

    var showStatusBar: Boolean

    var dateTimeVisibility: Int

    var batteryMode: Int

    var swipeLeftEnabled: Boolean

    var swipeRightEnabled: Boolean

    var swipeRightAction: Int

    var todayOrderSmart: Boolean
}

internal class HomeLookPrefsStore(private val prefs: SharedPreferences) : HomeLookPrefs {
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val HOME_SHORTCUTS = "HOME_SHORTCUTS"
    private val HOME_INFO_COMBINED = "HOME_INFO_COMBINED"
    private val HOME_CONTEXT_LINE = "HOME_CONTEXT_LINE"
    private val HOME_NEXT_EVENT = "HOME_NEXT_EVENT"
    private val HOME_NOTES = "HOME_NOTES"
    private val NOTES_JSON = "NOTES_JSON"
    private val INTENTION_TEXT = "INTENTION_TEXT"
    private val INTENTION_DAY = "INTENTION_DAY"
    private val INTENTION_ON_HOME = "INTENTION_ON_HOME"
    private val HOME_TEXT_CONTRAST = "HOME_TEXT_CONTRAST"
    private val HOME_WASH_LEVEL = "HOME_WASH_LEVEL"
    private val HOME_TEXT_BRIGHTNESS = "HOME_TEXT_BRIGHTNESS"
    private val HOME_MUSIC_PULSE = "HOME_MUSIC_PULSE"
    private val HOME_GLOW_STRENGTH = "HOME_GLOW_STRENGTH"
    private val HOME_WEATHER_EFFECTS = "HOME_WEATHER_EFFECTS"
    private val SHORTCUT_COUNT = "SHORTCUT_COUNT"
    private val SHORTCUT_SIZE_STEP = "SHORTCUT_SIZE_STEP"
    private val SHORTCUT_SIDE_START = "SHORTCUT_SIDE_START"
    private val SHORTCUT_OFFSET_STEP = "SHORTCUT_OFFSET_STEP"
    private val SHORTCUT_ICON_STYLE = "SHORTCUT_ICON_STYLE"
    private val APP_LABEL_ALIGNMENT = "APP_LABEL_ALIGNMENT"
    private val STATUS_BAR = "STATUS_BAR"
    private val DATE_TIME_VISIBILITY = "DATE_TIME_VISIBILITY"
    private val SWIPE_LEFT_ENABLED = "SWIPE_LEFT_ENABLED"
    private val SWIPE_RIGHT_ENABLED = "SWIPE_RIGHT_ENABLED"

    override var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    override var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value).apply() }

    override var homeInfoCombined: Boolean
        get() = prefs.getBoolean(HOME_INFO_COMBINED, false)
        set(value) = prefs.edit { putBoolean(HOME_INFO_COMBINED, value).apply() }

    override var homeContextLine: Boolean
        get() = prefs.getBoolean(HOME_CONTEXT_LINE, false)
        set(value) = prefs.edit { putBoolean(HOME_CONTEXT_LINE, value).apply() }

    override var homeNextEvent: Boolean
        get() = prefs.getBoolean(HOME_NEXT_EVENT, true)
        set(value) = prefs.edit { putBoolean(HOME_NEXT_EVENT, value).apply() }

    override var homeNotes: Boolean
        get() = prefs.getBoolean(HOME_NOTES, true)
        set(value) = prefs.edit { putBoolean(HOME_NOTES, value).apply() }

    override var notesJson: String
        get() = prefs.getString(NOTES_JSON, "").orEmpty()
        set(value) = prefs.edit { putString(NOTES_JSON, value).apply() }

    override var intentionText: String
        get() = prefs.getString(INTENTION_TEXT, "").orEmpty()
        set(value) = prefs.edit { putString(INTENTION_TEXT, value).apply() }

    override var intentionDay: Long
        get() = prefs.getLong(INTENTION_DAY, 0L)
        set(value) = prefs.edit { putLong(INTENTION_DAY, value).apply() }

    override var intentionOnHome: Boolean
        get() = prefs.getBoolean(INTENTION_ON_HOME, true)
        set(value) = prefs.edit { putBoolean(INTENTION_ON_HOME, value).apply() }

    override var homeTextContrast: Int
        get() = prefs.getInt(HOME_TEXT_CONTRAST, TEXT_CONTRAST_AUTO)
        set(value) = prefs.edit { putInt(HOME_TEXT_CONTRAST, value).apply() }

    override var homeWashLevel: Int
        get() = prefs.getInt(HOME_WASH_LEVEL, WASH_AUTO)
        set(value) = prefs.edit { putInt(HOME_WASH_LEVEL, value).apply() }

    override var homeTextBrightness: Int
        get() = prefs.getInt(HOME_TEXT_BRIGHTNESS, BRIGHTNESS_FULL)
        set(value) = prefs.edit { putInt(HOME_TEXT_BRIGHTNESS, value).apply() }

    /** Slow album-colour glow and a progress line while media plays. */
    override var homeMusicPulse: Boolean
        get() = prefs.getBoolean(HOME_MUSIC_PULSE, true)
        set(value) = prefs.edit { putBoolean(HOME_MUSIC_PULSE, value).apply() }

    /** Music pulse glow strength in percent, 0 to 100. 0 hides the glow. */
    override var homeGlowStrength: Int
        get() = MusicPulse.clampGlowStrength(prefs.getInt(HOME_GLOW_STRENGTH, MusicPulse.GLOW_STRENGTH_DEFAULT))
        set(value) = prefs.edit { putInt(HOME_GLOW_STRENGTH, MusicPulse.clampGlowStrength(value)).apply() }

    /** Weather animation (sun, rain, snow, fog, clouds, storm) drawn over the wallpaper. */
    override var homeWeatherEffects: Boolean
        get() = prefs.getBoolean(HOME_WEATHER_EFFECTS, false)
        set(value) = prefs.edit { putBoolean(HOME_WEATHER_EFFECTS, value).apply() }

    override var homeShortcuts: Boolean
        get() = prefs.getBoolean(HOME_SHORTCUTS, false)
        set(value) = prefs.edit { putBoolean(HOME_SHORTCUTS, value).apply() }

    override var shortcutCount: Int
        get() = SlotCount.clamp(prefs.getInt(SHORTCUT_COUNT, SlotCount.DEFAULT))
        set(value) = prefs.edit { putInt(SHORTCUT_COUNT, SlotCount.clamp(value)).apply() }

    override var shortcutSizeStep: Int
        get() = ShortcutSize.clamp(prefs.getInt(SHORTCUT_SIZE_STEP, ShortcutSize.DEFAULT))
        set(value) = prefs.edit { putInt(SHORTCUT_SIZE_STEP, ShortcutSize.clamp(value)).apply() }

    override var shortcutSideStart: Boolean
        get() = prefs.getBoolean(SHORTCUT_SIDE_START, false)
        set(value) = prefs.edit { putBoolean(SHORTCUT_SIDE_START, value).apply() }

    override var shortcutOffsetStep: Int
        get() = ShortcutOffset.clamp(prefs.getInt(SHORTCUT_OFFSET_STEP, ShortcutOffset.DEFAULT_STEP))
        set(value) = prefs.edit { putInt(SHORTCUT_OFFSET_STEP, ShortcutOffset.clamp(value)).apply() }

    override var shortcutIconStyle: Int
        get() = ShortcutIconStyle.clamp(prefs.getInt(SHORTCUT_ICON_STYLE, ShortcutIconStyle.DEFAULT))
        set(value) = prefs.edit { putInt(SHORTCUT_ICON_STYLE, ShortcutIconStyle.clamp(value)).apply() }

    override var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    override var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value).apply() }

    override var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value).apply() }

    override var batteryMode: Int
        get() = prefs.getInt(BATTERY_MODE, BATTERY_MODE_AUTO)
        set(value) = prefs.edit { putInt(BATTERY_MODE, value).apply() }

    override var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    override var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    override var swipeRightAction: Int
        get() = prefs.getInt(SWIPE_RIGHT_ACTION, SWIPE_RIGHT_ACTION_TODAY)
        set(value) = prefs.edit { putInt(SWIPE_RIGHT_ACTION, value).apply() }

    override var todayOrderSmart: Boolean
        get() = prefs.getBoolean(TODAY_ORDER_SMART, true)
        set(value) = prefs.edit { putBoolean(TODAY_ORDER_SMART, value).apply() }
}
