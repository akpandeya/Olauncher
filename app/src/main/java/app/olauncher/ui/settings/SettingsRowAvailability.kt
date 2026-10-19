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
        R.id.germanClock,
        R.id.germanLookupOnOff,
        R.id.germanPauseWordOnOff,
        R.id.germanUiOnOff,
    )

    fun isAvailable(@IdRes viewId: Int, shortcutsOn: Boolean, germanOn: Boolean): Boolean = when (viewId) {
        in shortcutDependentRows -> shortcutsOn
        in germanDependentRows -> germanOn
        else -> true
    }
}
