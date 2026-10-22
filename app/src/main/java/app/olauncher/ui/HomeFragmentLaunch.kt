package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.view.View
import android.widget.TextView
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.helper.Haptics
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import android.util.Log

private const val TAG = "HomeFragmentLaunch"

internal fun HomeFragment.setHomeAppText(
    textView: TextView,
    appName: String,
    packageName: String,
    userString: String,
    isShortcut: Boolean,
    shortcutId: String?,
): Boolean {
    // Get user handle for the app/shortcut
    val userHandle = getUserHandleFromString(requireContext(), userString)

    // If it's a shortcut, verify it still exists
    if (isShortcut) {
        val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        // Query for the specific shortcut
        val query = LauncherApps.ShortcutQuery().apply {
            setPackage(packageName)
            setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        }

        try {
            val shortcuts = launcherApps.getShortcuts(query, userHandle)
            // Check if our shortcut still exists
            if (shortcuts?.any { it.id == shortcutId } == true) {
                textView.text = appName
                return true
            }
            textView.text = ""
            return false
        } catch (e: Exception) {
            Log.w(TAG, "setHomeAppText failed", e)
            textView.text = ""
            return false
        }
    }

    // Regular app check
    if (isPackageInstalled(requireContext(), packageName, userString)) {
        textView.text = appName
        return true
    }
    textView.text = ""
    return false
}

internal fun HomeFragment.launchAppOrShortcut(
    appName: String,
    packageName: String,
    activityClassName: String?,
    shortcutId: String?,
    isShortcut: Boolean,
    userString: String,
    fallback: (() -> Unit)? = null,
) {
    if (appName.isEmpty()) {
        showLongPressToast()
        return
    }
    if (isShortcut && !shortcutId.isNullOrEmpty()) {
        launchShortcut(
            packageName = packageName,
            shortcutId = shortcutId,
            shortcutLabel = appName,
            userString = userString
        )
    } else if (packageName.isNotEmpty()) {
        launchApp(
            appName = appName,
            packageName = packageName,
            activityClassName = activityClassName,
            userString = userString
        )
    } else {
        fallback?.invoke()
    }
}

internal fun HomeFragment.launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
    viewModel.selectedApp(
        AppModel.PinnedShortcut(
            shortcutId = shortcutId,
            appLabel = shortcutLabel,
            user = getUserHandleFromString(requireContext(), userString),
            key = null,
            appPackage = packageName,
            isNew = false,
        ),
        Constants.FLAG_LAUNCH_APP
    )
}

internal fun HomeFragment.launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
    val userHandle = getUserHandleFromString(requireContext(), userString)
    val launcher = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val isArchived = launcher.getActivityList(packageName, userHandle).firstOrNull()?.applicationInfo?.isArchived == true
    viewModel.selectedApp(
        AppModel.App(
            appLabel = appName,
            key = null,
            appPackage = packageName,
            activityClassName = activityClassName,
            isNew = false,
            user = userHandle,
            isArchived = isArchived,
        ),
        Constants.FLAG_LAUNCH_APP
    )
}

internal fun HomeFragment.homeAppClicked(location: Int, view: View? = null) {
    val appName = prefs.getAppName(location)
    if (appName.isNotEmpty()) {
        Haptics.confirm(view ?: binding.root)
    }
    launchAppOrShortcut(
        appName = appName,
        packageName = prefs.getAppPackage(location),
        activityClassName = prefs.getAppActivityClassName(location),
        shortcutId = prefs.getShortcutId(location),
        isShortcut = prefs.getIsShortcut(location),
        userString = prefs.getAppUser(location)
    )
}
