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

    fun pause(): SettingsDeepLink = PAUSE

    fun forPause(): SettingsDeepLink = PAUSE

    fun pauseBundle(): Bundle = PAUSE.toBundle()

    fun createBundle(target: SettingsDeepLink): Bundle = target.toBundle()
}
