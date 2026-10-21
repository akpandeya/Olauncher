package app.olauncher.ui.privacy

import androidx.annotation.StringRes
import app.olauncher.R
import app.olauncher.helper.PrivacyInventory

/** Context-free mapping from [PrivacyInventory] keys to string resources and revoke targets. */
object PrivacyText {

    enum class RevokeTarget { NONE, APP_INFO, USAGE_ACCESS, HEALTH_CONNECT, THIS_SETTINGS }

    private val purposes: Map<String, Int> = mapOf(
        "privacy_purpose_haptics" to R.string.privacy_purpose_haptics,
        "privacy_purpose_wallpaper" to R.string.privacy_purpose_wallpaper,
        "privacy_purpose_status_bar" to R.string.privacy_purpose_status_bar,
        "privacy_purpose_app_list" to R.string.privacy_purpose_app_list,
        "privacy_purpose_uninstall" to R.string.privacy_purpose_uninstall,
        "privacy_purpose_alarm" to R.string.privacy_purpose_alarm,
        "privacy_purpose_usage_stats" to R.string.privacy_purpose_usage_stats,
        "privacy_purpose_private_space" to R.string.privacy_purpose_private_space,
        "privacy_purpose_nudges" to R.string.privacy_purpose_nudges,
        "privacy_purpose_network" to R.string.privacy_purpose_network,
        "privacy_purpose_weather_location" to R.string.privacy_purpose_weather_location,
        "privacy_purpose_calendar" to R.string.privacy_purpose_calendar,
        "privacy_purpose_contacts" to R.string.privacy_purpose_contacts,
        "privacy_purpose_health" to R.string.privacy_purpose_health,
        "privacy_purpose_system_grey" to R.string.privacy_purpose_system_grey,
        "privacy_purpose_weather_network" to R.string.privacy_purpose_weather_network,
        "privacy_purpose_dictionary_network" to R.string.privacy_purpose_dictionary_network,
        "privacy_purpose_rates_network" to R.string.privacy_purpose_rates_network,
        "privacy_purpose_artwork_network" to R.string.privacy_purpose_artwork_network,
    )

    @StringRes
    fun purposeRes(purposeKey: String): Int? = purposes[purposeKey]

    @StringRes
    fun stateRes(state: PrivacyInventory.GrantState): Int = when (state) {
        PrivacyInventory.GrantState.GRANTED -> R.string.privacy_state_granted
        PrivacyInventory.GrantState.NOT_GRANTED -> R.string.privacy_state_not_granted
        PrivacyInventory.GrantState.NOT_APPLICABLE -> R.string.privacy_state_not_applicable
    }

    fun revokeTarget(revokeKey: String): RevokeTarget = when (revokeKey) {
        PrivacyInventory.REVOKE_APP_INFO -> RevokeTarget.APP_INFO
        PrivacyInventory.REVOKE_USAGE_ACCESS -> RevokeTarget.USAGE_ACCESS
        PrivacyInventory.REVOKE_HEALTH_CONNECT -> RevokeTarget.HEALTH_CONNECT
        PrivacyInventory.REVOKE_FEATURE_OFF -> RevokeTarget.THIS_SETTINGS
        else -> RevokeTarget.NONE
    }

    @StringRes
    fun revokeLabelRes(target: RevokeTarget): Int? = when (target) {
        RevokeTarget.NONE -> null
        RevokeTarget.THIS_SETTINGS -> R.string.privacy_revoke_feature_off
        else -> R.string.privacy_revoke
    }
}
