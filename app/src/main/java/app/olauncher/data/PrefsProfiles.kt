package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Settings group: Profiles (see [Prefs]). */
interface ProfilePrefs {
    /** Saved profiles as JSON (see [Profile.listFromJson]). Empty until profiles are used. */
    var profilesJson: String

    /** Saved profile schedule as JSON (see [ProfileSchedule.fromJson]). Empty until the user edits the schedule. */
    var profileScheduleJson: String

    /** Id of the profile whose settings were last applied or passed over. Empty until a schedule runs. */
    var appliedProfileId: String

    /** Id of the profile the user switched to by hand. Empty when none. */
    var manualProfileId: String

    /** Epoch millis at which the manual switch ends; 0 when no switch is held. */
    var manualProfileUntil: Long

    /** Names of [ProfilePermission]s the user turned down at a profile switch. Asked again only on request. */
    var profilePermissionDeclined: Set<String>
}

internal class ProfilePrefsStore(private val prefs: SharedPreferences) : ProfilePrefs {
    private val PROFILES_JSON = "PROFILES_JSON"
    private val PROFILE_SCHEDULE_JSON = "PROFILE_SCHEDULE_JSON"
    private val APPLIED_PROFILE_ID = "APPLIED_PROFILE_ID"
    private val MANUAL_PROFILE_ID = "MANUAL_PROFILE_ID"
    private val MANUAL_PROFILE_UNTIL = "MANUAL_PROFILE_UNTIL"
    private val PROFILE_PERMISSION_DECLINED = "PROFILE_PERMISSION_DECLINED"

    override var profilesJson: String
        get() = prefs.getString(PROFILES_JSON, "").orEmpty()
        set(value) = prefs.edit { putString(PROFILES_JSON, value).apply() }

    override var profileScheduleJson: String
        get() = prefs.getString(PROFILE_SCHEDULE_JSON, "").orEmpty()
        set(value) {
            val changed = value != profileScheduleJson
            prefs.edit {
                putString(PROFILE_SCHEDULE_JSON, value)
                // New rules end a manual switch, so the schedule can show.
                if (changed) {
                    putString(MANUAL_PROFILE_ID, "")
                    putLong(MANUAL_PROFILE_UNTIL, 0L)
                }
            }
        }

    override var appliedProfileId: String
        get() = prefs.getString(APPLIED_PROFILE_ID, "").orEmpty()
        set(value) = prefs.edit { putString(APPLIED_PROFILE_ID, value) }

    override var manualProfileId: String
        get() = prefs.getString(MANUAL_PROFILE_ID, "").orEmpty()
        set(value) = prefs.edit { putString(MANUAL_PROFILE_ID, value) }

    override var manualProfileUntil: Long
        get() = prefs.getLong(MANUAL_PROFILE_UNTIL, 0L)
        set(value) = prefs.edit { putLong(MANUAL_PROFILE_UNTIL, value) }

    override var profilePermissionDeclined: Set<String>
        get() = prefs.getStringSet(PROFILE_PERMISSION_DECLINED, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit { putStringSet(PROFILE_PERMISSION_DECLINED, value) }
}

/** Lets a profile write its settings into [Prefs]. */
class PrefsProfileTarget(private val prefs: Prefs) : ProfileSettingsSource {
    override var homeAppsNum: Int by prefs::homeAppsNum
    override var dateTimeVisibility: Int by prefs::dateTimeVisibility
    override var weatherEnabled: Boolean by prefs::weatherEnabled
    override var homeNextEvent: Boolean by prefs::homeNextEvent
    override var homeContextLine: Boolean by prefs::homeContextLine
    override var homeMusicPulse: Boolean by prefs::homeMusicPulse
    override var homeGlowStrength: Int by prefs::homeGlowStrength
    override var homeWeatherEffects: Boolean by prefs::homeWeatherEffects
    override var homeShortcuts: Boolean by prefs::homeShortcuts
    override var shortcutCount: Int by prefs::shortcutCount
    override var intentionOnHome: Boolean by prefs::intentionOnHome
    override var pauseEnabled: Boolean by prefs::pauseEnabled
    override var pauseSeconds: Int by prefs::pauseSeconds
    override var pauseAllowanceMin: Int by prefs::pauseAllowanceMin
    override var pauseEscalate: Boolean by prefs::pauseEscalate
    override var quietEnabled: Boolean by prefs::quietEnabled
    override var windDown: Boolean by prefs::windDown
    override var windDownGrey: Boolean by prefs::windDownGrey
    override var nudgeWaterIntervalMin: Int by prefs::nudgeWaterIntervalMin
    override var nudgeWalkIntervalMin: Int by prefs::nudgeWalkIntervalMin
    override var todayOrderSmart: Boolean by prefs::todayOrderSmart
    override var drawerRecents: Boolean by prefs::drawerRecents
    override var drawerLetterStrip: Boolean by prefs::drawerLetterStrip
    override var searchContacts: Boolean by prefs::searchContacts

    override fun setTodayCardEnabled(key: String, enabled: Boolean) = prefs.setTodayCardEnabled(key, enabled)
    override fun isTodayCardEnabled(key: String): Boolean = prefs.isTodayCardEnabled(key)
}

/** The user's current home apps (active set, slots 1 to 8) as the start for built-in profiles. */
fun Prefs.currentHomeApps(): List<HomeApp> = (1..8).map { slot ->
    HomeApp(
        name = getAppName(slot),
        packageName = getAppPackage(slot),
        activityClassName = getAppActivityClassName(slot).ifBlank { null },
        user = getAppUser(slot),
        isShortcut = getIsShortcut(slot),
        shortcutId = getShortcutId(slot),
    )
}
