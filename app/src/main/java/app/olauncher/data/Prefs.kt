package app.olauncher.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

import java.util.Locale
import app.olauncher.glance.CalendarEvent
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSetStarts
import app.olauncher.helper.HomeSets
import app.olauncher.wellbeing.WindDown
import app.olauncher.helper.HomeSlotField
import app.olauncher.helper.RecentApp
import app.olauncher.helper.RecentApps
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayCards
import app.olauncher.ui.today.TodayOrder
import app.olauncher.ui.today.TodayWidgetResize
import app.olauncher.wellbeing.DaySnapshot
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.PauseDay
import app.olauncher.wellbeing.toDaySnapshot
import app.olauncher.wellbeing.PauseLog
import app.olauncher.wellbeing.PauseLogStorage
import app.olauncher.wellbeing.UsageSnapshotStore
import app.olauncher.wellbeing.WalkNudgePolicy

class Prefs(context: Context) :
    PauseLogStorage,
    HomeSlotPrefs by HomeSlotPrefsStore(context.appPrefs()),
    AppTargetPrefs by AppTargetPrefsStore(context.appPrefs()),
    WeatherPrefs by WeatherPrefsStore(context.appPrefs()),
    GermanPrefs by GermanPrefsStore(context.appPrefs()),
    PausePrefs by PausePrefsStore(context.appPrefs()),
    WindDownPrefs by WindDownPrefsStore(context.appPrefs()),
    NudgePrefs by NudgePrefsStore(context.appPrefs()),
    HomeLookPrefs by HomeLookPrefsStore(context.appPrefs()),
    AppearancePrefs by AppearancePrefsStore(context.appPrefs()),
    WallpaperPrefs by WallpaperPrefsStore(context.appPrefs()),
    DrawerPrefs by DrawerPrefsStore(context.appPrefs()),
    ProfilePrefs by ProfilePrefsStore(context.appPrefs()),
    GesturePrefs by GesturePrefsStore(context.appPrefs()),
    OnboardingPrefs by OnboardingPrefsStore(context.appPrefs()) {
    private val appContext: Context by lazy { context.applicationContext ?: context }

    /** The permissions of the profile features, read now. Never asks for one. */
    fun currentProfilePermissions(): ProfilePermissions = ProfilePermissions(
        weather = app.olauncher.glance.WeatherLocation.hasPermission(appContext),
        calendar = hasPermission(android.Manifest.permission.READ_CALENDAR),
        notificationAccess = app.olauncher.quiet.QuietAccess.isNotificationAccessGranted(appContext),
        contacts = hasPermission(android.Manifest.permission.READ_CONTACTS),
    )

    private fun hasPermission(permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(appContext, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    companion object {
        /** Shared by every [Prefs] instance, so a recreated screen keeps the events already read. */
        @Volatile
        private var sharedProfileCalendarEvents: List<CalendarEvent>? = null

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

        private val HOME_SET_PROFILE_IDS = setOf(HomeSetProfiles.MORNING_ID, HomeSetProfiles.DAY_ID, HomeSetProfiles.EVENING_ID)
        const val HOME_SETS = "HOME_SETS"
        const val HOME_SET_MORNING_START = "HOME_SET_MORNING_START"
        const val HOME_SET_DAY_START = "HOME_SET_DAY_START"
        const val HOME_SET_EVENING_START = "HOME_SET_EVENING_START"

        const val DRAWER_BOTTOM_SEARCH = "DRAWER_BOTTOM_SEARCH"
        const val SWIPE_RIGHT_ACTION_TODAY = 0
        const val SWIPE_RIGHT_ACTION_APP = 1
        const val SWIPE_RIGHT_ACTION = "SWIPE_RIGHT_ACTION"
        const val TODAY_CARD_PREFIX = "TODAY_CARD_"
        const val TODAY_TILE_PREFIX = "TODAY_TILE_"
        const val TODAY_ORDER_SMART = "TODAY_ORDER_SMART"
        const val TODAY_WIDGET_IDS = "TODAY_WIDGET_IDS"
        const val TODAY_WIDGET_HEIGHTS = "TODAY_WIDGET_HEIGHTS"
        const val TODAY_PENDING_WIDGET_ID = "TODAY_PENDING_WIDGET_ID"
        const val TODAY_PANEL_ORDER = "TODAY_PANEL_ORDER"
        const val TODAY_PINNED_KEYS = "TODAY_PINNED_KEYS"
        const val WELLBEING_SHOW_WEEK = "WELLBEING_SHOW_WEEK"
        const val NUDGE_WINDOW_START_MIN = "NUDGE_WINDOW_START_MIN"
        const val NUDGE_WINDOW_END_MIN = "NUDGE_WINDOW_END_MIN"
        const val NUDGE_WATER_INTERVAL_MIN = "NUDGE_WATER_INTERVAL_MIN"
        const val NUDGE_WALK_INTERVAL_MIN = "NUDGE_WALK_INTERVAL_MIN"
        const val HEALTH_CONNECT_ENABLED = "HEALTH_CONNECT_ENABLED"
        const val NUDGE_DONE_DAY = "NUDGE_DONE_DAY"
        const val NUDGE_DONE_PREFIX = "NUDGE_DONE_"
        const val NUDGE_AUTO_WALK_COUNT = "NUDGE_AUTO_WALK_COUNT"
        const val NUDGE_AUTO_WALK_DAY = "NUDGE_AUTO_WALK_DAY"
        const val NUDGE_SOCIAL_THRESHOLD_MIN = "NUDGE_SOCIAL_THRESHOLD_MIN"
        const val NUDGE_SOCIAL_REPEAT_MIN = "NUDGE_SOCIAL_REPEAT_MIN"
        const val NUDGE_SOCIAL_LAST_BUCKET = "NUDGE_SOCIAL_LAST_BUCKET"
        const val NUDGE_SOCIAL_DAY = "NUDGE_SOCIAL_DAY"
        const val GERMAN_ENABLED = "GERMAN_ENABLED"
        const val GERMAN_UI = "GERMAN_UI"
        const val GERMAN_OFFLINE_DICTIONARY = "GERMAN_OFFLINE_DICTIONARY"
        const val GERMAN_LOOKUP = "GERMAN_LOOKUP"
        const val GERMAN_DICTIONARY_APP = "GERMAN_DICTIONARY_APP"
        const val GERMAN_SCREEN_PICK = "GERMAN_SCREEN_PICK"
        const val GERMAN_SPELL_SUGGEST = "GERMAN_SPELL_SUGGEST"
        const val GERMAN_PAUSE_WORD = "GERMAN_PAUSE_WORD"
        const val GERMAN_PAUSE_LAST_WORD_ID = "GERMAN_PAUSE_LAST_WORD_ID"
        const val GERMAN_CLOCK = "GERMAN_CLOCK"
        const val GERMAN_CLOCK_SYSTEM = 0
        const val GERMAN_CLOCK_DEUTSCH = 1
        const val GERMAN_CLOCK_IN_WORTEN = 2
        const val DRILL_LAST_TYPE = "DRILL_LAST_TYPE"
        const val BATTERY_MODE = "BATTERY_MODE"
        const val BATTERY_MODE_AUTO = 0
        const val BATTERY_MODE_ALWAYS = 1
        const val BATTERY_MODE_OFF = 2
        const val TEXT_CONTRAST_AUTO = 0
        const val TEXT_CONTRAST_THEME = 1
        const val WASH_AUTO = 0
        const val WASH_STRONG = 1
        const val WASH_OFF = 2
        const val BRIGHTNESS_FULL = 0
        const val BRIGHTNESS_SOFT = 1
        const val BRIGHTNESS_DIM = 2
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
        const val LETTER_STRIP_HINT_SHOWN = "LETTER_STRIP_HINT_SHOWN"
        const val NEW_APP_TIP_SEEN = "NEW_APP_TIP_SEEN"
        const val DRAWER_RECENTS = "DRAWER_RECENTS"
        const val SEARCH_CONTACTS = "SEARCH_CONTACTS"
        const val CURRENCY_RATES = "CURRENCY_RATES"
        const val RECENT_APPS = "RECENT_APPS"
        const val QUIET_ENABLED = "QUIET_ENABLED"
        const val QUIET_RELEASE_TIMES = "QUIET_RELEASE_TIMES"
        const val DEFAULT_QUIET_RELEASE_TIMES = "750,1110"
        const val FONT_FAMILY = "FONT_FAMILY"
        const val ACCENT = "ACCENT"
        const val ACCENT_CUSTOM_COLOR = "ACCENT_CUSTOM_COLOR"
        const val CLOCK_FROM_WALLPAPER = "CLOCK_FROM_WALLPAPER"
        const val CLOCK_WALLPAPER_COLOR = "CLOCK_WALLPAPER_COLOR"
        const val HAPTICS = "HAPTICS"
        const val HAPTIC_STRENGTH = "HAPTIC_STRENGTH"
        const val HAPTIC_DURATION_MS = "HAPTIC_DURATION_MS"

        fun nudgeDoneKey(type: String): String = "$NUDGE_DONE_PREFIX${type.uppercase()}"

        var onNudgeDoneRollover: ((epochDay: Long, water: Int, walk: Int) -> Unit)? = null
        var onPauseLogRollover: ((previous: PauseDay) -> Unit)? = null
    }

    private val HOME_APPS_NUM = "HOME_APPS_NUM"
    private val DISTRACTING_APPS = "DISTRACTING_APPS"
    private val OWN_OPENS_DAY = "OWN_OPENS_DAY"
    private val OWN_OPENS_PREFIX = "OWN_OPENS_"
    // Home button for recents feature disabled
    // private val HOME_BUTTON_SHOW_RECENTS = "HOME_BUTTON_SHOW_RECENTS"





    private val prefs: SharedPreferences = context.appPrefs()

    var usageSnapshotStore: UsageSnapshotStore? = try {
        UsageSnapshotStore(context.filesDir)
    } catch (_: Exception) {
        null
    }

    var onNudgeDoneRollover: ((epochDay: Long, water: Int, walk: Int) -> Unit)? = null
    var onPauseLogRollover: ((previous: PauseDay) -> Unit)? = null

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

    var homeAppsNum: Int
        get() = prefs.getInt(HOME_APPS_NUM, 4)
        set(value) = prefs.edit { putInt(HOME_APPS_NUM, value).apply() }

    // Home button for recents feature disabled
    // var homeButtonShowRecents: Boolean
    //     get() = prefs.getBoolean(HOME_BUTTON_SHOW_RECENTS, false)
    //     set(value) = prefs.edit { putBoolean(HOME_BUTTON_SHOW_RECENTS, value).apply() }

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
        set(value) {
            val changed = value != homeSets
            prefs.edit { putBoolean(HOME_SETS, value) }
            // A change to the schedule's rules ends a manual switch.
            if (changed) clearManualProfile()
        }

    var homeSetMorningStart: Int
        get() = prefs.getInt(HOME_SET_MORNING_START, HomeSets.DEFAULT_MORNING_START)
        set(value) {
            val changed = HomeSets.normalize(value) != homeSetMorningStart
            prefs.edit { putInt(HOME_SET_MORNING_START, HomeSets.normalize(value)) }
            if (changed) clearManualProfile()
        }

    var homeSetDayStart: Int
        get() = prefs.getInt(HOME_SET_DAY_START, HomeSets.DEFAULT_DAY_START)
        set(value) {
            val changed = HomeSets.normalize(value) != homeSetDayStart
            prefs.edit { putInt(HOME_SET_DAY_START, HomeSets.normalize(value)) }
            if (changed) clearManualProfile()
        }

    var homeSetEveningStart: Int
        get() = prefs.getInt(HOME_SET_EVENING_START, HomeSets.DEFAULT_EVENING_START)
        set(value) {
            val changed = HomeSets.normalize(value) != homeSetEveningStart
            prefs.edit { putInt(HOME_SET_EVENING_START, HomeSets.normalize(value)) }
            if (changed) clearManualProfile()
        }

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

    fun isWindDownActive(nowMinutes: Int = currentMinutesOfDay()): Boolean =
        windDown && WindDown.isActive(nowMinutes, windDownStart, windDownEnd)

    /**
     * The schedule in force: the saved one, else the one built from the old Home-set keys
     * (read live, never removed), else nothing but the Day default when Home sets are off.
     */
    fun profileSchedule(): ProfileSchedule {
        val saved = profileScheduleJson
        if (saved.isNotBlank()) {
            try {
                return ProfileSchedule.fromJson(saved)
            } catch (e: ProfileFormatException) {
                // A damaged saved schedule falls back to the Home-set times.
            }
        }
        return if (homeSets) HomeSetProfiles.scheduleFrom(homeSetStarts())
        else ProfileSchedule(HomeSetProfiles.DAY_ID, emptyList())
    }

    /** True when the user has saved their own schedule rules. */
    val hasSavedProfileSchedule: Boolean
        get() = profileScheduleJson.isNotBlank() && try {
            ProfileSchedule.fromJson(profileScheduleJson)
            true
        } catch (e: ProfileFormatException) {
            false
        }

    /** True when the active profile can change with time, so the home tick must run. */
    val profileScheduleActive: Boolean
        get() = homeSets || hasSavedProfileSchedule

    /**
     * Events running now, last read for the calendar rules; null while calendar access is missing or
     * nothing was read yet. Kept in memory only: the home screen refreshes it off the main thread.
     */
    var profileCalendarEvents: List<CalendarEvent>?
        get() = sharedProfileCalendarEvents
        set(value) {
            sharedProfileCalendarEvents = value
        }

    /** True when the saved schedule has rules that follow the calendar. */
    val hasProfileEventRules: Boolean
        get() = profileSchedule().eventRules.isNotEmpty()

    private fun manualOverride(): ManualOverride = ManualOverride(manualProfileId, manualProfileUntil)

    fun activeProfileId(
        nowMinutes: Int = currentMinutesOfDay(),
        dayOfWeek: Int = currentDayOfWeek(),
        nowMillis: Long = System.currentTimeMillis(),
    ): String =
        ProfileScheduleResolver.activeProfileId(
            manualProfileId = manualOverride().takeIf { it.isActive(nowMillis) }?.profileId,
            schedule = profileSchedule(),
            nowMinutes = nowMinutes,
            dayOfWeek = dayOfWeek,
            windDownActive = isWindDownActive(nowMinutes),
            // Wind-down forces the Evening profile only when Home sets are on, as before.
            windDownProfileId = if (homeSets) HomeSetProfiles.EVENING_ID else null,
            runningEvents = profileCalendarEvents,
            nowMillis = nowMillis,
            hasApps = { if (it in HOME_SET_PROFILE_IDS) homeSetHasApps(HomeSetProfiles.setOf(it)) else true },
        )

    fun activeHomeSet(nowMinutes: Int = currentMinutesOfDay(), dayOfWeek: Int = currentDayOfWeek()): HomeSet =
        HomeSetProfiles.setOf(activeProfileId(nowMinutes, dayOfWeek))

    /**
     * Applies the settings bundle of the active profile when it differs from the last one seen.
     * Returns true when a bundle was written.
     */
    fun applyScheduledProfile(
        nowMinutes: Int = currentMinutesOfDay(),
        dayOfWeek: Int = currentDayOfWeek(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        // A manual switch ends at the next scheduled boundary; from then on the schedule decides.
        if (manualProfileId.isNotEmpty() && !manualOverride().isActive(nowMillis)) clearManualProfile()
        val activeId = activeProfileId(nowMinutes, dayOfWeek, nowMillis)
        val profiles = profiles()
        val toApply = ProfileScheduleResolver.profileToApply(activeId, appliedProfileId, profiles)
        if (activeId != appliedProfileId) appliedProfileId = activeId
        toApply?.let { ProfilePicker.applyBundle(it.settings, PrefsProfileTarget(this), currentProfilePermissions()) }
        return toApply != null
    }

    /** Saved profiles, then the built-in ones that are not saved. Profiles with a bundle only. */
    fun profiles(): List<Profile> {
        val saved = try {
            Profile.listFromJson(profilesJson)
        } catch (e: ProfileFormatException) {
            emptyList()
        }
        val savedIds = saved.map { it.id }.toSet()
        return saved + BuiltInProfiles.all(currentHomeApps()).filter { it.id !in savedIds }
    }

    /**
     * Switches to [profileId] by hand: applies its bundle (if it has one) and holds it until the
     * next scheduled boundary. Also used to reset a Custom profile, as the bundle is applied again.
     */
    fun switchProfile(
        profileId: String,
        nowMinutes: Int = currentMinutesOfDay(),
        dayOfWeek: Int = currentDayOfWeek(),
        nowMillis: Long = System.currentTimeMillis(),
        weatherGranted: Boolean? = null,
    ) {
        val override = ManualOverride.create(profileId, profileSchedule(), nowMillis, nowMinutes, dayOfWeek)
        manualProfileId = override.profileId
        manualProfileUntil = override.untilMillis
        appliedProfileId = profileId
        profiles().firstOrNull { it.id == profileId }
            ?.let {
                val permissions = currentProfilePermissions().let { p -> weatherGranted?.let { w -> p.copy(weather = w) } ?: p }
                ProfilePicker.applyBundle(it.settings, PrefsProfileTarget(this), permissions)
            }
    }

    fun clearManualProfile() {
        manualProfileId = ""
        manualProfileUntil = ManualOverride.NO_EXPIRY
    }

    /** What the profile row shows now: the applied profile, or Custom when its bundle was changed. */
    fun profileState(nameOf: (String) -> String): ProfileState =
        ProfileCustom.state(appliedProfileId.ifEmpty { activeProfileId() }, profiles(), nameOf, PrefsProfileTarget(this), currentProfilePermissions())

    /** Stores the settings now in force as the bundle of [profile], keeping its other fields. */
    fun saveToProfile(profile: Profile) {
        val updated = profile.copy(settings = ProfileCustom.capture(profile.settings, PrefsProfileTarget(this)))
        profilesJson = Profile.listToJson(profiles().map { if (it.id == profile.id) updated else it })
    }

    private fun currentDayOfWeek(): Int = java.time.LocalDate.now().dayOfWeek.value

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
        val canonical = TodayOrder.canonicalKey(key)
        val default = TodayCards.all.firstOrNull { it.key == canonical }?.defaultEnabled ?: true
        fun saved(k: String): Boolean? =
            if (prefs.contains("$TODAY_CARD_PREFIX$k")) prefs.getBoolean("$TODAY_CARD_PREFIX$k", default) else null
        if (canonical != TodayOrder.canonicalKey(TodayOrder.LEGACY_WEEK_KEY)) return saved(canonical) ?: default
        // The old week card merged into Wellbeing: its saved choice still counts.
        return TodayOrder.mergedEnabled(saved(canonical), saved(TodayOrder.LEGACY_WEEK_KEY), default, true)
    }

    fun isTodayCardEnabled(card: TodayCard): Boolean = isTodayCardEnabled(card.key)

    /** True when the card is shown as a compact tile (default off). */
    fun isTodayTileOn(key: String): Boolean = prefs.getBoolean("$TODAY_TILE_PREFIX$key", false)

    fun setTodayTileOn(key: String, on: Boolean) = prefs.edit { putBoolean("$TODAY_TILE_PREFIX$key", on) }

    var todayWidgetIds: List<Int>
        get() = prefs.getString(TODAY_WIDGET_IDS, "").orEmpty().split(",")
            .mapNotNull { it.toIntOrNull()?.takeIf { id -> id > 0 } }.distinct()
        set(value) = prefs.edit(commit = true) { putString(TODAY_WIDGET_IDS, value.joinToString(",")) }

    var todayPendingWidgetId: Int
        get() = prefs.getInt(TODAY_PENDING_WIDGET_ID, -1)
        set(value) = prefs.edit(commit = true) { putInt(TODAY_PENDING_WIDGET_ID, value) }

    /** Per-widget card size (height dp, width column span) survived across reboots; see [TodayWidgetResize]. */
    internal var todayWidgetSizes: Map<Int, TodayWidgetResize.Size>
        get() = TodayWidgetResize.decode(prefs.getString(TODAY_WIDGET_HEIGHTS, "").orEmpty())
        set(value) = prefs.edit(commit = true) { putString(TODAY_WIDGET_HEIGHTS, TodayWidgetResize.encode(value)) }

    var todayPanelOrder: List<String>
        get() = prefs.getString(TODAY_PANEL_ORDER, "").orEmpty().split(",").filter { it.isNotEmpty() }
        set(value) = prefs.edit { putString(TODAY_PANEL_ORDER, value.distinct().joinToString(",")) }

    var todayPinnedKeys: Set<String>
        get() = prefs.getStringSet(TODAY_PINNED_KEYS, emptySet()).orEmpty().toSet()
        set(value) = prefs.edit { putStringSet(TODAY_PINNED_KEYS, value.toSet()) }

    fun setTodayCardEnabled(key: String, enabled: Boolean) = prefs.edit {
        val canonical = TodayOrder.canonicalKey(key)
        putBoolean("$TODAY_CARD_PREFIX$canonical", enabled)
        // The merged card keeps the old week key in step, so both keys always agree.
        if (canonical == TodayOrder.canonicalKey(TodayOrder.LEGACY_WEEK_KEY)) {
            putBoolean("$TODAY_CARD_PREFIX${TodayOrder.LEGACY_WEEK_KEY}", enabled)
        }
        apply()
    }

    /** True when the merged Wellbeing card last showed the 7-day side. */
    var wellbeingShowWeek: Boolean
        get() = prefs.getBoolean(WELLBEING_SHOW_WEEK, false)
        set(value) = prefs.edit { putBoolean(WELLBEING_SHOW_WEEK, value).apply() }

    /** Automatic walks found today; reads 0 when the stored count is from another day. */
    var autoWalksToday: Int
        get() = WalkNudgePolicy.autoWalksForDay(
            prefs.getLong(NUDGE_AUTO_WALK_DAY, 0L),
            prefs.getInt(NUDGE_AUTO_WALK_COUNT, 0),
            epochDaySupplier(),
        )
        set(value) {
            // Close a finished day first so its snapshot still sees the old automatic count.
            checkAndResetNudgeDoneDay()
            prefs.edit {
                putInt(NUDGE_AUTO_WALK_COUNT, value)
                putLong(NUDGE_AUTO_WALK_DAY, epochDaySupplier())
            }
        }

    fun checkAndResetNudgeDoneDay() {
        val today = epochDaySupplier()
        val savedDay = prefs.getLong(NUDGE_DONE_DAY, 0L)
        if (savedDay == 0L) {
            prefs.edit { putLong(NUDGE_DONE_DAY, today) }
            return
        }
        if (savedDay != today) {
            val water = prefs.getInt(nudgeDoneKey(Nudges.WATER), 0)
            val walk = WalkNudgePolicy.shownWalks(
                prefs.getInt(nudgeDoneKey(Nudges.WALK), 0),
                WalkNudgePolicy.autoWalksForDay(
                    prefs.getLong(NUDGE_AUTO_WALK_DAY, 0L),
                    prefs.getInt(NUDGE_AUTO_WALK_COUNT, 0),
                    savedDay,
                ),
            )

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

    var drillLastType: String
        get() = prefs.getString(DRILL_LAST_TYPE, "prepositions") ?: "prepositions"
        set(value) = prefs.edit { putString(DRILL_LAST_TYPE, value).apply() }

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
