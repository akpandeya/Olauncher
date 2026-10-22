package app.olauncher.ui

import android.view.View
import app.olauncher.R
import app.olauncher.helper.AppActionHandlers
import app.olauncher.helper.HomeSets
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
    val changeApp = AppAction(AppAction.Id.CHANGE_APP, R.string.change_app)
    val actions = if (prefs.getIsShortcut(slot, set)) {
        listOf(changeApp)
    } else {
        listOf(changeApp) + AppActionList.build(
            isArchived = false,
            isHidden = prefs.hiddenApps.contains(hiddenKey),
            isDistracting = prefs.distractingApps.contains(appPackage),
        ).filter { it.id != AppAction.Id.ADD_TO_HOME } // already on home
    }
    anchor.showAppActionPopup(appName, actions) { action ->
        val context = requireContext()
        val user = getUserHandleFromString(context, userString)
        when (action.id) {
            AppAction.Id.CHANGE_APP -> showAppList(flag, false, true)
            AppAction.Id.ADD_TO_HOME -> {}
            AppAction.Id.RENAME -> showAppList(flag, true, true)
            AppAction.Id.HIDE -> {
                prefs.hiddenApps = prefs.hiddenApps.toMutableSet().apply {
                    if (!remove(hiddenKey)) add(hiddenKey)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            }
            AppAction.Id.DISTRACTING -> AppActionHandlers.toggleDistracting(context, prefs, appPackage)
            AppAction.Id.APP_INFO -> openAppInfo(context, user, appPackage)
            AppAction.Id.UNINSTALL -> AppActionHandlers.uninstallApp(context, appPackage, user)
        }
    }
}
