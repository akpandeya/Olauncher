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

    /**
     * Writes exactly the bundle of the chosen profile in [profiles] to [target].
     * Returns false and writes nothing for "Keep defaults" or a profile that is not in [profiles].
     */
    fun apply(option: ProfilePickerOption, profiles: List<Profile>, target: ProfileSettingsTarget): Boolean {
        val id = profileIdToApply(option) ?: return false
        val profile = profiles.firstOrNull { it.id == id } ?: return false
        profile.settings.apply(target)
        return true
    }
}
