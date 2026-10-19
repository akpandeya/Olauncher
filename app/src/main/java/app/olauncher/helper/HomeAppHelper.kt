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
     * Writes [appModel] into home slot [position] (1..8).
     */
    fun setHomeApp(prefs: Prefs, position: Int, appModel: AppModel) {
        when (appModel) {
            is AppModel.App -> {
                when (position) {
                    1 -> {
                        prefs.appName1 = appModel.appLabel
                        prefs.appPackage1 = appModel.appPackage
                        prefs.appUser1 = appModel.user.toString()
                        prefs.appActivityClassName1 = appModel.activityClassName
                        prefs.isShortcut1 = false
                        prefs.shortcutId1 = ""
                    }
                    2 -> {
                        prefs.appName2 = appModel.appLabel
                        prefs.appPackage2 = appModel.appPackage
                        prefs.appUser2 = appModel.user.toString()
                        prefs.appActivityClassName2 = appModel.activityClassName
                        prefs.isShortcut2 = false
                        prefs.shortcutId2 = ""
                    }
                    3 -> {
                        prefs.appName3 = appModel.appLabel
                        prefs.appPackage3 = appModel.appPackage
                        prefs.appUser3 = appModel.user.toString()
                        prefs.appActivityClassName3 = appModel.activityClassName
                        prefs.isShortcut3 = false
                        prefs.shortcutId3 = ""
                    }
                    4 -> {
                        prefs.appName4 = appModel.appLabel
                        prefs.appPackage4 = appModel.appPackage
                        prefs.appUser4 = appModel.user.toString()
                        prefs.appActivityClassName4 = appModel.activityClassName
                        prefs.isShortcut4 = false
                        prefs.shortcutId4 = ""
                    }
                    5 -> {
                        prefs.appName5 = appModel.appLabel
                        prefs.appPackage5 = appModel.appPackage
                        prefs.appUser5 = appModel.user.toString()
                        prefs.appActivityClassName5 = appModel.activityClassName
                        prefs.isShortcut5 = false
                        prefs.shortcutId5 = ""
                    }
                    6 -> {
                        prefs.appName6 = appModel.appLabel
                        prefs.appPackage6 = appModel.appPackage
                        prefs.appUser6 = appModel.user.toString()
                        prefs.appActivityClassName6 = appModel.activityClassName
                        prefs.isShortcut6 = false
                        prefs.shortcutId6 = ""
                    }
                    7 -> {
                        prefs.appName7 = appModel.appLabel
                        prefs.appPackage7 = appModel.appPackage
                        prefs.appUser7 = appModel.user.toString()
                        prefs.appActivityClassName7 = appModel.activityClassName
                        prefs.isShortcut7 = false
                        prefs.shortcutId7 = ""
                    }
                    8 -> {
                        prefs.appName8 = appModel.appLabel
                        prefs.appPackage8 = appModel.appPackage
                        prefs.appUser8 = appModel.user.toString()
                        prefs.appActivityClassName8 = appModel.activityClassName
                        prefs.isShortcut8 = false
                        prefs.shortcutId8 = ""
                    }
                }
            }
            is AppModel.PinnedShortcut -> {
                when (position) {
                    1 -> {
                        prefs.appName1 = appModel.appLabel
                        prefs.appPackage1 = appModel.appPackage
                        prefs.appUser1 = appModel.user.toString()
                        prefs.appActivityClassName1 = null
                        prefs.isShortcut1 = true
                        prefs.shortcutId1 = appModel.shortcutId
                    }
                    2 -> {
                        prefs.appName2 = appModel.appLabel
                        prefs.appPackage2 = appModel.appPackage
                        prefs.appUser2 = appModel.user.toString()
                        prefs.appActivityClassName2 = null
                        prefs.isShortcut2 = true
                        prefs.shortcutId2 = appModel.shortcutId
                    }
                    3 -> {
                        prefs.appName3 = appModel.appLabel
                        prefs.appPackage3 = appModel.appPackage
                        prefs.appUser3 = appModel.user.toString()
                        prefs.appActivityClassName3 = null
                        prefs.isShortcut3 = true
                        prefs.shortcutId3 = appModel.shortcutId
                    }
                    4 -> {
                        prefs.appName4 = appModel.appLabel
                        prefs.appPackage4 = appModel.appPackage
                        prefs.appUser4 = appModel.user.toString()
                        prefs.appActivityClassName4 = null
                        prefs.isShortcut4 = true
                        prefs.shortcutId4 = appModel.shortcutId
                    }
                    5 -> {
                        prefs.appName5 = appModel.appLabel
                        prefs.appPackage5 = appModel.appPackage
                        prefs.appUser5 = appModel.user.toString()
                        prefs.appActivityClassName5 = null
                        prefs.isShortcut5 = true
                        prefs.shortcutId5 = appModel.shortcutId
                    }
                    6 -> {
                        prefs.appName6 = appModel.appLabel
                        prefs.appPackage6 = appModel.appPackage
                        prefs.appUser6 = appModel.user.toString()
                        prefs.appActivityClassName6 = null
                        prefs.isShortcut6 = true
                        prefs.shortcutId6 = appModel.shortcutId
                    }
                    7 -> {
                        prefs.appName7 = appModel.appLabel
                        prefs.appPackage7 = appModel.appPackage
                        prefs.appUser7 = appModel.user.toString()
                        prefs.appActivityClassName7 = null
                        prefs.isShortcut7 = true
                        prefs.shortcutId7 = appModel.shortcutId
                    }
                    8 -> {
                        prefs.appName8 = appModel.appLabel
                        prefs.appPackage8 = appModel.appPackage
                        prefs.appUser8 = appModel.user.toString()
                        prefs.appActivityClassName8 = null
                        prefs.isShortcut8 = true
                        prefs.shortcutId8 = appModel.shortcutId
                    }
                }
            }
            else -> {}
        }
    }
}
