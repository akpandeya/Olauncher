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
     * Writes [settings] to [target]. Weather needs the location permission, so unless
     * [weatherGranted] the two weather settings keep the values they had; the rest is written.
     */
    fun applyBundle(settings: ProfileSettings, target: ProfileSettingsTarget, weatherGranted: Boolean) {
        val weather = target.weatherEnabled
        val effects = target.homeWeatherEffects
        settings.apply(target)
        if (!weatherGranted && settings.showWeather) {
            target.weatherEnabled = weather
            target.homeWeatherEffects = effects
        }
    }

    /**
     * Writes the bundle of the chosen profile in [profiles] to [target], see [applyBundle].
     * Returns false and writes nothing for "Keep defaults" or a profile that is not in [profiles].
     */
    fun apply(
        option: ProfilePickerOption,
        profiles: List<Profile>,
        target: ProfileSettingsTarget,
        weatherGranted: Boolean = true,
    ): Boolean {
        val id = profileIdToApply(option) ?: return false
        val profile = profiles.firstOrNull { it.id == id } ?: return false
        applyBundle(profile.settings, target, weatherGranted)
        return true
    }
}
