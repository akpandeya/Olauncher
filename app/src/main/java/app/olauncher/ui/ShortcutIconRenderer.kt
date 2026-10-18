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
import kotlin.math.roundToInt

/** A resolved shortcut icon: the drawable, the padding for its 48dp view, and whether it is an app icon. */
data class RenderedShortcutIcon(
    val drawable: Drawable,
    val paddingPx: Int,
    val isAppIcon: Boolean,
)

object ShortcutIconRenderer {

    /** Size of each shortcut ImageView in the home column. */
    const val VIEW_SIZE_DP = 48

    /** Visible box of an app icon (its adaptive mask fills this box). */
    const val APP_ICON_BOX_DP = 32
    const val DEFAULT_ICON_PADDING_DP = 12

    /** AdaptiveIconDrawable draws its layers 1.5x the mask bounds (25% extra on each side). */
    const val ADAPTIVE_LAYER_SCALE = 1.5f

    /** Padding that draws a bare adaptive layer as large as it appears inside a [maskBoxDp] icon. */
    fun layerPaddingDp(viewSizeDp: Int, maskBoxDp: Int): Int =
        ((viewSizeDp - maskBoxDp * ADAPTIVE_LAYER_SCALE) / 2f).roundToInt().coerceAtLeast(0)

    /**
     * Padding in dp for a shortcut icon. A monochrome layer is a bare 108-unit adaptive layer
     * with its glyph in the centre, so it is drawn at layer scale instead of inside the
     * [APP_ICON_BOX_DP] box; otherwise its glyph shows at two thirds of the intended size.
     */
    fun paddingDp(isAppIcon: Boolean, treatment: ShortcutIconStyle.Treatment?): Int = when {
        !isAppIcon -> DEFAULT_ICON_PADDING_DP
        treatment == ShortcutIconStyle.Treatment.MONOCHROME_LAYER ->
            layerPaddingDp(VIEW_SIZE_DP, APP_ICON_BOX_DP)
        else -> ((VIEW_SIZE_DP - APP_ICON_BOX_DP) / 2).coerceAtLeast(0)
    }

    fun render(context: Context, slot: Int, prefs: Prefs): RenderedShortcutIcon {
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
                            val monochromeLayer = (icon as? AdaptiveIconDrawable)?.monochrome
                                ?: (match?.getIcon(0) as? AdaptiveIconDrawable)?.monochrome
                            val style = prefs.shortcutIconStyle
                            val styledIcon = applyStyle(
                                icon = icon,
                                style = style,
                                primaryColor = context.getColorFromAttr(R.attr.primaryColor),
                                monochromeLayer = monochromeLayer
                            )
                            val treatment = ShortcutIconStyle.resolveTreatment(style, monochromeLayer != null)
                            return RenderedShortcutIcon(
                                drawable = styledIcon,
                                paddingPx = paddingDp(isAppIcon = true, treatment = treatment).dpToPx(),
                                isAppIcon = true,
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return RenderedShortcutIcon(
            drawable = getDefaultDrawable(context, slot),
            paddingPx = paddingDp(isAppIcon = false, treatment = null).dpToPx(),
            isAppIcon = false,
        )
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

    /** [isAppIcon] comes from [render]; it tells whether an app icon was resolved. */
    fun getLabel(context: Context, slot: Int, prefs: Prefs, isAppIcon: Boolean): String {
        if (isAppIcon && prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP) {
            val label = prefs.getShortcutLabel(slot)
            if (label.isNotEmpty()) return label
        }
        return getDefaultLabel(context, slot)
    }
}
