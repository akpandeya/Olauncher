package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.ShortcutIconStyle
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.getUserHandleFromString

object ShortcutIconRenderer {

    private const val APP_ICON_PADDING_DP = 8
    private const val DEFAULT_ICON_PADDING_DP = 12

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
                        val icon = match?.getBadgedIcon(0) ?: match?.getIcon(0)
                        if (icon != null) {
                            val styledIcon = applyStyle(
                                icon = icon,
                                style = prefs.shortcutIconStyle,
                                primaryColor = context.getColorFromAttr(R.attr.primaryColor),
                                monochromeLayer = (icon as? AdaptiveIconDrawable)?.monochrome
                                    ?: (match?.getIcon(0) as? AdaptiveIconDrawable)?.monochrome
                            )
                            return Pair(styledIcon, APP_ICON_PADDING_DP.dpToPx())
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return Pair(getDefaultDrawable(context, slot), DEFAULT_ICON_PADDING_DP.dpToPx())
    }

    fun applyStyle(
        icon: Drawable,
        style: Int,
        @ColorInt primaryColor: Int,
        monochromeLayer: Drawable? = (icon as? AdaptiveIconDrawable)?.monochrome,
    ): Drawable {
        val hasMonochrome = monochromeLayer != null
        val treatment = ShortcutIconStyle.resolveTreatment(style, hasMonochrome)
        return when (treatment) {
            ShortcutIconStyle.Treatment.ORIGINAL -> {
                icon.mutate().apply {
                    colorFilter = null
                    setTintList(null)
                    alpha = 255
                }
            }
            ShortcutIconStyle.Treatment.MONOCHROME_LAYER -> {
                monochromeLayer!!.mutate().apply {
                    colorFilter = null
                    setTint(primaryColor)
                    alpha = 255
                }
            }
            ShortcutIconStyle.Treatment.FALLBACK_GRAYSCALE -> {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                icon.mutate().apply {
                    colorFilter = ColorMatrixColorFilter(matrix)
                    setTintList(null)
                    alpha = 255
                }
            }
            ShortcutIconStyle.Treatment.FALLBACK_GRAYSCALE_ALPHA -> {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                icon.mutate().apply {
                    colorFilter = ColorMatrixColorFilter(matrix)
                    setTintList(null)
                    alpha = ShortcutIconStyle.FALLBACK_ALPHA_INT
                }
            }
        }
    }

    fun getDefaultDrawable(context: Context, slot: Int): Drawable {
        val resId = getDefaultIconRes(slot)
        val primaryColor = context.getColorFromAttr(R.attr.primaryColor)
        return ContextCompat.getDrawable(context, resId)?.mutate()?.apply {
            colorFilter = null
            setTint(primaryColor)
            alpha = 255
        } ?: ColorDrawable(Color.TRANSPARENT)
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

    /** [paddingPx] is the value returned by [render]; true when an app icon was resolved. */
    fun isAppIcon(paddingPx: Int): Boolean = paddingPx == APP_ICON_PADDING_DP.dpToPx()

    /** [paddingPx] is the value returned by [render]; it tells whether an app icon was resolved. */
    fun getLabel(context: Context, slot: Int, prefs: Prefs, paddingPx: Int): String {
        if (isAppIcon(paddingPx) && prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP) {
            val label = prefs.getShortcutLabel(slot)
            if (label.isNotEmpty()) return label
        }
        return getDefaultLabel(context, slot)
    }
}
