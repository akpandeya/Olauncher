package app.olauncher.ui

import android.util.Log
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.Accent
import app.olauncher.data.Prefs
import app.olauncher.data.ThemePack
import app.olauncher.data.ThemePackCodec
import app.olauncher.helper.HomeWash
import app.olauncher.helper.TextBrightness
import app.olauncher.helper.ContrastPolicy
import app.olauncher.helper.ThemePacks
import app.olauncher.helper.ThemePackDecor
import app.olauncher.helper.dpToPx
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import android.view.Gravity

internal fun HomeFragment.applyTextContrast() {
    if (_binding == null) return
    // A theme pack colours the home screen through the theme (its scrim is drawn by
    // applyHomeDecor), so the wallpaper contrast pass must not fight it: only the packed
    // text colours from XML show through untouched. Restoring the cached originals
    // (captured from the packed theme) undoes any earlier override from this pass.
    if (prefs.themePackId.isNotEmpty()) {
        val views = originalTextColors.keys + originalShadows.keys
        views.forEach { view ->
            originalTextColors[view]?.let { view.setTextColor(it) }
            originalShadows[view]?.let { original ->
                val originalShadowColor = originalShadowColors[view] ?: view.shadowColor
                view.setShadowLayer(original[0], original[1], original[2], originalShadowColor)
            }
        }
        return
    }
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
    val wallpaperAccent = if (prefs.accent == Accent.WALLPAPER.id) prefs.accentCustomColor else 0
    val accentViews = setOf(binding.clock, binding.clockWords, binding.date)
    val views = listOf(
        binding.clock, binding.clockWords, binding.date, binding.weatherSeparator,
        binding.weather, binding.contextLine, binding.nextEventLine, binding.noteLine, binding.noteMore, binding.intentionLine, binding.tvScreenTime,
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
        if (view !in originalTextColors) {
            originalTextColors[view] = if (wallpaperAccent != 0 && view in accentViews) wallpaperAccent else view.currentTextColor
        }
        val base = treatment.color ?: originalTextColors.getValue(view)
        val alpha = (Color.alpha(base) * textOpacity).toInt()
        view.setTextColor(Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base)))
    }
}

/**
 * Shows the seasonal dressing for the active theme pack (HomeDecorView) and draws the pack
 * background as a scrim over the system wallpaper (homeWash, at the user's Background
 * opacity). With no pack, an unknown pack or a plain (non-seasonal) pack the dressing view
 * hides. The pack parse is cached on the fragment, so calling this on every resume and
 * alignment change only re-reads prefs.
 */
internal fun HomeFragment.applyHomeDecor() {
    val binding = _binding ?: return
    val id = prefs.themePackId
    // Whether a pack was active on the previous pass: only then does homeWash still hold
    // the pack scrim that must be handed back to the contrast pass.
    val hadPack = decorPackId != null
    val pack = when {
        id.isEmpty() -> {
            decorPack = null
            decorPackId = null
            null
        }
        id == decorPackId -> decorPack
        else -> ThemePacks.load(requireContext(), id).also {
            decorPack = it
            // Only remember ids that actually resolve: a stale id (pack removed from
            // assets) must not count as "a pack scrim is on screen", or the contrast
            // wash would be hidden on alignment-only passes that never reload it.
            decorPackId = if (it != null) id else null
        }
    }
    if (pack == null) {
        binding.homeDecor.stop()
        // After a pack scrim the contrast pass owns homeWash again; hide the scrim so the
        // next non-pack applyTextContrast pass can re-establish its wash cleanly. In steady
        // non-pack mode (e.g. an alignment change) the contrast wash must survive untouched.
        if (hadPack) binding.homeWash.isVisible = false
        return
    }
    val context = context ?: return
    // The pack colour is a scrim over the system wallpaper: alpha 100% is the solid colour
    // the windowBackground used to paint, lower shows the user's photo through. Per-resume
    // work only, never per frame.
    val scrimAlpha = (ThemePackDecor.backgroundAlphaFraction(prefs.backgroundOpacity) * 255).toInt()
    val scrimColor = (ThemePackCodec.parseHex(pack.background) and 0x00FFFFFF) or (scrimAlpha shl 24)
    binding.homeWash.background = ColorDrawable(scrimColor)
    binding.homeWash.isVisible = true
    // The scrim covers the whole screen, whatever the top text does.
    sizeHomeWash()
    binding.homeDecor.configure(
        pack = pack,
        alignmentRight = prefs.homeAlignment == Gravity.END,
        // The dressing is decoration: keep the home still on e-ink or with animations off.
        animate = !context.isEinkDisplay() && !context.isSystemAnimationsDisabled(),
    )
}
