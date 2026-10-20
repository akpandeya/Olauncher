package app.olauncher.helper

import app.olauncher.data.Prefs
import kotlin.math.pow

/**
 * Whether to show the soft dark wash behind the home clock and info lines.
 * It only helps light text, so it never shows with dark (light-theme) text.
 * With light text it shows when white text would read poorly on any of the
 * wallpaper's main colours (below [MIN_CONTRAST]), which catches light and busy photos.
 */
object HomeWash {
    /** WCAG AA contrast for body text. */
    const val MIN_CONTRAST = 4.5

    /**
     * @param autoContrast the user left home text contrast on auto (not "follow theme").
     * @param themeIsDark the theme draws light text.
     * @param supportsDarkText the wallpaper says it is light (HINT_SUPPORTS_DARK_TEXT); null when unknown.
     * @param wallpaperColors the wallpaper's main colours as ARGB ints; empty when unknown.
     * @param level the user's darkness setting: auto, strong (always on with light text) or off.
     */
    fun shouldShow(
        autoContrast: Boolean,
        themeIsDark: Boolean,
        supportsDarkText: Boolean?,
        wallpaperColors: List<Int>,
        level: Int = Prefs.WASH_AUTO,
    ): Boolean {
        if (level == Prefs.WASH_OFF || !themeIsDark) return false
        if (level == Prefs.WASH_STRONG) return true
        if (!autoContrast) return false
        // Unknown wallpaper (e.g. a live one): the wash is subtle, so err on readable.
        if (supportsDarkText == null) return true
        if (supportsDarkText) return true
        return wallpaperColors.any { whiteContrastOn(it) < MIN_CONTRAST }
    }

    /** WCAG contrast ratio of white text on [argb]. */
    fun whiteContrastOn(argb: Int): Double = 1.05 / (relativeLuminance(argb) + 0.05)

    /** WCAG relative luminance (0 black to 1 white) of an ARGB colour; alpha is ignored. */
    fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
