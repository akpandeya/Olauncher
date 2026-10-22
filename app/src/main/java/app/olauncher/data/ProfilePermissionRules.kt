package app.olauncher.data

/** The Android permissions a profile feature can need. Declared in the order they are asked. */
enum class ProfilePermission { LOCATION, CONTACTS, CALENDAR, NOTIFICATION_ACCESS }

/** A profile feature that stays off until its [permission] is granted. */
enum class ProfileFeature(val permission: ProfilePermission) {
    WEATHER(ProfilePermission.LOCATION),
    CONTACTS_SEARCH(ProfilePermission.CONTACTS),
    NEXT_EVENT(ProfilePermission.CALENDAR),
    MUSIC_LINE(ProfilePermission.NOTIFICATION_ACCESS),
    MUSIC_PULSE(ProfilePermission.NOTIFICATION_ACCESS),
    QUIET(ProfilePermission.NOTIFICATION_ACCESS),
}

/** Context-free rules for offering the permissions a profile needs. Same rule as [ProfilePicker.applyBundle]. */
object ProfilePermissionRules {
    fun isGranted(permission: ProfilePermission, granted: ProfilePermissions): Boolean = when (permission) {
        ProfilePermission.LOCATION -> granted.weather
        ProfilePermission.CONTACTS -> granted.contacts
        ProfilePermission.CALENDAR -> granted.calendar
        ProfilePermission.NOTIFICATION_ACCESS -> granted.notificationAccess
    }

    /** Features [settings] switches on whose permission is not in [granted], in enum order. */
    fun missing(settings: ProfileSettings, granted: ProfilePermissions): List<ProfileFeature> =
        ProfileFeature.values().filter { feature ->
            val wanted = when (feature) {
                ProfileFeature.WEATHER -> settings.showWeather
                ProfileFeature.CONTACTS_SEARCH -> settings.drawerContacts
                ProfileFeature.NEXT_EVENT -> settings.nextEvent
                ProfileFeature.MUSIC_LINE -> settings.musicLine
                ProfileFeature.MUSIC_PULSE -> settings.musicPulse
                ProfileFeature.QUIET -> settings.quietNotifications
            }
            wanted && !isGranted(feature.permission, granted)
        }

    /** Permissions to offer: those of [missing] not in [declined], once each, in enum order. */
    fun toAsk(missing: List<ProfileFeature>, declined: Set<ProfilePermission>): List<ProfilePermission> =
        missing.map { it.permission }.distinct().filter { it !in declined }.sorted()

    /** Reads declined permission names saved in prefs; unknown names are ignored. */
    fun parseDeclined(names: Set<String>): Set<ProfilePermission> =
        names.mapNotNull { name -> ProfilePermission.values().firstOrNull { it.name == name } }.toSet()

    fun declinedNames(declined: Set<ProfilePermission>): Set<String> = declined.map { it.name }.toSet()

    /** The features to list in the dialog: those of [missing] whose permission is in [ask]. */
    fun featuresFor(missing: List<ProfileFeature>, ask: List<ProfilePermission>): List<ProfileFeature> =
        missing.filter { it.permission in ask }
}
