package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getUserHandleFromString

object ShortcutIconRenderer {

    fun render(context: Context, slot: Int, prefs: Prefs): Pair<Drawable, Int /*paddingPx*/> {
        if (prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP) {
            val packageName = prefs.getShortcutPackage(slot)
            if (packageName.isNotEmpty()) {
                try {
                    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
                    if (launcher != null) {
                        val user = getUserHandleFromString(context, prefs.getShortcutUser(slot))
                        val activities = launcher.getActivityList(packageName, user)
                        val match = activities.find { it.componentName.className == prefs.getShortcutClass(slot) }
                            ?: activities.firstOrNull()
                        val icon = match?.getBadgedIcon(0)
                        if (icon != null) {
                            return Pair(icon, 8.dpToPx())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return Pair(getDefaultDrawable(context, slot), 12.dpToPx())
    }

    fun getDefaultDrawable(context: Context, slot: Int): Drawable {
        val resId = getDefaultIconRes(slot)
        return ContextCompat.getDrawable(context, resId)?.mutate()
            ?: ColorDrawable(Color.TRANSPARENT)
    }

    @DrawableRes
    fun getDefaultIconRes(slot: Int): Int = when (slot) {
        1 -> R.drawable.ic_shortcut_search
        2 -> R.drawable.ic_shortcut_gallery
        3 -> R.drawable.ic_shortcut_camera
        4 -> R.drawable.ic_shortcut_messages
        5 -> R.drawable.ic_shortcut_phone
        6 -> R.drawable.ic_shortcut_more
        else -> R.drawable.ic_shortcut_more
    }

    @StringRes
    fun getDefaultLabelRes(slot: Int): Int = when (slot) {
        1 -> R.string.shortcut_search
        2 -> R.string.shortcut_gallery
        3 -> R.string.shortcut_camera
        4 -> R.string.shortcut_messages
        5 -> R.string.shortcut_phone
        6 -> R.string.shortcut_more
        else -> R.string.shortcut_more
    }

    fun getDefaultLabel(context: Context, slot: Int): String {
        return context.getString(getDefaultLabelRes(slot))
    }

    fun getLabel(context: Context, slot: Int, prefs: Prefs, isAppIcon: Boolean): String {
        if (isAppIcon && prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP) {
            val label = prefs.getShortcutLabel(slot)
            if (label.isNotEmpty()) return label
        }
        return getDefaultLabel(context, slot)
    }
}
