package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit

/** Settings group: Profiles (see [Prefs]). */
interface ProfilePrefs {
    /** Saved profiles as JSON (see [Profile.listFromJson]). Empty until profiles are used. */
    var profilesJson: String
}

internal class ProfilePrefsStore(private val prefs: SharedPreferences) : ProfilePrefs {
    private val PROFILES_JSON = "PROFILES_JSON"

    override var profilesJson: String
        get() = prefs.getString(PROFILES_JSON, "").orEmpty()
        set(value) = prefs.edit { putString(PROFILES_JSON, value).apply() }
}

/** Lets a profile write its settings into [Prefs]. */
class PrefsProfileTarget(private val prefs: Prefs) : ProfileSettingsTarget {
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
