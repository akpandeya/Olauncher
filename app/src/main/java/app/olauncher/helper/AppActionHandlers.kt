package app.olauncher.helper

import android.content.Context
import android.os.Process
import android.os.UserHandle
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.wellbeing.UsageSnapshotWorker

/** App action handlers shared by the drawer and the home long-press popup. */
object AppActionHandlers {

    fun toggleDistracting(context: Context, prefs: Prefs, appPackage: String) {
        if (appPackage.isBlank()) return
        val current = prefs.distractingApps.toMutableSet()
        if (current.contains(appPackage)) current.remove(appPackage) else current.add(appPackage)
        prefs.distractingApps = current
        UsageSnapshotWorker.syncWithDistractingApps(context.applicationContext, current)
    }

    fun uninstallApp(context: Context, appPackage: String, user: UserHandle) {
        if (user != Process.myUserHandle()) {
            openAppInfo(context, user, appPackage)
        } else if (context.isSystemApp(appPackage, user)) {
            context.showToast(context.getString(R.string.system_app_cannot_delete))
            openAppInfo(context, user, appPackage)
        } else {
            context.uninstall(appPackage)
        }
    }
}
