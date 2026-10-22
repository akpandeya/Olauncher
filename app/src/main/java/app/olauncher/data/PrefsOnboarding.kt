package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.AUTO_BACKUP_ENABLED
import app.olauncher.data.Prefs.Companion.AUTO_BACKUP_LAST_FAILED
import app.olauncher.data.Prefs.Companion.AUTO_BACKUP_LAST_OK
import app.olauncher.data.Prefs.Companion.AUTO_BACKUP_TREE_URI

/** Settings group: Onboarding (see [Prefs]). */
interface OnboardingPrefs {
    /** Saved first-run progress, see OnboardingStore. Empty until onboarding starts. */
    var onboardingState: String

    var firstOpen: Boolean

    var gestureHintIndex: Int

    var gestureHintDay: Long

    var firstOpenTime: Long

    var firstSettingsOpen: Boolean

    var firstHide: Boolean

    var userState: String

    var lockModeOn: Boolean

    var autoShowKeyboard: Boolean

    var keyboardMessageShown: Boolean

    var proMessageShown: Boolean

    var hideSetDefaultLauncher: Boolean

    var screenTimeLastUpdated: Long

    var screenTimeStyle: Int

    var screenTimeTargetMin: Int

    var launcherRestartTimestamp: Long

    var shownOnDayOfYear: Int

    var hiddenApps: MutableSet<String>

    var hiddenAppsUpdated: Boolean

    var toShowHintCounter: Int

    var aboutClicked: Boolean

    var rateClicked: Boolean

    var wallpaperMsgShown: Boolean

    var shareShownTime: Long

    var autoBackupEnabled: Boolean

    var autoBackupTreeUri: String

    var autoBackupLastOk: Long

    var autoBackupLastFailed: Boolean
}

internal class OnboardingPrefsStore(private val prefs: SharedPreferences) : OnboardingPrefs {
    private val ONBOARDING_STATE = "ONBOARDING_STATE"
    private val FIRST_OPEN = "FIRST_OPEN"
    private val FIRST_OPEN_TIME = "FIRST_OPEN_TIME"
    private val GESTURE_HINT_INDEX = "GESTURE_HINT_INDEX"
    private val GESTURE_HINT_DAY = "GESTURE_HINT_DAY"
    private val FIRST_SETTINGS_OPEN = "FIRST_SETTINGS_OPEN"
    private val FIRST_HIDE = "FIRST_HIDE"
    private val USER_STATE = "USER_STATE"
    private val LOCK_MODE = "LOCK_MODE"
    private val AUTO_SHOW_KEYBOARD = "AUTO_SHOW_KEYBOARD"
    private val KEYBOARD_MESSAGE = "KEYBOARD_MESSAGE"
    private val HIDDEN_APPS = "HIDDEN_APPS"
    private val HIDDEN_APPS_UPDATED = "HIDDEN_APPS_UPDATED"
    private val SHOW_HINT_COUNTER = "SHOW_HINT_COUNTER"
    private val ABOUT_CLICKED = "ABOUT_CLICKED"
    private val RATE_CLICKED = "RATE_CLICKED"
    private val WALLPAPER_MSG_SHOWN = "WALLPAPER_MSG_SHOWN"
    private val SHARE_SHOWN_TIME = "SHARE_SHOWN_TIME"
    private val PRO_MESSAGE_SHOWN = "PRO_MESSAGE_SHOWN"
    private val HIDE_SET_DEFAULT_LAUNCHER = "HIDE_SET_DEFAULT_LAUNCHER"
    private val SCREEN_TIME_LAST_UPDATED = "SCREEN_TIME_LAST_UPDATED"
    private val SCREEN_TIME_STYLE = "SCREEN_TIME_STYLE"
    private val SCREEN_TIME_TARGET_MIN = "SCREEN_TIME_TARGET_MIN"
    private val LAUNCHER_RESTART_TIMESTAMP = "LAUNCHER_RECREATE_TIMESTAMP"
    private val SHOWN_ON_DAY_OF_YEAR = "SHOWN_ON_DAY_OF_YEAR"

    /** Saved first-run progress, see OnboardingStore. Empty until onboarding starts. */
    override var onboardingState: String
        get() = prefs.getString(ONBOARDING_STATE, "") ?: ""
        set(value) = prefs.edit { putString(ONBOARDING_STATE, value).apply() }

    override var firstOpen: Boolean
        get() = prefs.getBoolean(FIRST_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_OPEN, value).apply() }

    override var gestureHintIndex: Int
        get() = prefs.getInt(GESTURE_HINT_INDEX, 0)
        set(value) = prefs.edit { putInt(GESTURE_HINT_INDEX, value).apply() }

    override var gestureHintDay: Long
        get() = prefs.getLong(GESTURE_HINT_DAY, 0L)
        set(value) = prefs.edit { putLong(GESTURE_HINT_DAY, value).apply() }

    override var firstOpenTime: Long
        get() = prefs.getLong(FIRST_OPEN_TIME, 0L)
        set(value) = prefs.edit { putLong(FIRST_OPEN_TIME, value).apply() }

    override var firstSettingsOpen: Boolean
        get() = prefs.getBoolean(FIRST_SETTINGS_OPEN, true)
        set(value) = prefs.edit { putBoolean(FIRST_SETTINGS_OPEN, value).apply() }

    override var firstHide: Boolean
        get() = prefs.getBoolean(FIRST_HIDE, true)
        set(value) = prefs.edit { putBoolean(FIRST_HIDE, value).apply() }

    override var userState: String
        get() = prefs.getString(USER_STATE, Constants.UserState.START).toString()
        set(value) = prefs.edit { putString(USER_STATE, value).apply() }

    override var lockModeOn: Boolean
        get() = prefs.getBoolean(LOCK_MODE, false)
        set(value) = prefs.edit { putBoolean(LOCK_MODE, value).apply() }

    override var autoShowKeyboard: Boolean
        get() = prefs.getBoolean(AUTO_SHOW_KEYBOARD, true)
        set(value) = prefs.edit { putBoolean(AUTO_SHOW_KEYBOARD, value).apply() }

    override var keyboardMessageShown: Boolean
        get() = prefs.getBoolean(KEYBOARD_MESSAGE, false)
        set(value) = prefs.edit { putBoolean(KEYBOARD_MESSAGE, value).apply() }

    override var proMessageShown: Boolean
        get() = prefs.getBoolean(PRO_MESSAGE_SHOWN, false)
        set(value) = prefs.edit { putBoolean(PRO_MESSAGE_SHOWN, value).apply() }

    override var hideSetDefaultLauncher: Boolean
        get() = prefs.getBoolean(HIDE_SET_DEFAULT_LAUNCHER, false)
        set(value) = prefs.edit { putBoolean(HIDE_SET_DEFAULT_LAUNCHER, value).apply() }

    override var screenTimeLastUpdated: Long
        get() = prefs.getLong(SCREEN_TIME_LAST_UPDATED, 0L)
        set(value) = prefs.edit { putLong(SCREEN_TIME_LAST_UPDATED, value).apply() }

    override var screenTimeStyle: Int
        get() = prefs.getInt(SCREEN_TIME_STYLE, 0)
        set(value) = prefs.edit { putInt(SCREEN_TIME_STYLE, value).apply() }

    override var screenTimeTargetMin: Int
        get() = prefs.getInt(SCREEN_TIME_TARGET_MIN, 120)
        set(value) = prefs.edit { putInt(SCREEN_TIME_TARGET_MIN, value).apply() }

    override var launcherRestartTimestamp: Long
        get() = prefs.getLong(LAUNCHER_RESTART_TIMESTAMP, 0L)
        set(value) = prefs.edit { putLong(LAUNCHER_RESTART_TIMESTAMP, value).apply() }

    override var shownOnDayOfYear: Int
        get() = prefs.getInt(SHOWN_ON_DAY_OF_YEAR, 0)
        set(value) = prefs.edit { putInt(SHOWN_ON_DAY_OF_YEAR, value).apply() }

    override var hiddenApps: MutableSet<String>
        get() = prefs.getStringSet(HIDDEN_APPS, mutableSetOf()) as MutableSet<String>
        set(value) = prefs.edit { putStringSet(HIDDEN_APPS, value).apply() }

    override var hiddenAppsUpdated: Boolean
        get() = prefs.getBoolean(HIDDEN_APPS_UPDATED, false)
        set(value) = prefs.edit { putBoolean(HIDDEN_APPS_UPDATED, value).apply() }

    override var toShowHintCounter: Int
        get() = prefs.getInt(SHOW_HINT_COUNTER, 1)
        set(value) = prefs.edit { putInt(SHOW_HINT_COUNTER, value).apply() }

    override var aboutClicked: Boolean
        get() = prefs.getBoolean(ABOUT_CLICKED, false)
        set(value) = prefs.edit { putBoolean(ABOUT_CLICKED, value).apply() }

    override var rateClicked: Boolean
        get() = prefs.getBoolean(RATE_CLICKED, false)
        set(value) = prefs.edit { putBoolean(RATE_CLICKED, value).apply() }

    override var wallpaperMsgShown: Boolean
        get() = prefs.getBoolean(WALLPAPER_MSG_SHOWN, false)
        set(value) = prefs.edit { putBoolean(WALLPAPER_MSG_SHOWN, value).apply() }

    override var shareShownTime: Long
        get() = prefs.getLong(SHARE_SHOWN_TIME, 0L)
        set(value) = prefs.edit { putLong(SHARE_SHOWN_TIME, value).apply() }

    override var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(AUTO_BACKUP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(AUTO_BACKUP_ENABLED, value).apply() }

    override var autoBackupTreeUri: String
        get() = prefs.getString(AUTO_BACKUP_TREE_URI, "") ?: ""
        set(value) = prefs.edit { putString(AUTO_BACKUP_TREE_URI, value).apply() }

    override var autoBackupLastOk: Long
        get() = prefs.getLong(AUTO_BACKUP_LAST_OK, 0L)
        set(value) = prefs.edit { putLong(AUTO_BACKUP_LAST_OK, value).apply() }

    override var autoBackupLastFailed: Boolean
        get() = prefs.getBoolean(AUTO_BACKUP_LAST_FAILED, false)
        set(value) = prefs.edit { putBoolean(AUTO_BACKUP_LAST_FAILED, value).apply() }
}
