package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.data.Prefs

object HomeAppHelper {

    const val MAX_HOME_APPS = 8

    sealed class AddResult {
        object Added : AddResult()
        object AlreadyOnHome : AddResult()
        object HomeFull : AddResult()
    }

    /**
     * Checks if [appModel] is currently placed on one of the active home slots (1..homeAppsNum).
     */
    fun isAppOnHome(prefs: Prefs, appModel: AppModel): Boolean {
        if (appModel !is AppModel.App && appModel !is AppModel.PinnedShortcut) return false
        val pkg = appModel.appPackage
        val user = appModel.user.toString()
        val num = prefs.homeAppsNum.coerceIn(0, MAX_HOME_APPS)
        for (i in 1..num) {
            if (prefs.getAppPackage(i) == pkg && prefs.getAppUser(i) == user) {
                if (appModel is AppModel.PinnedShortcut) {
                    if (prefs.getIsShortcut(i) && prefs.getShortcutId(i) == appModel.shortcutId) return true
                } else {
                    if (!prefs.getIsShortcut(i)) return true
                }
            }
        }
        return false
    }

    /**
     * Finds the target slot to add an app to, or null if full.
     * Searches for the first empty slot in 1..homeAppsNum. If none and homeAppsNum < 8,
     * expands homeAppsNum by 1 and returns that new slot.
     */
    fun findTargetSlot(prefs: Prefs): Int? {
        val currentNum = prefs.homeAppsNum.coerceIn(0, MAX_HOME_APPS)
        for (i in 1..currentNum) {
            if (prefs.getAppPackage(i).isBlank()) {
                return i
            }
        }
        if (currentNum < MAX_HOME_APPS) {
            return currentNum + 1
        }
        return null
    }

    /**
     * Adds [appModel] to the home screen.
     * Returns [AddResult.AlreadyOnHome] if already present, [AddResult.HomeFull] if full or archived,
     * or [AddResult.Added] on success.
     */
    fun addToHome(prefs: Prefs, appModel: AppModel): AddResult {
        if (appModel.isArchived) return AddResult.HomeFull
        if (appModel !is AppModel.App && appModel !is AppModel.PinnedShortcut) return AddResult.HomeFull

        if (isAppOnHome(prefs, appModel)) {
            return AddResult.AlreadyOnHome
        }

        val targetSlot = findTargetSlot(prefs) ?: return AddResult.HomeFull
        if (targetSlot > prefs.homeAppsNum) {
            prefs.homeAppsNum = targetSlot
        }
        setHomeApp(prefs, targetSlot, appModel)
        return AddResult.Added
    }

    /**
     * Writes [appModel] into home slot [position] (1..8) of [set] (the active set by default).
     */
    fun setHomeApp(prefs: Prefs, position: Int, appModel: AppModel, set: HomeSet = prefs.activeHomeSet()) {
        when (appModel) {
            is AppModel.App -> prefs.setHomeSlot(
                set, position, appModel.appLabel, appModel.appPackage, appModel.user.toString(),
                appModel.activityClassName, isShortcut = false, shortcutId = "",
            )
            is AppModel.PinnedShortcut -> prefs.setHomeSlot(
                set, position, appModel.appLabel, appModel.appPackage, appModel.user.toString(),
                null, isShortcut = true, shortcutId = appModel.shortcutId,
            )
            else -> {}
        }
    }
}
