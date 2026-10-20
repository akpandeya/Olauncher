package app.olauncher.ui

import android.util.Log
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.res.Configuration
import android.graphics.Color
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.helper.HomeWash
import app.olauncher.helper.TextBrightness
import app.olauncher.helper.ContrastPolicy
import app.olauncher.helper.dpToPx

internal fun HomeFragment.applyTextContrast() {
    if (_binding == null) return
    val context = context ?: return
    val themeIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val autoContrast = prefs.homeTextContrast != Prefs.TEXT_CONTRAST_THEME
    val colors: WallpaperColors? = if (!autoContrast) {
        null
    } else {
        try {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        } catch (e: Exception) {
            Log.w("HomeFragment", "Failed to read wallpaper colors", e)
            null
        }
    }
    val supportsDarkText: Boolean? =
        colors?.let { (it.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0 }
    val treatment = ContrastPolicy.decide(supportsDarkText, themeIsDark)
    val washLevel = prefs.homeWashLevel
    binding.homeWash.isVisible = HomeWash.shouldShow(
        autoContrast = autoContrast,
        themeIsDark = themeIsDark,
        supportsDarkText = supportsDarkText,
        wallpaperColors = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
            .map { it.toArgb() },
        level = washLevel,
    )
    binding.homeWash.setBackgroundResource(
        if (washLevel == Prefs.WASH_STRONG) R.drawable.home_full_wash else R.drawable.home_top_wash
    )
    val textOpacity = if (themeIsDark) TextBrightness.opacity(prefs.homeTextBrightness) else 1f
    sizeHomeWash()
    val views = listOf(
        binding.clock, binding.clockWords, binding.date, binding.weatherSeparator,
        binding.weather, binding.contextLine, binding.nextEventLine, binding.intentionLine, binding.tvScreenTime,
        binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
        binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8,
    )
    views.forEach { view ->
        if (view !in originalShadows) {
            originalShadows[view] = floatArrayOf(view.shadowRadius, view.shadowDx, view.shadowDy)
            originalShadowColors[view] = view.shadowColor
        }
        val original = originalShadows.getValue(view)
        if (treatment.shadowRadiusDp > 0) {
            val base = if (themeIsDark) Color.BLACK else Color.WHITE
            val shadowColor = Color.argb((treatment.shadowAlpha * 255).toInt(), Color.red(base), Color.green(base), Color.blue(base))
            view.setShadowLayer(treatment.shadowRadiusDp.dpToPx().toFloat(), 0f, original[2], shadowColor)
        } else {
            view.setShadowLayer(original[0], original[1], original[2], originalShadowColors.getValue(view))
        }
        if (view !in originalTextColors) originalTextColors[view] = view.currentTextColor
        val base = treatment.color ?: originalTextColors.getValue(view)
        val alpha = (Color.alpha(base) * textOpacity).toInt()
        view.setTextColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)))
    }
}
