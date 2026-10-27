package app.olauncher.ui.settings

import androidx.annotation.IdRes
import app.olauncher.R

/**
 * Rows that only make sense while a parent switch is on. Sections and search must not
 * show these while the switch is off (the populate* functions hide them).
 */
object SettingsRowAvailability {

    private val shortcutDependentRows = setOf(
        R.id.shortcutCount,
        R.id.shortcutSize,
        R.id.shortcutSide,
        R.id.shortcutHeight,
        R.id.shortcutIconStyle,
    )

    private val germanDependentRows = setOf(
        R.id.dictionaryApp,
        R.id.germanClock,
        R.id.germanOfflineDictionaryOnOff,
        R.id.germanLookupOnOff,
        R.id.germanSpellSuggestOnOff,
        R.id.germanPauseWordOnOff,
        R.id.germanUiOnOff,
        R.id.aiPracticeOnOff,
    )

    private val windDownDependentRows = setOf(
        R.id.windDownGreyOnOff,
        R.id.systemGreyOnOff,
    )

    private val holdDependentRows = setOf(
        R.id.quietReleaseTimes,
    )

    fun isAvailable(
        @IdRes viewId: Int,
        shortcutsOn: Boolean,
        germanOn: Boolean,
        windDownOn: Boolean = true,
        holdOn: Boolean = true,
    ): Boolean = when (viewId) {
        in shortcutDependentRows -> shortcutsOn
        in germanDependentRows -> germanOn
        in windDownDependentRows -> windDownOn
        in holdDependentRows -> holdOn
        else -> true
    }
}
