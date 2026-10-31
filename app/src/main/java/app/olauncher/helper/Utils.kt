package app.olauncher.helper

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.UserHandle
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import app.olauncher.BuildConfig
import app.olauncher.R
import app.olauncher.data.Accent
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.shortcutIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import kotlin.math.pow
import kotlin.math.sqrt

private const val TAG = "Utils"

fun Context.showToast(message: String?, duration: Int = Toast.LENGTH_SHORT) {
    if (message.isNullOrBlank()) return
    Toast.makeText(this, message, duration).show()
}

fun Context.showToast(stringResource: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, getString(stringResource), duration).show()
}

suspend fun getAppsList(
    context: Context,
    prefs: Prefs,
    includeRegularApps: Boolean = true,
    includeHiddenApps: Boolean = false,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()

        try {
            if (!prefs.hiddenAppsUpdated) upgradeHiddenApps(prefs)
            val hiddenApps = prefs.hiddenApps

            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (profile in userManager.userProfiles) {
                if (isPrivateSpaceProfile(context, profile)) continue
                for (app in launcherApps.getActivityList(null, profile)) {
                    val appLabelShown = AppListBuilder.displayLabel(
                        prefs.getAppRenameLabel(app.applicationInfo.packageName),
                        app.label.toString(),
                    )
                    val isArchived = app.applicationInfo.isArchived
                    val appModel = AppModel.App(
                        appLabel = appLabelShown,
                        key = collator.getCollationKey(app.label.toString()),
                        appPackage = app.applicationInfo.packageName,
                        activityClassName = app.componentName.className,
                        isNew = !isArchived && (System.currentTimeMillis() - app.firstInstallTime) < Constants.NEW_APP_WINDOW_MILLIS,
                        user = profile,
                        firstInstallTime = app.firstInstallTime,
                        isArchived = isArchived,
                    )

                    if (AppListBuilder.shouldInclude(
                            packageName = app.applicationInfo.packageName,
                            userString = profile.toString(),
                            ownPackage = BuildConfig.APPLICATION_ID,
                            hiddenApps = hiddenApps,
                            includeRegularApps = includeRegularApps,
                            includeHiddenApps = includeHiddenApps,
                        )
                    ) {
                        appList.add(appModel)
                    }
                }
            }

            // Add shortcuts if we're getting regular apps
            if (includeRegularApps) {
                val pinned = try {
                    getPinnedShortcuts(context, prefs, collator)
                } catch (e: Exception) {
                    Log.w("Utils", "Failed to load pinned shortcuts", e)
                    emptyList()
                }
                appList.addAll(pinned)
            }

            AppListBuilder.sortByLabel(appList, collator) { it.appLabel }
        } catch (e: Exception) {
            Log.w(TAG, "getAppsList failed", e)
        }
        appList
    }
}

private suspend fun getPinnedShortcuts(
    context: Context,
    prefs: Prefs,
    collator: Collator,
): List<AppModel.PinnedShortcut> =
    withContext(Dispatchers.IO) {
        val pinnedShortcuts = mutableListOf<AppModel.PinnedShortcut>()
        val shortcuts = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
        if (shortcuts?.hasShortcutHostPermission() == true) {
            val query = LauncherApps.ShortcutQuery().apply {
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }
            shortcuts.profiles.forEach { profile ->
                if (isPrivateSpaceProfile(context, profile)) return@forEach
                try {
                    shortcuts.getShortcuts(query, profile)?.forEach { shortcut ->
                        val identity = shortcutIdentity(
                            shortcut.`package`,
                            shortcut.id,
                            profile.toString()
                        )
                        if (shortcut.isPinned && pinnedShortcuts.none { it.identity == identity }) {
                            val label = prefs.getAppRenameLabel(identity)
                                .ifBlank { prefs.getAppRenameLabel(shortcut.id) }
                                .takeIf { it.isNotBlank() }
                                ?: shortcut.shortLabel?.toString()
                                ?: shortcut.longLabel?.toString().orEmpty()
                            pinnedShortcuts.add(
                                AppModel.PinnedShortcut(
                                    appLabel = label,
                                    key = collator.getCollationKey(label),
                                    appPackage = shortcut.`package`,
                                    shortcutId = shortcut.id,
                                    isNew = false,
                                    user = profile
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "getPinnedShortcuts failed", e)
                }
            }
        }
        pinnedShortcuts
    }

// This is to ensure backward compatibility with older app versions
// which did not support multiple user profiles
private fun upgradeHiddenApps(prefs: Prefs) {
    val hiddenAppsSet = prefs.hiddenApps
    val newHiddenAppsSet = mutableSetOf<String>()
    for (hiddenPackage in hiddenAppsSet) {
        if (hiddenPackage.contains("|")) newHiddenAppsSet.add(hiddenPackage)
        else newHiddenAppsSet.add(hiddenPackage + android.os.Process.myUserHandle().toString())
    }
    prefs.hiddenApps = newHiddenAppsSet
    prefs.hiddenAppsUpdated = true
}

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
    if (activityInfo.isNotEmpty()) return true
    return false
}

fun isPrivateSpaceProfile(context: Context, userHandle: UserHandle): Boolean {
    return try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.getLauncherUserInfo(userHandle)?.userType == "android.os.usertype.profile.PRIVATE"
    } catch (e: Exception) {
        Log.w("Utils", "Failed to check private space profile", e)
        false
    }
}

fun isPrivateSpaceLocked(context: Context, userHandle: UserHandle): Boolean {
    return try {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        userManager.isQuietModeEnabled(userHandle)
    } catch (e: Exception) {
        Log.w("Utils", "Failed to check private space quiet mode", e)
        true
    }
}

fun getPrivateSpaceUserHandle(context: Context): UserHandle? {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (profile in userManager.userProfiles) {
        if (isPrivateSpaceProfile(context, profile)) return profile
    }
    return null
}

suspend fun getPrivateSpaceApps(
    context: Context,
    prefs: Prefs,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        val appList: MutableList<AppModel> = mutableListOf()
        try {
            val privateSpaceHandle = getPrivateSpaceUserHandle(context) ?: return@withContext appList
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val collator = Collator.getInstance()

            for (app in launcherApps.getActivityList(null, privateSpaceHandle)) {
                if (!AppListBuilder.shouldIncludePrivate(app.applicationInfo.packageName, BuildConfig.APPLICATION_ID)) continue
                val appLabelShown = AppListBuilder.displayLabel(
                    prefs.getAppRenameLabel(app.applicationInfo.packageName),
                    app.label.toString(),
                )
                val isArchived = app.applicationInfo.isArchived
                appList.add(
                    AppModel.App(
                        appLabel = appLabelShown,
                        key = collator.getCollationKey(app.label.toString()),
                        appPackage = app.applicationInfo.packageName,
                        activityClassName = app.componentName.className,
                        isNew = false,
                        user = privateSpaceHandle,
                        firstInstallTime = app.firstInstallTime,
                        isArchived = isArchived,
                    )
                )
            }
            AppListBuilder.sortByLabel(appList, collator) { it.appLabel }
        } catch (e: Exception) {
            Log.w(TAG, "getPrivateSpaceApps failed", e)
        }
        appList
    }
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return android.os.Process.myUserHandle()
}

fun isOlauncherDefault(context: Context): Boolean {
    val roleHeld = try {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME))
            roleManager.isRoleHeld(RoleManager.ROLE_HOME)
        else null
    } catch (e: Exception) {
        Log.w("Utils", "Failed to query home role", e)
        null
    }
    val resolvedPackage = if (roleHeld == null) getDefaultLauncherPackage(context) else null
    return DefaultLauncherCheck.isDefault(roleHeld, resolvedPackage, BuildConfig.APPLICATION_ID) == true
}

fun getDefaultLauncherPackage(context: Context): String {
    val intent = Intent()
    intent.action = Intent.ACTION_MAIN
    intent.addCategory(Intent.CATEGORY_HOME)
    val packageManager = context.packageManager
    val result = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return if (result?.activityInfo != null) {
        result.activityInfo.packageName
    } else "android"
}

fun setPlainWallpaperByTheme(context: Context, appTheme: Int) {
    when (appTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(context, android.R.color.black)
        AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(context, android.R.color.white)
        else -> {
            if (context.isDarkThemeOn())
                setPlainWallpaper(context, android.R.color.black)
            else setPlainWallpaper(context, android.R.color.white)
        }
    }
}

fun setPlainWallpaper(context: Context, color: Int) {
    try {
        val bitmap = createBitmap(1000, 2000)
        bitmap.eraseColor(context.getColor(color))
        val manager = WallpaperManager.getInstance(context)
        manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM)
        manager.setBitmap(bitmap, null, false, WallpaperManager.FLAG_LOCK)
        bitmap.recycle()
    } catch (e: Exception) {
        Log.w(TAG, "setPlainWallpaper failed", e)
    }
}

fun getChangedAppTheme(context: Context, currentAppTheme: Int): Int {
    return when (currentAppTheme) {
        AppCompatDelegate.MODE_NIGHT_YES -> AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.MODE_NIGHT_NO -> AppCompatDelegate.MODE_NIGHT_YES
        else -> {
            if (context.isDarkThemeOn())
                AppCompatDelegate.MODE_NIGHT_NO
            else AppCompatDelegate.MODE_NIGHT_YES
        }
    }
}

fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val component = launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName
    if (component != null)
        launcher.startAppDetailsActivity(component, userHandle, null, null)
    else
        context.showToast(context.getString(R.string.unable_to_open_app_info))
}

suspend fun getWallpaperBitmap(originalImage: Bitmap, width: Int, height: Int): Bitmap {
    return withContext(Dispatchers.IO) {

        val background = createBitmap(width, height)

        val originalWidth: Float = originalImage.width.toFloat()
        val originalHeight: Float = originalImage.height.toFloat()

        val canvas = Canvas(background)
        val heightScale: Float = height / originalHeight
        val widthScale: Float = width / originalWidth
        val scale = maxOf(heightScale, widthScale)

        val (xTranslation, yTranslation) = if (heightScale > widthScale)
            Pair((width - originalWidth * heightScale) / 2.0f, 0f)
        else
            Pair(0f, (height - originalHeight * widthScale) / 2.0f)

        val transformation = Matrix()
        transformation.postTranslate(xTranslation, yTranslation)
        transformation.preScale(scale, scale)

        val paint = Paint()
        paint.isFilterBitmap = true
        canvas.drawBitmap(originalImage, transformation, paint)

        background
    }
}

fun getScreenDimensions(context: Context): Pair<Int, Int> {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val bounds = windowManager.maximumWindowMetrics.bounds
    return Pair(bounds.width(), bounds.height())
}

@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationDrawer(context: Context) {
    // Source: https://stackoverflow.com/a/51132142
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    } catch (e: Exception) {
        Log.w(TAG, "expandNotificationDrawer failed", e)
    }
}

fun openDialerApp(context: Context) {
    try {
        val sendIntent = Intent(Intent.ACTION_DIAL)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        Log.w(TAG, "openDialerApp failed", e)
    }
}

fun openCameraApp(context: Context) {
    try {
        val sendIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        Log.w(TAG, "openCameraApp failed", e)
    }
}

fun openGalleryApp(context: Context) {
    try {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "openGalleryApp failed", e)
    }
}

fun openMessagesApp(context: Context) {
    try {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "openMessagesApp failed", e)
    }
}

fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.d("TAG", e.toString())
    }
}

fun openCalendar(context: Context) {
    try {
        val calendarUri = CalendarContract.CONTENT_URI
            .buildUpon()
            .appendPath("time")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, calendarUri))
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "openCalendar failed", e)
        }
    }
}

fun isAccessServiceEnabled(context: Context): Boolean {
    val enabled = try {
        Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    } catch (e: Exception) {
        Log.w("Utils", "Failed to read accessibility setting", e)
        0
    }
    if (enabled == 1) {
        val enabledServicesString: String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServicesString?.contains(context.packageName + "/" + MyAccessibilityService::class.java.name) ?: false
    }
    return false
}

fun isTablet(context: Context): Boolean {
    val metrics = context.resources.displayMetrics
    val widthInches = metrics.widthPixels / metrics.xdpi
    val heightInches = metrics.heightPixels / metrics.ydpi
    val diagonalInches = sqrt(widthInches.toDouble().pow(2.0) + heightInches.toDouble().pow(2.0))
    if (diagonalInches >= 7.0) return true
    return false
}

fun Context.isDarkThemeOn(): Boolean {
    return resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
}

fun Context.copyToClipboard(text: String) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText(getString(R.string.app_name), text)
    clipboardManager.setPrimaryClip(clipData)
    showToast("")
}

fun Context.openUrl(url: String) {
    if (url.isEmpty()) return
    val intent = Intent(Intent.ACTION_VIEW)
    intent.data = Uri.parse(url)
    startActivity(intent)
}

fun Context.isSystemApp(packageName: String, user: UserHandle? = null): Boolean {
    if (packageName.isBlank()) return true
    return try {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val targetUser = user ?: android.os.Process.myUserHandle()
        val activityList = launcherApps.getActivityList(packageName, targetUser)
        if (activityList.isNotEmpty()) {
            val applicationInfo = activityList.first().applicationInfo
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        } else {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            ((applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0)
                    || (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0))
        }
    } catch (e: Exception) {
        Log.w(TAG, "isSystemApp failed", e)
        false
    }
}

fun Context.uninstall(packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE)
    intent.data = Uri.parse("package:$packageName")
    startActivity(intent)
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    if (attrColor == R.attr.accentColor) {
        val prefs = Prefs(this)
        if (prefs.accent == Accent.WALLPAPER.id && prefs.accentCustomColor != 0) return prefs.accentCustomColor
    }
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

fun Context.deletePinnedShortcut(packageName: String, shortcutIdToDelete: String, user: UserHandle) {
    val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    // 1. Query for existing pinned shortcuts for the package
    val query = LauncherApps.ShortcutQuery().apply {
        setPackage(packageName)
        // Query only for pinned shortcuts
        setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    }

    try {
        val pinnedShortcuts = launcherApps.getShortcuts(query, user)

        if (pinnedShortcuts != null) {
            // 2. Filter out the shortcut to be deleted
            val updatedPinnedIds = pinnedShortcuts
                .filter { it.id != shortcutIdToDelete }
                .map { it.id }

            // 3. Re-pin the remaining shortcuts
            // This replaces the existing set of pinned shortcuts for this package
            launcherApps.pinShortcuts(packageName, updatedPinnedIds, user)
        }
    } catch (e: SecurityException) {
        // Handle cases where the app doesn't have permission
        // (e.g., not the default launcher or active voice interaction service)
        Log.e("ShortcutHelper", "Permission denied to modify pinned shortcuts for $packageName", e)
    } catch (e: IllegalStateException) {
        // Handle cases where the user profile is locked or not running
        Log.e("ShortcutHelper", "User profile unavailable for modifying pinned shortcuts for $packageName", e)
    } catch (e: Exception) {
        // Handle other potential exceptions (like RemoteException wrapped)
        Log.e("ShortcutHelper", "Failed to modify pinned shortcuts for $packageName", e)
    }
}
