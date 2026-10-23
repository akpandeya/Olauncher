package app.olauncher.helper

import kotlin.math.max
import kotlin.math.min

/**
 * The home clock colour taken from the wallpaper (Context-free).
 * The pick is the most colourful wallpaper colour, toned until it reaches
 * [ReadableScrim.MIN_CONTRAST] against every main wallpaper colour, because any of them
 * can sit behind the clock. When no tone can do that (a busy photo with both dark and
 * light areas) the answer is null and the theme colour stays.
 */
object ClockColour {
    private const val STEP = 0.01f

    private const val MODE_NIGHT_NO = 1
    private const val MODE_NIGHT_YES = 2

    /** Whether the app draws light text: the app theme if it is fixed, else the system setting. */
    fun themeIsDark(appTheme: Int, systemIsDark: Boolean): Boolean = when (appTheme) {
        MODE_NIGHT_YES -> true
        MODE_NIGHT_NO -> false
        else -> systemIsDark
    }

    /**
     * @param wallpaperColors the wallpaper's main colours as ARGB ints, most important first.
     * @param themeIsDark the theme draws light text, so the pick is lightened; otherwise darkened.
     * @return an opaque ARGB colour, or null to keep the theme colour.
     */
    fun pick(wallpaperColors: List<Int>, themeIsDark: Boolean): Int? {
        val backdrops = wallpaperColors.map { it or (0xFF shl 24) }.distinct()
        if (backdrops.isEmpty()) return null
        // maxByOrNull keeps the first of equals, so the more important colour wins a tie.
        val seed = backdrops.maxByOrNull { AccentSuggestions.hsl(it)[1] } ?: return null
        val (h, s, l) = AccentSuggestions.hsl(seed).let { Triple(it[0], it[1], it[2]) }
        var lightness = l
        while (true) {
            val candidate = AccentSuggestions.fromHsl(h, s, lightness)
            if (readableOnAll(candidate, backdrops)) return candidate
            val next = if (themeIsDark) min(1f, lightness + STEP) else max(0f, lightness - STEP)
            if (next == lightness) return null
            lightness = next
        }
    }

    /** True when [colour] reaches the contrast floor on each of [backdrops]. */
    fun readableOnAll(colour: Int, backdrops: List<Int>): Boolean =
        backdrops.all { ReadableScrim.contrast(colour, it or (0xFF shl 24)) >= ReadableScrim.MIN_CONTRAST }
}
