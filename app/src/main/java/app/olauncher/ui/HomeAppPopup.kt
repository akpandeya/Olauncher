package app.olauncher.ui

import android.view.View
import app.olauncher.R
import app.olauncher.helper.AppActionHandlers
import app.olauncher.helper.HiddenChange
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSets
import app.olauncher.helper.HomeSlotRemoval
import app.olauncher.helper.HomeSlotSnapshot
import app.olauncher.helper.UndoMessage
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.showAppActionPopup

/** Long-press popup for a home app: "Change app" first, then the shared app actions. */
internal fun HomeFragment.showHomeAppPopup(anchor: View, slot: Int) {
    val set = prefs.activeHomeSet()
    val flag = HomeSets.flagFor(set, slot)
    val appName = prefs.getAppName(slot, set)
    if (appName.isEmpty()) {
        showAppList(flag, false, true)
        return
    }
    val appPackage = prefs.getAppPackage(slot, set)
    val userString = prefs.getAppUser(slot, set)
    val hiddenKey = "$appPackage|$userString"
    val actions = AppActionList.buildHome(
        isShortcut = prefs.getIsShortcut(slot, set),
        isHidden = prefs.hiddenApps.contains(hiddenKey),
        isDistracting = prefs.distractingApps.contains(appPackage),
    )
    anchor.showAppActionPopup(appName, actions) { action ->
        val context = requireContext()
        val user = getUserHandleFromString(context, userString)
        when (action.id) {
            AppAction.Id.CHANGE_APP -> showAppList(flag, false, true)
            AppAction.Id.ADD_TO_HOME, AppAction.Id.CHOOSE_BUILTIN, AppAction.Id.RESET_SHORTCUT -> {}
            AppAction.Id.RENAME -> showAppList(flag, true, true)
            AppAction.Id.REMOVE_FROM_HOME -> {
                val removal = HomeSlotRemoval(
                    HomeSlotSnapshot(
                        name = appName,
                        packageName = appPackage,
                        user = userString,
                        activityClassName = prefs.getAppActivityClassName(slot, set),
                        isShortcut = prefs.getIsShortcut(slot, set),
                        shortcutId = prefs.getShortcutId(slot, set),
                    )
                )
                writeSlot(set, slot, removal.apply())
                viewModel.refreshHome(false)
                UndoMessage.show(activity, getString(R.string.removed_from_home)) {
                    val current = HomeSlotSnapshot(
                        name = prefs.getAppName(slot, set),
                        packageName = prefs.getAppPackage(slot, set),
                        user = prefs.getAppUser(slot, set),
                        activityClassName = prefs.getAppActivityClassName(slot, set),
                        isShortcut = prefs.getIsShortcut(slot, set),
                        shortcutId = prefs.getShortcutId(slot, set),
                    )
                    writeSlot(set, slot, removal.undo(current))
                    viewModel.refreshHome(false)
                }
            }
            AppAction.Id.HIDE -> {
                val change = HiddenChange.toggle(prefs.hiddenApps, hiddenKey)
                prefs.hiddenApps = change.apply(prefs.hiddenApps).toMutableSet()
                viewModel.getAppList()
                viewModel.getHiddenApps()
                UndoMessage.show(activity, getString(if (change.wasHidden) R.string.app_unhidden else R.string.app_hidden)) {
                    prefs.hiddenApps = change.undo(prefs.hiddenApps).toMutableSet()
                    viewModel.getAppList()
                    viewModel.getHiddenApps()
                }
            }
            AppAction.Id.DISTRACTING -> AppActionHandlers.toggleDistracting(context, prefs, appPackage)
            AppAction.Id.APP_INFO -> openAppInfo(context, user, appPackage)
            AppAction.Id.UNINSTALL -> AppActionHandlers.uninstallApp(context, appPackage, user)
        }
    }
}

private fun HomeFragment.writeSlot(set: HomeSet, slot: Int, snapshot: HomeSlotSnapshot) {
    prefs.setHomeSlot(
        set, slot, snapshot.name, snapshot.packageName, snapshot.user,
        snapshot.activityClassName, snapshot.isShortcut, snapshot.shortcutId,
    )
}
