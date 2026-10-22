package app.olauncher.ui.settings

import android.view.Menu
import android.view.View
import android.widget.TextView
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.helper.GestureAction
import app.olauncher.helper.GestureActions
import app.olauncher.helper.GestureSlot
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.SettingsFragment

private fun SettingsFragment.gestureValueView(slot: GestureSlot): TextView = when (slot) {
    GestureSlot.DOUBLE_TAP -> binding.doubleTapAction
    GestureSlot.CLOCK_TAP -> binding.clockTapAction
    GestureSlot.DATE_TAP -> binding.dateTapAction
}

private fun gestureAppFlag(slot: GestureSlot): Int = when (slot) {
    GestureSlot.DOUBLE_TAP -> Constants.FLAG_SET_GESTURE_APP_DOUBLE_TAP
    GestureSlot.CLOCK_TAP -> Constants.FLAG_SET_GESTURE_APP_CLOCK_TAP
    GestureSlot.DATE_TAP -> Constants.FLAG_SET_GESTURE_APP_DATE_TAP
}

private fun gestureActionTitle(action: GestureAction): Int = when (action) {
    GestureAction.DEFAULT -> R.string.gesture_action_default
    GestureAction.LOCK -> R.string.gesture_action_lock
    GestureAction.TORCH -> R.string.gesture_action_torch
    GestureAction.NEXT_TRACK -> R.string.gesture_action_next_track
    GestureAction.PREVIOUS_TRACK -> R.string.gesture_action_previous_track
    GestureAction.OPEN_APP -> R.string.gesture_action_open_app
    GestureAction.NOTHING -> R.string.gesture_action_nothing
}

internal fun SettingsFragment.populateGestureActions() {
    for (slot in GestureSlot.values()) {
        val action = prefs.gestureAction(slot)
        val app = prefs.gestureApp(slot)
        gestureValueView(slot).text =
            if (action == GestureAction.OPEN_APP && app != null) app.label else getString(gestureActionTitle(action))
    }
}

/** A tap opens this menu with every action the gesture can do. */
internal fun SettingsFragment.showGestureActionMenu(slot: GestureSlot) {
    val options = GestureActions.options(slot)
    gestureValueView(slot).showPopupMenu(
        configure = { menu ->
            options.forEachIndexed { i, option -> menu.add(Menu.NONE, i, i, gestureActionTitle(option)) }
        }
    ) { item -> chooseGestureAction(slot, options[item.itemId]) }
}

private fun SettingsFragment.chooseGestureAction(slot: GestureSlot, action: GestureAction) {
    // Locking needs the accessibility service: explain and send the user to turn it on first.
    if (GestureActions.needsAccessibilityService(action) && !isAccessServiceEnabled(requireContext())) {
        showAccessibilityDialog()
        return
    }
    if (action == GestureAction.OPEN_APP) {
        // The action switches on once an app is picked; backing out changes nothing.
        showAppListIfEnabled(gestureAppFlag(slot))
        return
    }
    prefs.setGestureAction(slot, action)
    if (action == GestureAction.LOCK) prefs.lockModeOn = true
    populateGestureActions()
    populateLockSettings()
}
