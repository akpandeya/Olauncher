package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import android.view.Gravity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

import java.util.Locale
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSetStarts
import app.olauncher.helper.HomeSets
import app.olauncher.wellbeing.SystemGrey
import app.olauncher.wellbeing.WindDown
import app.olauncher.helper.HomeSlotField
import app.olauncher.helper.RecentApp
import app.olauncher.helper.RecentApps
import app.olauncher.helper.RotationInterval
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayCards
import app.olauncher.wellbeing.DaySnapshot
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.PauseDay
import app.olauncher.wellbeing.toDaySnapshot
import app.olauncher.wellbeing.PauseLog
import app.olauncher.wellbeing.PauseSnooze
import app.olauncher.wellbeing.PauseLogStorage
import app.olauncher.wellbeing.UsageSnapshotStore

class Prefs(context: Context) : PauseLogStorage {
    companion object {
        const val AUTO_BACKUP_ENABLED = "AUTO_BACKUP_ENABLED"
        const val AUTO_BACKUP_TREE_URI = "AUTO_BACKUP_TREE_URI"
        const val AUTO_BACKUP_LAST_OK = "AUTO_BACKUP_LAST_OK"
        const val AUTO_BACKUP_LAST_FAILED = "AUTO_BACKUP_LAST_FAILED"
        const val WEATHER_ENABLED = "WEATHER_ENABLED"
        const val WEATHER_PLACE_NAME = "WEATHER_PLACE_NAME"
        const val WEATHER_LAT = "WEATHER_LAT"
        const val WEATHER_LON = "WEATHER_LON"
        const val WEATHER_UNIT = "WEATHER_UNIT"
        const val WEATHER_FETCHED_AT = "WEATHER_FETCHED_AT"
        const val WEATHER_USE_LOCATION = "WEATHER_USE_LOCATION"
        const val WEATHER_LOCATION_AT = "WEATHER_LOCATION_AT"
        const val WEATHER_APP_PACKAGE = "WEATHER_APP_PACKAGE"
        const val WEATHER_APP_USER = "WEATHER_APP_USER"
        const val WEATHER_APP_CLASS_NAME = "WEATHER_APP_CLASS_NAME"
        const val WEATHER_UNIT_C = "C"
        const val WEATHER_UNIT_F = "F"

        fun defaultWeatherUnit(country: String = Locale.getDefault().country): String =
            if (country == "US") WEATHER_UNIT_F else WEATHER_UNIT_C

        const val HOME_SETS = "HOME_SETS"
        const val HOME_SET_MORNING_START = "HOME_SET_MORNING_START"
        const val HOME_SET_DAY_START = "HOME_SET_DAY_START"
        const val HOME_SET_EVENING_START = "HOME_SET_EVENING_START"

        const val DRAWER_BOTTOM_SEARCH = "DRAWER_BOTTOM_SEARCH"
        const val SWIPE_RIGHT_ACTION_TODAY = 0
        const val SWIPE_RIGHT_ACTION_APP = 1
        const val SWIPE_RIGHT_ACTION = "SWIPE_RIGHT_ACTION"
        const val TODAY_CARD_PREFIX = "TODAY_CARD_"
        const val TODAY_ORDER_SMART = "TODAY_ORDER_SMART"
        const val NUDGE_WINDOW_START_MIN = "NUDGE_WINDOW_START_MIN"
        const val NUDGE_WINDOW_END_MIN = "NUDGE_WINDOW_END_MIN"
        const val NUDGE_WATER_INTERVAL_MIN = "NUDGE_WATER_INTERVAL_MIN"
        const val NUDGE_WALK_INTERVAL_MIN = "NUDGE_WALK_INTERVAL_MIN"
        const val HEALTH_CONNECT_ENABLED = "HEALTH_CONNECT_ENABLED"
        const val NUDGE_DONE_DAY = "NUDGE_DONE_DAY"
        const val NUDGE_DONE_PREFIX = "NUDGE_DONE_"
        const val NUDGE_SOCIAL_THRESHOLD_MIN = "NUDGE_SOCIAL_THRESHOLD_MIN"
        const val NUDGE_SOCIAL_REPEAT_MIN = "NUDGE_SOCIAL_REPEAT_MIN"
        const val NUDGE_SOCIAL_LAST_BUCKET = "NUDGE_SOCIAL_LAST_BUCKET"
        const val NUDGE_SOCIAL_DAY = "NUDGE_SOCIAL_DAY"
        const val GERMAN_ENABLED = "GERMAN_ENABLED"
        const val GERMAN_UI = "GERMAN_UI"
        const val GERMAN_LOOKUP = "GERMAN_LOOKUP"
        const val GERMAN_SCREEN_PICK = "GERMAN_SCREEN_PICK"
        const val GERMAN_SPELL_SUGGEST = "GERMAN_SPELL_SUGGEST"
        const val GERMAN_PAUSE_WORD = "GERMAN_PAUSE_WORD"
        const val GERMAN_PAUSE_LAST_WORD_ID = "GERMAN_PAUSE_LAST_WORD_ID"
        const val GERMAN_CLOCK = "GERMAN_CLOCK"
        const val GERMAN_CLOCK_SYSTEM = 0
        const val GERMAN_CLOCK_DEUTSCH = 1
        const val GERMAN_CLOCK_IN_WORTEN = 2
        const val BATTERY_MODE = "BATTERY_MODE"
        const val BATTERY_MODE_AUTO = 0
        const val BATTERY_MODE_ALWAYS = 1
        const val BATTERY_MODE_OFF = 2
        const val TEXT_CONTRAST_AUTO = 0
        const val TEXT_CONTRAST_THEME = 1
        const val PAUSE_ALLOWANCE_MIN = "PAUSE_ALLOWANCE_MIN"
        const val PAUSE_ESCALATE = "PAUSE_ESCALATE"
        const val PAUSE_ASK_WHY = "PAUSE_ASK_WHY"
        const val PAUSE_LOG_DAY = "PAUSE_LOG_DAY"
        const val PAUSE_NOT_NOW_TODAY = "PAUSE_NOT_NOW_TODAY"
        const val PAUSE_OPENED_TODAY = "PAUSE_OPENED_TODAY"
        const val PAUSE_WHY_REPLY_TODAY = "PAUSE_WHY_REPLY_TODAY"
        const val PAUSE_WHY_LOOKUP_TODAY = "PAUSE_WHY_LOOKUP_TODAY"
        const val PAUSE_WHY_BORED_TODAY = "PAUSE_WHY_BORED_TODAY"
        const val PAUSE_SNOOZE_UNTIL = "PAUSE_SNOOZE_UNTIL"
        const val PAUSE_APP_SNOOZE = "PAUSE_APP_SNOOZE"
        const val COMMIT_LOCK = "COMMIT_LOCK"
        const val WIND_DOWN = "WIND_DOWN"
        const val WIND_DOWN_START = "WIND_DOWN_START"
        const val WIND_DOWN_END = "WIND_DOWN_END"
        const val WIND_DOWN_GREY = "WIND_DOWN_GREY"
        const val WIND_DOWN_GREY_APPLIED = "WIND_DOWN_GREY_APPLIED"
        const val WIND_DOWN_SYSTEM_GREY = "WIND_DOWN_SYSTEM_GREY"
        const val SYSTEM_GREY_SAVED = "SYSTEM_GREY_SAVED"
        const val SYSTEM_GREY_SAVED_ENABLED = "SYSTEM_GREY_SAVED_ENABLED"
        const val SYSTEM_GREY_SAVED_MODE = "SYSTEM_GREY_SAVED_MODE"
        const val SEARCH_ENGINE = "SEARCH_ENGINE"
        const val SEARCH_ENGINE_CUSTOM_URL = "SEARCH_ENGINE_CUSTOM_URL"
        const val DRAWER_NEW_FIRST = "DRAWER_NEW_FIRST"
        const val DRAWER_LETTER_STRIP = "DRAWER_LETTER_STRIP"
        const val DRAWER_RECENTS = "DRAWER_RECENTS"
        const val SEARCH_CONTACTS = "SEARCH_CONTACTS"
        const val CURRENCY_RATES = "CURRENCY_RATES"
        const val RECENT_APPS = "RECENT_APPS"
        const val QUIET_ENABLED = "QUIET_ENABLED"
        const val QUIET_RELEASE_TIMES = "QUIET_RELEASE_TIMES"
        const val DEFAULT_QUIET_RELEASE_TIMES = "750,1110"
        const val FONT_FAMILY = "FONT_FAMILY"
        const val ACCENT = "ACCENT"
        const val HAPTICS = "HAPTICS"

        fun nudgeDoneKey(type: String): String = "$NUDGE_DONE_PREFIX${type.uppercase()}"

        var onNudgeDoneRollover: ((epochDay: Long, water: Int, walk: Int) -> Unit)? = null
        var onPauseLogRollover: ((previous: PauseDay) -> Unit)? = null
    }

    private val PREFS_FILENAME = "app.olauncher"

    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_OPEN_TIME = "FIRST_OPEN_TIME"
    private val GESTURE_HINT_INDEX = "GESTURE_HINT_INDEX"
    private val GESTURE_HINT_DAY = "GESTURE_HINT_DAY"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val USER_STATE = "USER_STATE"
    private val LOCK_MODE = "LOCK_MODE"
    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val DAILY_WALLPAPER = "DAILY_WALLPAPER"
    private val DAILY_WALLPAPER_URL = "DAILY_WALLPAPER_URL"
    private val PHOTO_WALLPAPER_COUNT = "PHOTO_WALLPAPER_COUNT"
    private val PHOTO_WALLPAPER_INDEX = "PHOTO_WALLPAPER_INDEX"
    private val PHOTO_WALLPAPER_TARGET = "PHOTO_WALLPAPER_TARGET"
    private val PHOTO_WALLPAPER_LAST_DAY = "PHOTO_WALLPAPER_LAST_DAY"
    private val PHOTO_WALLPAPER_HOME_MENU = "PHOTO_WALLPAPER_HOME_MENU"
    private val PHOTO_WALLPAPER_INTERVAL = "PHOTO_WALLPAPER_INTERVAL"
    private val PHOTO_WALLPAPER_LAST_CHANGED = "PHOTO_WALLPAPER_LAST_CHANGED"
    private val HOME_ALIGNMENT = "HOME_ALIGNMENT"
    private val HOME_BOTTOM_ALIGNMENT = "HOME_BOTTOM_ALIGNMENT"
    private val HOME_SHORTCUTS = "HOME_SHORTCUTS"
    private val HOME_INFO_COMBINED = "HOME_INFO_COMBINED"
    private val HOME_CONTEXT_LINE = "HOME_CONTEXT_LINE"
    private val HOME_NEXT_EVENT = "HOME_NEXT_EVENT"
    private val INTENTION_TEXT = "INTENTION_TEXT"
    private val INTENTION_DAY = "INTENTION_DAY"
    private val INTENTION_ON_HOME = "INTENTION_ON_HOME"
    private val HOME_TEXT_CONTRAST = "HOME_TEXT_CONTRAST"
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
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val SHOW_HINT_COUNTER = "SHOW_HINT_COUNTER"
    private val APP_THEME = "APP_THEME"
    private val ABOUT_CLICKED = "ABOUT_CLICKED"
    private val RATE_CLICKED = "RATE_CLICKED"
    private val WALLPAPER_MSG_SHOWN = "WALLPAPER_MSG_SHOWN"
    private val SHARE_SHOWN_TIME = "SHARE_SHOWN_TIME"
    private val TEXT_SIZE_SCALE = "TEXT_SIZE_SCALE"
    private val BOLD_FONT = "BOLD_FONT"
    private val PRO_MESSAGE_SHOWN = "PRO_MESSAGE_SHOWN"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val SCREEN_TIME_STYLE = "SCREEN_TIME_STYLE"
    private val SCREEN_TIME_TARGET_MIN = "SCREEN_TIME_TARGET_MIN"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val SHOWN_ON_DAY_OF_YEAR = "SHOWN_ON_DAY_OF_YEAR"
    private val DISTRACTING_APPS = "DISTRACTING_APPS"
    private val PAUSE_ENABLED = "PAUSE_ENABLED"
    private val FIRST_PAUSE_ENABLE = "FIRST_PAUSE_ENABLE"
    private val PAUSE_SECONDS = "PAUSE_SECONDS"
    private val OWN_OPENS_DAY = "OWN_OPENS_DAY"
    private val OWN_OPENS_PREFIX = "OWN_OPENS_"
    // Home button for recents feature disabled
    // private val HOME_BUTTON_SHOW_RECENTS = "HOME_BUTTON_SHOW_RECENTS"

    private val APP_NAME_1 = "APP_NAME_1"
    private val APP_NAME_2 = "APP_NAME_2"
    private val APP_NAME_3 = "APP_NAME_3"
    private val APP_NAME_4 = "APP_NAME_4"
    private val APP_NAME_5 = "APP_NAME_5"
    private val APP_NAME_6 = "APP_NAME_6"
    private val APP_NAME_7 = "APP_NAME_7"
    private val APP_NAME_8 = "APP_NAME_8"
    private val APP_PACKAGE_1 = "APP_PACKAGE_1"
    private val APP_PACKAGE_2 = "APP_PACKAGE_2"
    private val APP_PACKAGE_3 = "APP_PACKAGE_3"
    private val APP_PACKAGE_4 = "APP_PACKAGE_4"
    private val APP_PACKAGE_5 = "APP_PACKAGE_5"
    private val APP_PACKAGE_6 = "APP_PACKAGE_6"
    private val APP_PACKAGE_7 = "APP_PACKAGE_7"
    private val APP_PACKAGE_8 = "APP_PACKAGE_8"
    private val APP_ACTIVITY_CLASS_NAME_1 = "APP_ACTIVITY_CLASS_NAME_1"
    private val APP_ACTIVITY_CLASS_NAME_2 = "APP_ACTIVITY_CLASS_NAME_2"
    private val APP_ACTIVITY_CLASS_NAME_3 = "APP_ACTIVITY_CLASS_NAME_3"
    private val APP_ACTIVITY_CLASS_NAME_4 = "APP_ACTIVITY_CLASS_NAME_4"
    private val APP_ACTIVITY_CLASS_NAME_5 = "APP_ACTIVITY_CLASS_NAME_5"
    private val APP_ACTIVITY_CLASS_NAME_6 = "APP_ACTIVITY_CLASS_NAME_6"
    private val APP_ACTIVITY_CLASS_NAME_7 = "APP_ACTIVITY_CLASS_NAME_7"
    private val APP_ACTIVITY_CLASS_NAME_8 = "APP_ACTIVITY_CLASS_NAME_8"
    private val APP_USER_1 = "APP_USER_1"
    private val APP_USER_2 = "APP_USER_2"
    private val APP_USER_3 = "APP_USER_3"
    private val APP_USER_4 = "APP_USER_4"
    private val APP_USER_5 = "APP_USER_5"
    private val APP_USER_6 = "APP_USER_6"
    private val APP_USER_7 = "APP_USER_7"
    private val APP_USER_8 = "APP_USER_8"

    private val APP_NAME_SWIPE_LEFT = "APP_NAME_SWIPE_LEFT"
    private val APP_NAME_SWIPE_RIGHT = "APP_NAME_SWIPE_RIGHT"
    private val APP_PACKAGE_SWIPE_LEFT = "APP_PACKAGE_SWIPE_LEFT"
    private val APP_PACKAGE_SWIPE_RIGHT = "APP_PACKAGE_SWIPE_RIGHT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT = "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT"
    private val APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT = "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT"
    private val APP_USER_SWIPE_LEFT = "APP_USER_SWIPE_LEFT"
    private val APP_USER_SWIPE_RIGHT = "APP_USER_SWIPE_RIGHT"
    private val CLOCK_APP_PACKAGE = "CLOCK_APP_PACKAGE"
    private val CLOCK_APP_USER = "CLOCK_APP_USER"
    private val CLOCK_APP_CLASS_NAME = "CLOCK_APP_CLASS_NAME"
    private val CALENDAR_APP_PACKAGE = "CALENDAR_APP_PACKAGE"
    private val CALENDAR_APP_USER = "CALENDAR_APP_USER"
    private val CALENDAR_APP_CLASS_NAME = "CALENDAR_APP_CLASS_NAME"
    private val SCREEN_TIME_APP_PACKAGE = "SCREEN_TIME_APP_PACKAGE"
    private val SCREEN_TIME_APP_USER = "SCREEN_TIME_APP_USER"
    private val SCREEN_TIME_APP_CLASS_NAME = "SCREEN_TIME_APP_CLASS_NAME"

    private val IS_SHORTCUT_1 = "IS_SHORTCUT_1"
    private val SHORTCUT_ID_1 = "SHORTCUT_ID_1"
    private val IS_SHORTCUT_2 = "IS_SHORTCUT_2"
    private val SHORTCUT_ID_2 = "SHORTCUT_ID_2"
    private val IS_SHORTCUT_3 = "IS_SHORTCUT_3"
    private val SHORTCUT_ID_3 = "SHORTCUT_ID_3"
    private val IS_SHORTCUT_4 = "IS_SHORTCUT_4"
    private val SHORTCUT_ID_4 = "SHORTCUT_ID_4"
    private val IS_SHORTCUT_5 = "IS_SHORTCUT_5"
    private val SHORTCUT_ID_5 = "SHORTCUT_ID_5"
    private val IS_SHORTCUT_6 = "IS_SHORTCUT_6"
    private val SHORTCUT_ID_6 = "SHORTCUT_ID_6"
    private val IS_SHORTCUT_7 = "IS_SHORTCUT_7"
    private val SHORTCUT_ID_7 = "SHORTCUT_ID_7"
    private val IS_SHORTCUT_8 = "IS_SHORTCUT_8"
    private val SHORTCUT_ID_8 = "SHORTCUT_ID_8"

    private val SHORTCUT_ID_SWIPE_LEFT = "SHORTCUT_ID_SWIPE_LEFT"
    private val IS_SHORTCUT_SWIPE_LEFT = "IS_SHORTCUT_SWIPE_LEFT"
    private val SHORTCUT_ID_SWIPE_RIGHT = "SHORTCUT_ID_SWIPE_RIGHT"
    private val IS_SHORTCUT_SWIPE_RIGHT = "IS_SHORTCUT_SWIPE_RIGHT"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_FILENAME, 0)

    var usageSnapshotStore: UsageSnapshotStore? = try {
        UsageSnapshotStore(context.filesDir)
    } catch (_: Exception) {
        null
    }

    var onNudgeDoneRollover: ((epochDay: Long, water: Int, walk: Int) -> Unit)? = null
    var onPauseLogRollover: ((previous: PauseDay) -> Unit)? = null

    var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    var gestureHintIndex: Int
        get() = prefs.getInt(GESTURE_HINT_INDEX, 0)
        set(value) = prefs.edit { putInt(GESTURE_HINT_INDEX, value).apply() }

    var gestureHintDay: Long
        get() = prefs.getLong(GESTURE_HINT_DAY, 0L)
        set(value) = prefs.edit { putLong(GESTURE_HINT_DAY, value).apply() }

    var firstOpenTime: Long
        get() = prefs.getLong(FIRST_OPEN_TIME, 0L)
        set(value) = prefs.edit { putLong(FIRST_OPEN_TIME, value).apply() }

    var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value).apply() }

    var userState: String
        get() = prefs.getString(USER_STATE, Constants.UserState.START).toString()
        set(value) = prefs.edit { putString(USER_STATE, value).apply() }

    var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value).apply() }

    var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    var drawerBottomSearch: Boolean
        get() = prefs.getBoolean(DRAWER_BOTTOM_SEARCH, true)
        set(value) = prefs.edit { putBoolean(DRAWER_BOTTOM_SEARCH, value).apply() }

    var drawerNewFirst: Boolean
        get() = prefs.getBoolean(DRAWER_NEW_FIRST, true)
        set(value) = prefs.edit { putBoolean(DRAWER_NEW_FIRST, value).apply() }

    var drawerLetterStrip: Boolean
        get() = prefs.getBoolean(DRAWER_LETTER_STRIP, false)
        set(value) = prefs.edit { putBoolean(DRAWER_LETTER_STRIP, value).apply() }

    var searchContacts: Boolean
        get() = prefs.getBoolean(SEARCH_CONTACTS, false)
        set(value) = prefs.edit { putBoolean(SEARCH_CONTACTS, value).apply() }

    var currencyRates: Boolean
        get() = prefs.getBoolean(CURRENCY_RATES, false)
        set(value) = prefs.edit { putBoolean(CURRENCY_RATES, value).apply() }

    var drawerRecents: Boolean
        get() = prefs.getBoolean(DRAWER_RECENTS, true)
        set(value) = prefs.edit { putBoolean(DRAWER_RECENTS, value).apply() }

    var recentApps: String
        get() = prefs.getString(RECENT_APPS, "") ?: ""
        set(value) = prefs.edit { putString(RECENT_APPS, value).apply() }

    fun addRecentApp(packageName: String, activityClassName: String?, user: String) {
        val current = RecentApps.decode(recentApps)
        val updated = RecentApps.push(
            current,
            RecentApp(
                packageName = packageName,
                activityClassName = activityClassName,
                user = user,
            ),
            max = 3,
        )
        recentApps = RecentApps.encode(updated)
    }

    var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value).apply() }

    var dailyWallpaper: Boolean
        get() = prefs.getBoolean(DAILY_WALLPAPER, false)
        set(value) = prefs.edit { putBoolean(DAILY_WALLPAPER, value).apply() }

    var dailyWallpaperUrl: String
        get() = prefs.getString(DAILY_WALLPAPER_URL, "").toString()
        set(value) = prefs.edit { putString(DAILY_WALLPAPER_URL, value).apply() }

    var photoWallpaperCount: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_COUNT, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_COUNT, value).apply() }

    var photoWallpaperIndex: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_INDEX, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_INDEX, value).apply() }

    var photoWallpaperTarget: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_TARGET, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_TARGET, value).apply() }

    var photoWallpaperLastDay: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_LAST_DAY, 0)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_LAST_DAY, value).apply() }

    var photoWallpaperHomeMenu: Boolean
        get() = prefs.getBoolean(PHOTO_WALLPAPER_HOME_MENU, false)
        set(value) = prefs.edit { putBoolean(PHOTO_WALLPAPER_HOME_MENU, value).apply() }

    var photoWallpaperInterval: Int
        get() = prefs.getInt(PHOTO_WALLPAPER_INTERVAL, RotationInterval.DEFAULT.id)
        set(value) = prefs.edit { putInt(PHOTO_WALLPAPER_INTERVAL, value).apply() }

    var photoWallpaperLastChanged: Long
        get() = prefs.getLong(PHOTO_WALLPAPER_LAST_CHANGED, 0L)
        set(value) = prefs.edit { putLong(PHOTO_WALLPAPER_LAST_CHANGED, value).apply() }

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value).apply() }

    var homeAlignment: Int
        get() = prefs.getInt(HOME_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(HOME_ALIGNMENT, value).apply() }

    var homeBottomAlignment: Boolean
        get() = prefs.getBoolean(HOME_BOTTOM_ALIGNMENT, false)
        set(value) = prefs.edit { putBoolean(HOME_BOTTOM_ALIGNMENT, value).apply() }

    var homeInfoCombined: Boolean
        get() = prefs.getBoolean(HOME_INFO_COMBINED, false)
        set(value) = prefs.edit { putBoolean(HOME_INFO_COMBINED, value).apply() }

    var homeContextLine: Boolean
        get() = prefs.getBoolean(HOME_CONTEXT_LINE, false)
        set(value) = prefs.edit { putBoolean(HOME_CONTEXT_LINE, value).apply() }

    var homeNextEvent: Boolean
        get() = prefs.getBoolean(HOME_NEXT_EVENT, true)
        set(value) = prefs.edit { putBoolean(HOME_NEXT_EVENT, value).apply() }

    var intentionText: String
        get() = prefs.getString(INTENTION_TEXT, "").orEmpty()
        set(value) = prefs.edit { putString(INTENTION_TEXT, value).apply() }

    var intentionDay: Long
        get() = prefs.getLong(INTENTION_DAY, 0L)
        set(value) = prefs.edit { putLong(INTENTION_DAY, value).apply() }

    var intentionOnHome: Boolean
        get() = prefs.getBoolean(INTENTION_ON_HOME, true)
        set(value) = prefs.edit { putBoolean(INTENTION_ON_HOME, value).apply() }

    var homeTextContrast: Int
        get() = prefs.getInt(HOME_TEXT_CONTRAST, TEXT_CONTRAST_AUTO)
        set(value) = prefs.edit { putInt(HOME_TEXT_CONTRAST, value).apply() }

    var homeShortcuts: Boolean
        get() = prefs.getBoolean(HOME_SHORTCUTS, false)
        set(value) = prefs.edit { putBoolean(HOME_SHORTCUTS, value).apply() }

    var shortcutCount: Int
        get() = SlotCount.clamp(prefs.getInt(SHORTCUT_COUNT, SlotCount.DEFAULT))
        set(value) = prefs.edit { putInt(SHORTCUT_COUNT, SlotCount.clamp(value)).apply() }

    var shortcutSizeStep: Int
        get() = ShortcutSize.clamp(prefs.getInt(SHORTCUT_SIZE_STEP, ShortcutSize.DEFAULT))
        set(value) = prefs.edit { putInt(SHORTCUT_SIZE_STEP, ShortcutSize.clamp(value)).apply() }

    var shortcutSideStart: Boolean
        get() = prefs.getBoolean(SHORTCUT_SIDE_START, false)
        set(value) = prefs.edit { putBoolean(SHORTCUT_SIDE_START, value).apply() }

    var shortcutOffsetStep: Int
        get() = ShortcutOffset.clamp(prefs.getInt(SHORTCUT_OFFSET_STEP, ShortcutOffset.DEFAULT_STEP))
        set(value) = prefs.edit { putInt(SHORTCUT_OFFSET_STEP, ShortcutOffset.clamp(value)).apply() }

    var shortcutIconStyle: Int
        get() = ShortcutIconStyle.clamp(prefs.getInt(SHORTCUT_ICON_STYLE, ShortcutIconStyle.DEFAULT))
        set(value) = prefs.edit { putInt(SHORTCUT_ICON_STYLE, ShortcutIconStyle.clamp(value)).apply() }

    var appLabelAlignment: Int
        get() = prefs.getInt(APP_LABEL_ALIGNMENT, Gravity.START)
        set(value) = prefs.edit { putInt(APP_LABEL_ALIGNMENT, value).apply() }

    var showStatusBar: Boolean
        get() = prefs.getBoolean(STATUS_BAR, false)
        set(value) = prefs.edit { putBoolean(STATUS_BAR, value).apply() }

    var dateTimeVisibility: Int
        get() = prefs.getInt(DATE_TIME_VISIBILITY, Constants.DateTime.ON)
        set(value) = prefs.edit { putInt(DATE_TIME_VISIBILITY, value).apply() }

    var batteryMode: Int
        get() = prefs.getInt(BATTERY_MODE, BATTERY_MODE_AUTO)
        set(value) = prefs.edit { putInt(BATTERY_MODE, value).apply() }

    var swipeLeftEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_LEFT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_LEFT_ENABLED, value).apply() }

    var swipeRightEnabled: Boolean
        get() = prefs.getBoolean(SWIPE_RIGHT_ENABLED, true)
        set(value) = prefs.edit { putBoolean(SWIPE_RIGHT_ENABLED, value).apply() }

    var swipeRightAction: Int
        get() = prefs.getInt(SWIPE_RIGHT_ACTION, SWIPE_RIGHT_ACTION_TODAY)
        set(value) = prefs.edit { putInt(SWIPE_RIGHT_ACTION, value).apply() }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)
        set(value) = prefs.edit { putInt(APP_THEME, value).apply() }

    var textSizeScale: Float
        get() = prefs.getFloat(TEXT_SIZE_SCALE, 1.0f)
        set(value) = prefs.edit { putFloat(TEXT_SIZE_SCALE, value).apply() }

    var fontFamily: Int
        get() {
            val value = prefs.getInt(FONT_FAMILY, Constants.FontFamily.SYSTEM)
            return when (value) {
                Constants.FontFamily.INTER,
                Constants.FontFamily.PLEX -> value
                else -> Constants.FontFamily.SYSTEM
            }
        }
        set(value) = prefs.edit { putInt(FONT_FAMILY, value).apply() }

    var boldFont: Boolean
        get() = prefs.getBoolean(BOLD_FONT, false)
        set(value) = prefs.edit { putBoolean(BOLD_FONT, value).apply() }

    var accent: Int
        get() = prefs.getInt(ACCENT, Accent.OFF.id)
        set(value) = prefs.edit { putInt(ACCENT, Accent.fromId(value).id).apply() }

    var haptics: Boolean
        get() = prefs.getBoolean(HAPTICS, true)
        set(value) = prefs.edit { putBoolean(HAPTICS, value).apply() }

    var proMessageShown: Boolean
        get() = prefs.getBoolean(PRO_MESSAGE_SHOWN, false)
        set(value) = prefs.edit { putBoolean(PRO_MESSAGE_SHOWN, value).apply() }

    var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value).apply() }

    var screenTimeStyle: Int
        get() = prefs.getInt(SCREEN_TIME_STYLE, 0)
        set(value) = prefs.edit { putInt(SCREEN_TIME_STYLE, value).apply() }

    var screenTimeTargetMin: Int
        get() = prefs.getInt(SCREEN_TIME_TARGET_MIN, 120)
        set(value) = prefs.edit { putInt(SCREEN_TIME_TARGET_MIN, value).apply() }

    var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    var shownOnDayOfYear: Int
        get() = prefs.getInt(SHOWN_ON_DAY_OF_YEAR, 0)
        set(value) = prefs.edit { putInt(SHOWN_ON_DAY_OF_YEAR, value).apply() }

    // Home button for recents feature disabled
    // var homeButtonShowRecents: Boolean
    //     get() = prefs.getBoolean(HOME_BUTTON_SHOW_RECENTS, false)
    //     set(value) = prefs.edit { putBoolean(HOME_BUTTON_SHOW_RECENTS, value).apply() }

    var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value).apply() }

    var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value).apply() }

    var distractingApps: Set<String>
        get() = prefs.getStringSet(DISTRACTING_APPS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(DISTRACTING_APPS, value).apply() }

    var pauseEnabled: Boolean
        get() = prefs.getBoolean(PAUSE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(PAUSE_ENABLED, value).apply() }

    var quietEnabled: Boolean
        get() = prefs.getBoolean(QUIET_ENABLED, false)
        set(value) = prefs.edit { putBoolean(QUIET_ENABLED, value).apply() }

    var quietReleaseTimes: String
        get() = prefs.getString(QUIET_RELEASE_TIMES, DEFAULT_QUIET_RELEASE_TIMES) ?: DEFAULT_QUIET_RELEASE_TIMES
        set(value) = prefs.edit { putString(QUIET_RELEASE_TIMES, value).apply() }

    var germanEnabled: Boolean
        get() = prefs.getBoolean(GERMAN_ENABLED, false)
        set(value) = prefs.edit { putBoolean(GERMAN_ENABLED, value).apply() }

    var germanClock: Int
        get() = prefs.getInt(GERMAN_CLOCK, GERMAN_CLOCK_SYSTEM)
        set(value) = prefs.edit { putInt(GERMAN_CLOCK, value).apply() }

    var germanLookup: Boolean
        get() = prefs.getBoolean(GERMAN_LOOKUP, true)
        set(value) = prefs.edit { putBoolean(GERMAN_LOOKUP, value).apply() }

    var germanScreenPick: Boolean
        get() = prefs.getBoolean(GERMAN_SCREEN_PICK, false)
        set(value) = prefs.edit { putBoolean(GERMAN_SCREEN_PICK, value).apply() }

    var germanSpellSuggest: Boolean
        get() = prefs.getBoolean(GERMAN_SPELL_SUGGEST, false)
        set(value) = prefs.edit { putBoolean(GERMAN_SPELL_SUGGEST, value).apply() }

    var germanPauseWord: Boolean
        get() = prefs.getBoolean(GERMAN_PAUSE_WORD, true)
        set(value) = prefs.edit { putBoolean(GERMAN_PAUSE_WORD, value).apply() }

    var germanPauseLastWordId: Long?
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

    var germanUi: Boolean
        get() = prefs.getBoolean(GERMAN_UI, false)
        set(value) = prefs.edit { putBoolean(GERMAN_UI, value).apply() }

    val isGermanUi: Boolean
        get() = germanEnabled && germanUi

    var firstPauseEnable: Boolean
        get() = prefs.getBoolean(FIRST_PAUSE_ENABLE, true)
        set(value) = prefs.edit { putBoolean(FIRST_PAUSE_ENABLE, value).apply() }

    var pauseSeconds: Int
        get() = prefs.getInt(PAUSE_SECONDS, 5)
        set(value) = prefs.edit { putInt(PAUSE_SECONDS, value).apply() }

    var pauseAllowanceMin: Int
        get() = prefs.getInt(PAUSE_ALLOWANCE_MIN, 0)
        set(value) = prefs.edit { putInt(PAUSE_ALLOWANCE_MIN, value).apply() }

    var pauseEscalate: Boolean
        get() = prefs.getBoolean(PAUSE_ESCALATE, false)
        set(value) = prefs.edit { putBoolean(PAUSE_ESCALATE, value).apply() }

    var pauseSnoozeUntil: Long
        get() = prefs.getLong(PAUSE_SNOOZE_UNTIL, 0L)
        set(value) = prefs.edit { putLong(PAUSE_SNOOZE_UNTIL, value).apply() }

    var pauseAppSnooze: Map<String, Long>
        get() = PauseSnooze.decode(prefs.getStringSet(PAUSE_APP_SNOOZE, emptySet())?.toSet() ?: emptySet())
        set(value) = prefs.edit { putStringSet(PAUSE_APP_SNOOZE, PauseSnooze.encode(value)).apply() }

    var pauseAskWhy: Boolean
        get() = prefs.getBoolean(PAUSE_ASK_WHY, false)
        set(value) = prefs.edit { putBoolean(PAUSE_ASK_WHY, value).apply() }

    var commitLock: Boolean
        get() = prefs.getBoolean(COMMIT_LOCK, false)
        set(value) = prefs.edit { putBoolean(COMMIT_LOCK, value).apply() }

    var pauseLogDay: Long
        get() = prefs.getLong(PAUSE_LOG_DAY, 0L)
        set(value) = prefs.edit { putLong(PAUSE_LOG_DAY, value).apply() }

    var pauseNotNowToday: Int
        get() = prefs.getInt(PAUSE_NOT_NOW_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_NOT_NOW_TODAY, value).apply() }

    var pauseOpenedToday: Int
        get() = prefs.getInt(PAUSE_OPENED_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_OPENED_TODAY, value).apply() }

    var pauseWhyReplyToday: Int
        get() = prefs.getInt(PAUSE_WHY_REPLY_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_WHY_REPLY_TODAY, value).apply() }

    var pauseWhyLookupToday: Int
        get() = prefs.getInt(PAUSE_WHY_LOOKUP_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_WHY_LOOKUP_TODAY, value).apply() }

    var pauseWhyBoredToday: Int
        get() = prefs.getInt(PAUSE_WHY_BORED_TODAY, 0)
        set(value) = prefs.edit { putInt(PAUSE_WHY_BORED_TODAY, value).apply() }

    val pauseLog: PauseLog by lazy {
        PauseLog(
            storage = this,
            daySupplier = { epochDaySupplier() },
            onDayRollover = { previous ->
                handlePauseLogRollover(previous)
            }
        )
    }

    private fun handlePauseLogRollover(previous: PauseDay) {
        val snapshot = previous.toDaySnapshot()
        try {
            usageSnapshotStore?.upsert(snapshot)
        } catch (_: Exception) {}

        onPauseLogRollover?.invoke(previous)
        if (onPauseLogRollover !== Companion.onPauseLogRollover) {
            Companion.onPauseLogRollover?.invoke(previous)
        }
    }

    var toShowHintCounter: Int
        get() = prefs.getInt(SHOW_HINT_COUNTER, 1)
        set(value) = prefs.edit { putInt(SHOW_HINT_COUNTER, value).apply() }

    var aboutClicked: Boolean
        get() = prefs.getBoolean(ABOUT_CLICKED, false)
        set(value) = prefs.edit { putBoolean(ABOUT_CLICKED, value).apply() }

    var rateClicked: Boolean
        get() = prefs.getBoolean(RATE_CLICKED, false)
        set(value) = prefs.edit { putBoolean(RATE_CLICKED, value).apply() }

    var wallpaperMsgShown: Boolean
        get() = prefs.getBoolean(WALLPAPER_MSG_SHOWN, false)
        set(value) = prefs.edit { putBoolean(WALLPAPER_MSG_SHOWN, value).apply() }

    var shareShownTime: Long
        get() = prefs.getLong(SHARE_SHOWN_TIME, 0L)
        set(value) = prefs.edit { putLong(SHARE_SHOWN_TIME, value).apply() }

    var appName1: String
        get() = prefs.getString(APP_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_1, value).apply() }

    var appName2: String
        get() = prefs.getString(APP_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_2, value).apply() }

    var appName3: String
        get() = prefs.getString(APP_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_3, value).apply() }

    var appName4: String
        get() = prefs.getString(APP_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_4, value).apply() }

    var appName5: String
        get() = prefs.getString(APP_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_5, value).apply() }

    var appName6: String
        get() = prefs.getString(APP_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_6, value).apply() }

    var appName7: String
        get() = prefs.getString(APP_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_7, value).apply() }

    var appName8: String
        get() = prefs.getString(APP_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_NAME_8, value).apply() }

    var appPackage1: String
        get() = prefs.getString(APP_PACKAGE_1, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_1, value).apply() }

    var appPackage2: String
        get() = prefs.getString(APP_PACKAGE_2, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_2, value).apply() }

    var appPackage3: String
        get() = prefs.getString(APP_PACKAGE_3, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_3, value).apply() }

    var appPackage4: String
        get() = prefs.getString(APP_PACKAGE_4, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_4, value).apply() }

    var appPackage5: String
        get() = prefs.getString(APP_PACKAGE_5, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_5, value).apply() }

    var appPackage6: String
        get() = prefs.getString(APP_PACKAGE_6, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_6, value).apply() }

    var appPackage7: String
        get() = prefs.getString(APP_PACKAGE_7, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_7, value).apply() }

    var appPackage8: String
        get() = prefs.getString(APP_PACKAGE_8, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_8, value).apply() }

    var appActivityClassName1: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_1, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_1, value).apply() }

    var appActivityClassName2: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_2, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_2, value).apply() }

    var appActivityClassName3: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_3, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_3, value).apply() }

    var appActivityClassName4: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_4, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_4, value).apply() }

    var appActivityClassName5: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_5, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_5, value).apply() }

    var appActivityClassName6: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_6, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_6, value).apply() }

    var appActivityClassName7: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_7, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_7, value).apply() }

    var appActivityClassName8: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_8, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_8, value).apply() }

    var appUser1: String
        get() = prefs.getString(APP_USER_1, "").toString()
        set(value) = prefs.edit { putString(APP_USER_1, value).apply() }

    var appUser2: String
        get() = prefs.getString(APP_USER_2, "").toString()
        set(value) = prefs.edit { putString(APP_USER_2, value).apply() }

    var appUser3: String
        get() = prefs.getString(APP_USER_3, "").toString()
        set(value) = prefs.edit { putString(APP_USER_3, value).apply() }

    var appUser4: String
        get() = prefs.getString(APP_USER_4, "").toString()
        set(value) = prefs.edit { putString(APP_USER_4, value).apply() }

    var appUser5: String
        get() = prefs.getString(APP_USER_5, "").toString()
        set(value) = prefs.edit { putString(APP_USER_5, value).apply() }

    var appUser6: String
        get() = prefs.getString(APP_USER_6, "").toString()
        set(value) = prefs.edit { putString(APP_USER_6, value).apply() }

    var appUser7: String
        get() = prefs.getString(APP_USER_7, "").toString()
        set(value) = prefs.edit { putString(APP_USER_7, value).apply() }

    var appUser8: String
        get() = prefs.getString(APP_USER_8, "").toString()
        set(value) = prefs.edit { putString(APP_USER_8, value).apply() }

    var appNameSwipeLeft: String
        get() = prefs.getString(APP_NAME_SWIPE_LEFT, "Camera").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_LEFT, value).apply() }

    var appNameSwipeRight: String
        get() = prefs.getString(APP_NAME_SWIPE_RIGHT, "Phone").toString()
        set(value) = prefs.edit { putString(APP_NAME_SWIPE_RIGHT, value).apply() }

    var appPackageSwipeLeft: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_LEFT, value).apply() }

    var appActivityClassNameSwipeLeft: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT, value).apply() }

    var appPackageSwipeRight: String
        get() = prefs.getString(APP_PACKAGE_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_PACKAGE_SWIPE_RIGHT, value).apply() }

    var appActivityClassNameRight: String?
        get() = prefs.getString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT, value).apply() }

    var appUserSwipeLeft: String
        get() = prefs.getString(APP_USER_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_LEFT, value).apply() }

    var appUserSwipeRight: String
        get() = prefs.getString(APP_USER_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(APP_USER_SWIPE_RIGHT, value).apply() }

    var clockAppPackage: String
        get() = prefs.getString(CLOCK_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_PACKAGE, value).apply() }

    var clockAppUser: String
        get() = prefs.getString(CLOCK_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_USER, value).apply() }

    var clockAppClassName: String?
        get() = prefs.getString(CLOCK_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CLOCK_APP_CLASS_NAME, value).apply() }

    var calendarAppPackage: String
        get() = prefs.getString(CALENDAR_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_PACKAGE, value).apply() }

    var calendarAppUser: String
        get() = prefs.getString(CALENDAR_APP_USER, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_USER, value).apply() }

    var calendarAppClassName: String?
        get() = prefs.getString(CALENDAR_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(CALENDAR_APP_CLASS_NAME, value).apply() }

    var screenTimeAppPackage: String
        get() = prefs.getString(SCREEN_TIME_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_PACKAGE, value).apply() }

    var screenTimeAppUser: String
        get() = prefs.getString(SCREEN_TIME_APP_USER, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_USER, value).apply() }

    var screenTimeAppClassName: String?
        get() = prefs.getString(SCREEN_TIME_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(SCREEN_TIME_APP_CLASS_NAME, value).apply() }

    var weatherAppPackage: String
        get() = prefs.getString(WEATHER_APP_PACKAGE, "").toString()
        set(value) = prefs.edit { putString(WEATHER_APP_PACKAGE, value).apply() }

    var weatherAppUser: String
        get() = prefs.getString(WEATHER_APP_USER, "").toString()
        set(value) = prefs.edit { putString(WEATHER_APP_USER, value).apply() }

    var weatherAppClassName: String?
        get() = prefs.getString(WEATHER_APP_CLASS_NAME, "").toString()
        set(value) = prefs.edit { putString(WEATHER_APP_CLASS_NAME, value).apply() }

    var isShortcut1: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_1, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_1, value) }

    var shortcutId1: String
        get() = prefs.getString(SHORTCUT_ID_1, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_1, value) }

    var isShortcut2: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_2, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_2, value) }

    var shortcutId2: String
        get() = prefs.getString(SHORTCUT_ID_2, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_2, value) }

    var isShortcut3: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_3, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_3, value) }

    var shortcutId3: String
        get() = prefs.getString(SHORTCUT_ID_3, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_3, value) }

    var isShortcut4: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_4, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_4, value) }

    var shortcutId4: String
        get() = prefs.getString(SHORTCUT_ID_4, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_4, value) }

    var isShortcut5: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_5, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_5, value) }

    var shortcutId5: String
        get() = prefs.getString(SHORTCUT_ID_5, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_5, value) }

    var isShortcut6: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_6, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_6, value) }

    var shortcutId6: String
        get() = prefs.getString(SHORTCUT_ID_6, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_6, value) }

    var isShortcut7: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_7, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_7, value) }

    var shortcutId7: String
        get() = prefs.getString(SHORTCUT_ID_7, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_7, value) }

    var isShortcut8: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_8, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_8, value) }

    var shortcutId8: String
        get() = prefs.getString(SHORTCUT_ID_8, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_8, value) }

    var shortcutIdSwipeLeft: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_LEFT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_LEFT, value) }

    var isShortcutSwipeLeft: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_LEFT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_LEFT, value) }

    var shortcutIdSwipeRight: String
        get() = prefs.getString(SHORTCUT_ID_SWIPE_RIGHT, "").toString()
        set(value) = prefs.edit { putString(SHORTCUT_ID_SWIPE_RIGHT, value) }

    var isShortcutSwipeRight: Boolean
        get() = prefs.getBoolean(IS_SHORTCUT_SWIPE_RIGHT, false)
        set(value) = prefs.edit { putBoolean(IS_SHORTCUT_SWIPE_RIGHT, value) }

    fun getAppName(location: Int, set: HomeSet = activeHomeSet()): String =
        if (location in 1..HomeSets.MAX_SLOTS) prefs.getString(HomeSets.slotKey(set, HomeSlotField.NAME, location), "").toString() else ""

    fun getAppPackage(location: Int, set: HomeSet = activeHomeSet()): String =
        if (location in 1..HomeSets.MAX_SLOTS) prefs.getString(HomeSets.slotKey(set, HomeSlotField.PACKAGE, location), "").toString() else ""

    fun getAppActivityClassName(location: Int, set: HomeSet = activeHomeSet()): String =
        if (location in 1..HomeSets.MAX_SLOTS) prefs.getString(HomeSets.slotKey(set, HomeSlotField.ACTIVITY, location), "").toString() else ""

    fun getAppUser(location: Int, set: HomeSet = activeHomeSet()): String =
        if (location in 1..HomeSets.MAX_SLOTS) prefs.getString(HomeSets.slotKey(set, HomeSlotField.USER, location), "").toString() else ""

    fun getShortcutId(location: Int, set: HomeSet = activeHomeSet()): String =
        if (location in 1..HomeSets.MAX_SLOTS) prefs.getString(HomeSets.slotKey(set, HomeSlotField.SHORTCUT_ID, location), "").toString() else ""

    fun getIsShortcut(location: Int, set: HomeSet = activeHomeSet()): Boolean =
        location in 1..HomeSets.MAX_SLOTS && prefs.getBoolean(HomeSets.slotKey(set, HomeSlotField.IS_SHORTCUT, location), false)

    fun setAppActivityClassName(location: Int, activityClassName: String) {
        when (location) {
            1 -> appActivityClassName1 = activityClassName
            2 -> appActivityClassName2 = activityClassName
            3 -> appActivityClassName3 = activityClassName
            4 -> appActivityClassName4 = activityClassName
            5 -> appActivityClassName5 = activityClassName
            6 -> appActivityClassName6 = activityClassName
            7 -> appActivityClassName7 = activityClassName
            8 -> appActivityClassName8 = activityClassName
        }
    }

    // Home app sets (Morning / Day / Evening)
    var homeSets: Boolean
        get() = prefs.getBoolean(HOME_SETS, false)
        set(value) = prefs.edit { putBoolean(HOME_SETS, value) }

    var homeSetMorningStart: Int
        get() = prefs.getInt(HOME_SET_MORNING_START, HomeSets.DEFAULT_MORNING_START)
        set(value) = prefs.edit { putInt(HOME_SET_MORNING_START, HomeSets.normalize(value)) }

    var homeSetDayStart: Int
        get() = prefs.getInt(HOME_SET_DAY_START, HomeSets.DEFAULT_DAY_START)
        set(value) = prefs.edit { putInt(HOME_SET_DAY_START, HomeSets.normalize(value)) }

    var homeSetEveningStart: Int
        get() = prefs.getInt(HOME_SET_EVENING_START, HomeSets.DEFAULT_EVENING_START)
        set(value) = prefs.edit { putInt(HOME_SET_EVENING_START, HomeSets.normalize(value)) }

    fun homeSetStarts(): HomeSetStarts =
        HomeSetStarts(homeSetMorningStart, homeSetDayStart, homeSetEveningStart)

    fun homeSetStart(set: HomeSet): Int = homeSetStarts().startOf(set)

    fun setHomeSetStart(set: HomeSet, minutes: Int) {
        when (set) {
            HomeSet.MORNING -> homeSetMorningStart = minutes
            HomeSet.DAY -> homeSetDayStart = minutes
            HomeSet.EVENING -> homeSetEveningStart = minutes
        }
    }

    fun homeSetHasApps(set: HomeSet): Boolean {
        val num = homeAppsNum.coerceIn(0, HomeSets.MAX_SLOTS)
        return (1..num).any { getAppPackage(it, set).isNotBlank() }
    }

    var windDown: Boolean
        get() = prefs.getBoolean(WIND_DOWN, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN, value) }

    var windDownStart: Int
        get() = prefs.getInt(WIND_DOWN_START, WindDown.DEFAULT_START)
        set(value) = prefs.edit { putInt(WIND_DOWN_START, HomeSets.normalize(value)) }

    var windDownEnd: Int
        get() = prefs.getInt(WIND_DOWN_END, WindDown.DEFAULT_END)
        set(value) = prefs.edit { putInt(WIND_DOWN_END, HomeSets.normalize(value)) }

    var windDownGrey: Boolean
        get() = prefs.getBoolean(WIND_DOWN_GREY, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN_GREY, value) }

    /** True while the grey wallpaper is what is currently applied. */
    var windDownGreyApplied: Boolean
        get() = prefs.getBoolean(WIND_DOWN_GREY_APPLIED, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN_GREY_APPLIED, value) }

    var windDownSystemGrey: Boolean
        get() = prefs.getBoolean(WIND_DOWN_SYSTEM_GREY, false)
        set(value) = prefs.edit { putBoolean(WIND_DOWN_SYSTEM_GREY, value) }

    /** The daltonizer values we replaced with grey, or null when we hold nothing to restore. */
    var systemGreySaved: SystemGrey.Daltonizer?
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

    fun isWindDownActive(nowMinutes: Int = currentMinutesOfDay()): Boolean =
        windDown && WindDown.isActive(nowMinutes, windDownStart, windDownEnd)

    fun activeHomeSet(nowMinutes: Int = currentMinutesOfDay()): HomeSet {
        // Wind-down shows the Evening set (when sets are on and Evening has apps).
        if (homeSets && isWindDownActive(nowMinutes) && homeSetHasApps(HomeSet.EVENING)) return HomeSet.EVENING
        return HomeSets.active(nowMinutes, homeSetStarts(), homeSets) { homeSetHasApps(it) }
    }

    private fun currentMinutesOfDay(): Int {
        val now = java.time.LocalTime.now()
        return HomeSets.minutesOfDay(now.hour, now.minute)
    }

    fun setHomeSlot(
        set: HomeSet,
        slot: Int,
        name: String,
        packageName: String,
        user: String,
        activityClassName: String?,
        isShortcut: Boolean,
        shortcutId: String,
    ) {
        if (slot !in 1..HomeSets.MAX_SLOTS) return
        prefs.edit {
            putString(HomeSets.slotKey(set, HomeSlotField.NAME, slot), name)
            putString(HomeSets.slotKey(set, HomeSlotField.PACKAGE, slot), packageName)
            putString(HomeSets.slotKey(set, HomeSlotField.USER, slot), user)
            putString(HomeSets.slotKey(set, HomeSlotField.ACTIVITY, slot), activityClassName)
            putBoolean(HomeSets.slotKey(set, HomeSlotField.IS_SHORTCUT, slot), isShortcut)
            putString(HomeSets.slotKey(set, HomeSlotField.SHORTCUT_ID, slot), shortcutId)
        }
    }

    fun setHomeSlotName(set: HomeSet, slot: Int, name: String) {
        if (slot !in 1..HomeSets.MAX_SLOTS) return
        prefs.edit { putString(HomeSets.slotKey(set, HomeSlotField.NAME, slot), name) }
    }

    fun clearHomeSlotApp(set: HomeSet, slot: Int) {
        if (slot !in 1..HomeSets.MAX_SLOTS) return
        prefs.edit {
            putString(HomeSets.slotKey(set, HomeSlotField.NAME, slot), "")
            putString(HomeSets.slotKey(set, HomeSlotField.PACKAGE, slot), "")
        }
    }

    fun updateAppActivityClassName(packageName: String, activityClassName: String) {
        for (set in HomeSet.values()) {
            for (i in 1..HomeSets.MAX_SLOTS) {
                if (getAppPackage(i, set) == packageName) {
                    prefs.edit { putString(HomeSets.slotKey(set, HomeSlotField.ACTIVITY, i), activityClassName) }
                }
            }
        }
        if (clockAppPackage == packageName) clockAppClassName = activityClassName
        if (calendarAppPackage == packageName) calendarAppClassName = activityClassName
        if (screenTimeAppPackage == packageName) screenTimeAppClassName = activityClassName
        if (weatherAppPackage == packageName) weatherAppClassName = activityClassName
        if (appPackageSwipeLeft == packageName) appActivityClassNameSwipeLeft = activityClassName
        if (appPackageSwipeRight == packageName) appActivityClassNameRight = activityClassName
        for (slot in 1..Constants.MAX_SHORTCUT_SLOTS) {
            if (getShortcutPackage(slot) == packageName) prefs.edit { putString("SHORTCUT_CLASS_$slot", activityClassName) }
        }
    }

    // Home shortcut icon slots (1..MAX_SHORTCUT_SLOTS): default built-in or a chosen app
    fun getShortcutMode(slot: Int): Int = prefs.getInt("SHORTCUT_MODE_$slot", Constants.SHORTCUT_MODE_DEFAULT)

    fun getShortcutPackage(slot: Int): String = prefs.getString("SHORTCUT_PACKAGE_$slot", "").toString()

    fun getShortcutClass(slot: Int): String = prefs.getString("SHORTCUT_CLASS_$slot", "").toString()

    fun getShortcutUser(slot: Int): String = prefs.getString("SHORTCUT_USER_$slot", "").toString()

    fun getShortcutLabel(slot: Int): String = prefs.getString("SHORTCUT_LABEL_$slot", "").toString()

    fun setShortcutApp(slot: Int, label: String, packageName: String, className: String?, user: String) = prefs.edit {
        putInt("SHORTCUT_MODE_$slot", Constants.SHORTCUT_MODE_APP)
        putString("SHORTCUT_LABEL_$slot", label)
        putString("SHORTCUT_PACKAGE_$slot", packageName)
        putString("SHORTCUT_CLASS_$slot", className.orEmpty())
        putString("SHORTCUT_USER_$slot", user)
    }

    fun setShortcutMode(slot: Int, mode: Int) = prefs.edit { putInt("SHORTCUT_MODE_$slot", mode) }

    // Built-in action chosen for a slot (ShortcutAction.id); NONE_ID falls back to the slot's default
    fun getShortcutBuiltin(slot: Int): Int = prefs.getInt("SHORTCUT_BUILTIN_$slot", ShortcutAction.NONE_ID)

    fun setShortcutBuiltin(slot: Int, actionId: Int) = prefs.edit {
        putInt("SHORTCUT_MODE_$slot", Constants.SHORTCUT_MODE_DEFAULT)
        putInt("SHORTCUT_BUILTIN_$slot", actionId)
    }

    fun resetShortcut(slot: Int) = prefs.edit {
        putInt("SHORTCUT_MODE_$slot", Constants.SHORTCUT_MODE_DEFAULT)
        remove("SHORTCUT_BUILTIN_$slot")
    }

    fun getShortcutAction(slot: Int): ShortcutAction? = ShortcutAction.resolve(slot, getShortcutBuiltin(slot))

    fun getAppRenameLabel(appPackage: String): String = prefs.getString(appPackage, "").toString()

    fun setAppRenameLabel(appPackage: String, renameLabel: String) = prefs.edit { putString(appPackage, renameLabel) }

    fun hasSavedDistractingApps(): Boolean = prefs.contains(DISTRACTING_APPS)

    internal var epochDaySupplier: () -> Long = { java.time.LocalDate.now().toEpochDay() }

    private fun checkAndResetOwnOpensDay() {
        val today = epochDaySupplier()
        val savedDay = prefs.getLong(OWN_OPENS_DAY, 0L)
        if (savedDay != today) {
            prefs.edit {
                prefs.all.keys
                    .filter { it.startsWith(OWN_OPENS_PREFIX) && it != OWN_OPENS_DAY }
                    .forEach { remove(it) }
                putLong(OWN_OPENS_DAY, today)
            }
        }
    }

    fun ownOpenCount(pkg: String): Int {
        checkAndResetOwnOpensDay()
        return prefs.getInt("$OWN_OPENS_PREFIX$pkg", 0)
    }

    fun incrementOwnOpenCount(pkg: String): Int {
        checkAndResetOwnOpensDay()
        val key = "$OWN_OPENS_PREFIX$pkg"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit { putInt(key, count).apply() }
        return count
    }

    fun isTodayCardEnabled(key: String): Boolean {
        val default = TodayCards.all.firstOrNull { it.key == key }?.defaultEnabled ?: true
        return prefs.getBoolean("$TODAY_CARD_PREFIX$key", default)
    }

    fun isTodayCardEnabled(card: TodayCard): Boolean = isTodayCardEnabled(card.key)

    fun setTodayCardEnabled(key: String, enabled: Boolean) = prefs.edit {
        putBoolean("$TODAY_CARD_PREFIX$key", enabled).apply()
    }

    var todayOrderSmart: Boolean
        get() = prefs.getBoolean(TODAY_ORDER_SMART, true)
        set(value) = prefs.edit { putBoolean(TODAY_ORDER_SMART, value).apply() }

    var nudgeWindowStartMin: Int
        get() = prefs.getInt(NUDGE_WINDOW_START_MIN, 540)
        set(value) = prefs.edit { putInt(NUDGE_WINDOW_START_MIN, value).apply() }

    var nudgeWindowEndMin: Int
        get() = prefs.getInt(NUDGE_WINDOW_END_MIN, 1260)
        set(value) = prefs.edit { putInt(NUDGE_WINDOW_END_MIN, value).apply() }

    var nudgeWaterIntervalMin: Int
        get() = prefs.getInt(NUDGE_WATER_INTERVAL_MIN, 0)
        set(value) = prefs.edit { putInt(NUDGE_WATER_INTERVAL_MIN, value).apply() }

    var nudgeWalkIntervalMin: Int
        get() = prefs.getInt(NUDGE_WALK_INTERVAL_MIN, 0)
        set(value) = prefs.edit { putInt(NUDGE_WALK_INTERVAL_MIN, value).apply() }

    var healthConnectEnabled: Boolean
        get() = prefs.getBoolean(HEALTH_CONNECT_ENABLED, false)
        set(value) = prefs.edit { putBoolean(HEALTH_CONNECT_ENABLED, value).apply() }

    fun checkAndResetNudgeDoneDay() {
        val today = epochDaySupplier()
        val savedDay = prefs.getLong(NUDGE_DONE_DAY, 0L)
        if (savedDay == 0L) {
            prefs.edit { putLong(NUDGE_DONE_DAY, today) }
            return
        }
        if (savedDay != today) {
            val water = prefs.getInt(nudgeDoneKey(Nudges.WATER), 0)
            val walk = prefs.getInt(nudgeDoneKey(Nudges.WALK), 0)

            val snapshot = DaySnapshot(
                epochDay = savedDay,
                water = water,
                walk = walk,
            )
            try {
                usageSnapshotStore?.upsert(snapshot)
            } catch (_: Exception) {}

            onNudgeDoneRollover?.invoke(savedDay, water, walk)
            if (onNudgeDoneRollover !== Companion.onNudgeDoneRollover) {
                Companion.onNudgeDoneRollover?.invoke(savedDay, water, walk)
            }

            prefs.edit {
                prefs.all.keys
                    .filter { it.startsWith(NUDGE_DONE_PREFIX) && it != NUDGE_DONE_DAY }
                    .forEach { remove(it) }
                putLong(NUDGE_DONE_DAY, today)
            }
        }
    }

    fun checkRollovers() {
        pauseLog.checkRollover()
        checkAndResetNudgeDoneDay()
    }

    fun nudgeDone(type: String): Int {
        checkAndResetNudgeDoneDay()
        return prefs.getInt(nudgeDoneKey(type), 0)
    }

    fun incrementNudgeDone(type: String): Int {
        checkAndResetNudgeDoneDay()
        val key = nudgeDoneKey(type)
        val count = prefs.getInt(key, 0) + 1
        prefs.edit { putInt(key, count).apply() }
        return count
    }

    fun setNudgeDone(type: String, count: Int) {
        checkAndResetNudgeDoneDay()
        prefs.edit { putInt(nudgeDoneKey(type), count).apply() }
    }

    var nudgeSocialThresholdMin: Int
        get() = prefs.getInt(NUDGE_SOCIAL_THRESHOLD_MIN, 0)
        set(value) = prefs.edit { putInt(NUDGE_SOCIAL_THRESHOLD_MIN, value).apply() }

    var nudgeSocialRepeatMin: Int
        get() = prefs.getInt(NUDGE_SOCIAL_REPEAT_MIN, 15)
        set(value) = prefs.edit { putInt(NUDGE_SOCIAL_REPEAT_MIN, value).apply() }

    var nudgeSocialDay: Long
        get() = prefs.getLong(NUDGE_SOCIAL_DAY, 0L)
        set(value) = prefs.edit { putLong(NUDGE_SOCIAL_DAY, value).apply() }

    var nudgeSocialLastBucket: Int
        get() {
            val today = epochDaySupplier()
            if (prefs.getLong(NUDGE_SOCIAL_DAY, 0L) != today) {
                return -1
            }
            return prefs.getInt(NUDGE_SOCIAL_LAST_BUCKET, -1)
        }
        set(value) = prefs.edit {
            putLong(NUDGE_SOCIAL_DAY, epochDaySupplier())
            putInt(NUDGE_SOCIAL_LAST_BUCKET, value).apply()
        }

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(AUTO_BACKUP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(AUTO_BACKUP_ENABLED, value).apply() }

    var autoBackupTreeUri: String
        get() = prefs.getString(AUTO_BACKUP_TREE_URI, "") ?: ""
        set(value) = prefs.edit { putString(AUTO_BACKUP_TREE_URI, value).apply() }

    var autoBackupLastOk: Long
        get() = prefs.getLong(AUTO_BACKUP_LAST_OK, 0L)
        set(value) = prefs.edit { putLong(AUTO_BACKUP_LAST_OK, value).apply() }

    var autoBackupLastFailed: Boolean
        get() = prefs.getBoolean(AUTO_BACKUP_LAST_FAILED, false)
        set(value) = prefs.edit { putBoolean(AUTO_BACKUP_LAST_FAILED, value).apply() }

    var weatherEnabled: Boolean
        get() = prefs.getBoolean(WEATHER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(WEATHER_ENABLED, value).apply() }

    var weatherPlaceName: String
        get() = prefs.getString(WEATHER_PLACE_NAME, "") ?: ""
        set(value) = prefs.edit { putString(WEATHER_PLACE_NAME, value).apply() }

    var weatherLat: String
        get() = prefs.getString(WEATHER_LAT, "") ?: ""
        set(value) = prefs.edit { putString(WEATHER_LAT, value).apply() }

    var weatherLon: String
        get() = prefs.getString(WEATHER_LON, "") ?: ""
        set(value) = prefs.edit { putString(WEATHER_LON, value).apply() }

    var weatherUnit: String
        get() = prefs.getString(WEATHER_UNIT, defaultWeatherUnit()) ?: defaultWeatherUnit()
        set(value) = prefs.edit { putString(WEATHER_UNIT, value).apply() }

    var weatherFetchedAt: Long
        get() = prefs.getLong(WEATHER_FETCHED_AT, 0L)
        set(value) = prefs.edit { putLong(WEATHER_FETCHED_AT, value).apply() }

    var weatherUseLocation: Boolean
        get() = prefs.getBoolean(WEATHER_USE_LOCATION, false)
        set(value) = prefs.edit { putBoolean(WEATHER_USE_LOCATION, value).apply() }

    var weatherLocationAt: Long
        get() = prefs.getLong(WEATHER_LOCATION_AT, 0L)
        set(value) = prefs.edit { putLong(WEATHER_LOCATION_AT, value).apply() }

    var searchEngine: String
        get() = prefs.getString(SEARCH_ENGINE, SearchEngine.PHONE_DEFAULT) ?: SearchEngine.PHONE_DEFAULT
        set(value) = prefs.edit { putString(SEARCH_ENGINE, value).apply() }

    var searchEngineCustomUrl: String
        get() = prefs.getString(SEARCH_ENGINE_CUSTOM_URL, "") ?: ""
        set(value) = prefs.edit { putString(SEARCH_ENGINE_CUSTOM_URL, value).apply() }

    fun registerPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)

    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit { putLong(key, value).apply() }
    }

    override fun putInt(key: String, value: Int) {
        prefs.edit { putInt(key, value).apply() }
    }

    override fun contains(key: String): Boolean = prefs.contains(key)
}