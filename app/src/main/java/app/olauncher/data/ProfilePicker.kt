package app.olauncher.data

/** One row of the first-run profile step: a built-in profile, or "Keep defaults" ([profileId] null). */
enum class ProfilePickerOption(val profileId: String?) {
    BARE(BuiltInProfiles.BARE_ID),
    FOCUS(BuiltInProfiles.FOCUS_ID),
    RICH(BuiltInProfiles.RICH_ID),
    KEEP_DEFAULTS(null),
}

/** Context-free rules for the first-run profile step. */
object ProfilePicker {
    /** What the step opens on: nothing changes unless the user picks a profile. */
    val initial: ProfilePickerOption = ProfilePickerOption.KEEP_DEFAULTS

    /** The profile to apply for [option]; null for "Keep defaults" (and so for Skip). */
    fun profileIdToApply(option: ProfilePickerOption): String? = option.profileId

    /** The profile's weather-effects value, to apply once the location permission is granted. */
    fun weatherEffectsOf(option: ProfilePickerOption, profiles: List<Profile>): Boolean {
        val id = profileIdToApply(option) ?: return false
        return profiles.firstOrNull { it.id == id }?.settings?.weatherEffects == true
    }

    /** True when the chosen profile's bundle would switch weather on. */
    fun wantsWeather(option: ProfilePickerOption, profiles: List<Profile>): Boolean {
        val id = profileIdToApply(option) ?: return false
        return profiles.firstOrNull { it.id == id }?.settings?.showWeather == true
    }

    /**
     * Writes [settings] to [target]. A feature whose permission is missing in [permissions] and that
     * the bundle would turn on keeps the value it had; the rest is written. Nothing is asked.
     */
    fun applyBundle(settings: ProfileSettings, target: ProfileSettingsTarget, permissions: ProfilePermissions) {
        val weather = target.weatherEnabled
        val effects = target.homeWeatherEffects
        val nextEvent = target.homeNextEvent
        val musicLine = target.homeContextLine
        val musicPulse = target.homeMusicPulse
        val quiet = target.quietEnabled
        val contacts = target.searchContacts
        settings.apply(target)
        if (!permissions.weather && settings.showWeather) {
            target.weatherEnabled = weather
            target.homeWeatherEffects = effects
        }
        if (!permissions.calendar && settings.nextEvent) target.homeNextEvent = nextEvent
        if (!permissions.notificationAccess) {
            if (settings.musicLine) target.homeContextLine = musicLine
            if (settings.musicPulse) target.homeMusicPulse = musicPulse
            if (settings.quietNotifications) target.quietEnabled = quiet
        }
        if (!permissions.contacts && settings.drawerContacts) target.searchContacts = contacts
    }

    fun applyBundle(settings: ProfileSettings, target: ProfileSettingsTarget, weatherGranted: Boolean) =
        applyBundle(settings, target, ProfilePermissions.ALL.copy(weather = weatherGranted))

    /**
     * Writes the bundle of the chosen profile in [profiles] to [target], see [applyBundle].
     * Returns false and writes nothing for "Keep defaults" or a profile that is not in [profiles].
     */
    fun apply(
        option: ProfilePickerOption,
        profiles: List<Profile>,
        target: ProfileSettingsTarget,
        weatherGranted: Boolean = true,
        permissions: ProfilePermissions = ProfilePermissions.ALL.copy(weather = weatherGranted),
    ): Boolean {
        val id = profileIdToApply(option) ?: return false
        val profile = profiles.firstOrNull { it.id == id } ?: return false
        applyBundle(profile.settings, target, permissions)
        return true
    }
}

/** Which Android permissions the features of a profile need: location, calendar, notification access, contacts. */
data class ProfilePermissions(
    val weather: Boolean,
    val calendar: Boolean,
    val notificationAccess: Boolean,
    val contacts: Boolean,
) {
    companion object {
        val ALL = ProfilePermissions(weather = true, calendar = true, notificationAccess = true, contacts = true)
    }
}
