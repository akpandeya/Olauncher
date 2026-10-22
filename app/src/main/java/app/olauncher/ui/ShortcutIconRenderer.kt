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
import app.olauncher.data.ShortcutAction
import app.olauncher.data.ShortcutIconStyle
import app.olauncher.data.ShortcutSize
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.getUserHandleFromString
import kotlin.math.roundToInt
import android.util.Log

private const val TAG = "ShortcutIconRenderer"

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
    fun paddingDp(
        isAppIcon: Boolean,
        treatment: ShortcutIconStyle.Treatment?,
        viewSizeDp: Int = VIEW_SIZE_DP,
    ): Int {
        val appIconBoxDp = viewSizeDp * 2 / 3
        val defaultIconPaddingDp = viewSizeDp / 4
        return when {
            !isAppIcon -> defaultIconPaddingDp
            treatment == ShortcutIconStyle.Treatment.MONOCHROME_LAYER ->
                layerPaddingDp(viewSizeDp, appIconBoxDp)
            else -> ((viewSizeDp - appIconBoxDp) / 2).coerceAtLeast(0)
        }
    }

    fun render(
        context: Context,
        slot: Int,
        prefs: Prefs,
        viewSizeDp: Int = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep),
    ): RenderedShortcutIcon {
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
                                paddingPx = paddingDp(isAppIcon = true, treatment = treatment, viewSizeDp = viewSizeDp).dpToPx(),
                                isAppIcon = true,
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "render failed", e)
                }
            }
        }
        return RenderedShortcutIcon(
            drawable = getBuiltinDrawable(context, getBuiltinIconRes(slot, prefs.getShortcutBuiltin(slot))),
            paddingPx = paddingDp(isAppIcon = false, treatment = null, viewSizeDp = viewSizeDp).dpToPx(),
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

    fun getDefaultDrawable(context: Context, slot: Int): Drawable =
        getBuiltinDrawable(context, getDefaultIconRes(slot))

    private fun getBuiltinDrawable(context: Context, @DrawableRes resId: Int): Drawable {
        val primaryColor = context.getColorFromAttr(R.attr.primaryColor)
        return ContextCompat.getDrawable(context, resId)?.mutate()?.apply {
            colorFilter = null
            setTint(primaryColor)
            alpha = 255
        } ?: ColorDrawable(Color.TRANSPARENT)
    }

    @DrawableRes
    fun getDefaultIconRes(slot: Int): Int = getBuiltinIconRes(slot, ShortcutAction.NONE_ID)

    @StringRes
    fun getDefaultLabelRes(slot: Int): Int = getBuiltinLabelRes(slot, ShortcutAction.NONE_ID)

    /** Icon for [slot] given its stored built-in id; slots with no action show the settings glyph as a placeholder. */
    @DrawableRes
    fun getBuiltinIconRes(slot: Int, storedId: Int): Int =
        (ShortcutAction.resolve(slot, storedId) ?: ShortcutAction.SETTINGS).iconRes

    @StringRes
    fun getBuiltinLabelRes(slot: Int, storedId: Int): Int =
        (ShortcutAction.resolve(slot, storedId) ?: ShortcutAction.SETTINGS).labelRes

    fun getDefaultLabel(context: Context, slot: Int): String {
        return context.getString(getDefaultLabelRes(slot))
    }

    /** [isAppIcon] comes from [render]; it tells whether an app icon was resolved. */
    fun getLabel(context: Context, slot: Int, prefs: Prefs, isAppIcon: Boolean): String {
        if (isAppIcon && prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP) {
            val label = prefs.getShortcutLabel(slot)
            if (label.isNotEmpty()) return label
        }
        return context.getString(getBuiltinLabelRes(slot, prefs.getShortcutBuiltin(slot)))
    }
}
