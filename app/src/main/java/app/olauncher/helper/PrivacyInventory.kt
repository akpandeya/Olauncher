package app.olauncher.helper

/**
 * Everything Thumbline can touch, as data only: each declared permission and each outbound
 * network path. The UI maps the keys to strings and links later.
 */
object PrivacyInventory {

    enum class Kind { NORMAL, RUNTIME, SPECIAL_ACCESS, ADB_ONLY, NETWORK }

    enum class GrantState { GRANTED, NOT_GRANTED, NOT_APPLICABLE }

    /** [id] is the Android permission name, or a feature id for [Kind.NETWORK]. */
    data class Entry(
        val id: String,
        val purposeKey: String,
        val kind: Kind,
        val revokeKey: String,
    ) {
        val isPermission: Boolean get() = kind != Kind.NETWORK
        val isRuntime: Boolean get() = kind == Kind.RUNTIME
    }

    data class EntryState(val entry: Entry, val state: GrantState) {
        val isGranted: Boolean get() = state == GrantState.GRANTED
    }

    const val REVOKE_NONE = "none"
    const val REVOKE_APP_INFO = "settings_app_info"
    const val REVOKE_USAGE_ACCESS = "settings_usage_access"
    const val REVOKE_HEALTH_CONNECT = "settings_health_connect"
    const val REVOKE_ADB = "adb_revoke"
    const val REVOKE_FEATURE_OFF = "turn_off_feature"

    const val NET_WEATHER = "network_weather_open_meteo"
    const val NET_DICTIONARY = "network_wiktionary"
    const val NET_RATES = "network_frankfurter_rates"
    const val NET_ARTWORK = "network_now_playing_artwork"

    private fun normal(id: String, purpose: String) = Entry(id, purpose, Kind.NORMAL, REVOKE_NONE)

    private fun runtime(id: String, purpose: String, revoke: String = REVOKE_APP_INFO) =
        Entry(id, purpose, Kind.RUNTIME, revoke)

    private fun network(id: String, purpose: String) = Entry(id, purpose, Kind.NETWORK, REVOKE_FEATURE_OFF)

    val entries: List<Entry> = listOf(
        normal("android.permission.VIBRATE", "privacy_purpose_haptics"),
        normal("android.permission.SET_WALLPAPER", "privacy_purpose_wallpaper"),
        normal("android.permission.EXPAND_STATUS_BAR", "privacy_purpose_status_bar"),
        normal("android.permission.QUERY_ALL_PACKAGES", "privacy_purpose_app_list"),
        normal("android.permission.REQUEST_DELETE_PACKAGES", "privacy_purpose_uninstall"),
        normal("com.android.alarm.permission.SET_ALARM", "privacy_purpose_alarm"),
        Entry("android.permission.PACKAGE_USAGE_STATS", "privacy_purpose_usage_stats", Kind.SPECIAL_ACCESS, REVOKE_USAGE_ACCESS),
        normal("android.permission.ACCESS_HIDDEN_PROFILES", "privacy_purpose_private_space"),
        runtime("android.permission.POST_NOTIFICATIONS", "privacy_purpose_nudges"),
        normal("android.permission.INTERNET", "privacy_purpose_network"),
        runtime("android.permission.ACCESS_COARSE_LOCATION", "privacy_purpose_weather_location"),
        runtime("android.permission.READ_CALENDAR", "privacy_purpose_calendar"),
        runtime("android.permission.READ_CONTACTS", "privacy_purpose_contacts"),
        runtime("android.permission.health.READ_STEPS", "privacy_purpose_health", REVOKE_HEALTH_CONNECT),
        runtime("android.permission.health.READ_SLEEP", "privacy_purpose_health", REVOKE_HEALTH_CONNECT),
        runtime("android.permission.health.READ_RESTING_HEART_RATE", "privacy_purpose_health", REVOKE_HEALTH_CONNECT),
        runtime("android.permission.health.READ_HEART_RATE", "privacy_purpose_health", REVOKE_HEALTH_CONNECT),
        runtime("android.permission.health.READ_EXERCISE", "privacy_purpose_health", REVOKE_HEALTH_CONNECT),
        Entry("android.permission.WRITE_SECURE_SETTINGS", "privacy_purpose_system_grey", Kind.ADB_ONLY, REVOKE_ADB),
        network(NET_WEATHER, "privacy_purpose_weather_network"),
        network(NET_DICTIONARY, "privacy_purpose_dictionary_network"),
        network(NET_RATES, "privacy_purpose_rates_network"),
        network(NET_ARTWORK, "privacy_purpose_artwork_network"),
    )

    /**
     * Install-time (normal) permissions are always granted. Network entries have no grant
     * state. Everything else is granted only if its name is in [grantedPermissions].
     */
    fun states(grantedPermissions: Set<String>): List<EntryState> = entries.map { entry ->
        val state = when (entry.kind) {
            Kind.NETWORK -> GrantState.NOT_APPLICABLE
            Kind.NORMAL -> GrantState.GRANTED
            else -> if (entry.id in grantedPermissions) GrantState.GRANTED else GrantState.NOT_GRANTED
        }
        EntryState(entry, state)
    }
}
