package app.olauncher.ui.settings

import android.os.Bundle
import androidx.annotation.IdRes
import app.olauncher.R
import app.olauncher.ui.SettingsFragment

/**
 * Named target for a deep link pointing into a specific section and row of Settings.
 */
data class SettingsDeepLink(
    val sectionKey: String,
    @IdRes val rowViewId: Int,
) {
    /**
     * Builds the argument bundle to pass when navigating to [R.id.settingsFragment].
     */
    fun toBundle(): Bundle = Bundle().apply {
        putInt(SettingsFragment.ARG_ROW, rowViewId)
    }
}

typealias SettingsTarget = SettingsDeepLink

object SettingsDeepLinks {

    val PAUSE = SettingsDeepLink(
        sectionKey = SettingsSections.KEY_WELLBEING,
        rowViewId = R.id.pauseBeforeAppsOnOff,
    )

    /** The German web-app account row (#835): where a lapsed card's tap reconnects. */
    val GERMAN_WEB_ACCOUNT = SettingsDeepLink(
        sectionKey = SettingsSections.KEY_DEUTSCH,
        rowViewId = R.id.germanWebAccount,
    )

    fun pause(): SettingsDeepLink = PAUSE

    fun forPause(): SettingsDeepLink = PAUSE

    fun pauseBundle(): Bundle = PAUSE.toBundle()

    fun germanWebAccount(): SettingsDeepLink = GERMAN_WEB_ACCOUNT

    fun germanWebAccountBundle(): Bundle = GERMAN_WEB_ACCOUNT.toBundle()

    fun createBundle(target: SettingsDeepLink): Bundle = target.toBundle()
}
