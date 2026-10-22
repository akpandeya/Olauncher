package app.olauncher.ui

import android.view.View
import app.olauncher.data.Constants
import app.olauncher.helper.showAppActionPopup

/** Long-press popup for a home shortcut icon: name on top, then choose app / built-in / reset. */
internal fun HomeFragment.showShortcutPopup(anchor: View, slot: Int) {
    val context = context ?: return
    val isAppMode = prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP
    val name = ShortcutIconRenderer.getLabel(context, slot, prefs, isAppMode)
    anchor.showAppActionPopup(name, AppActionList.buildShortcut()) { action ->
        when (action.id) {
            AppAction.Id.CHANGE_APP -> showAppList(Constants.FLAG_SET_SHORTCUT_1 + slot - 1)
            AppAction.Id.CHOOSE_BUILTIN -> showBuiltinShortcutMenu(slot)
            AppAction.Id.RESET_SHORTCUT -> {
                prefs.resetShortcut(slot)
                populateShortcuts()
            }
            else -> {}
        }
    }
}
