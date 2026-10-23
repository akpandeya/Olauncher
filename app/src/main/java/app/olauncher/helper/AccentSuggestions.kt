package app.olauncher.helper

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Accent colours suggested from the wallpaper's main colours (Context-free).
 * Each pick is toned so accent text reads on the card background (see [ReadableScrim]):
 * lightened on a dark theme, darkened on a light one, until it reaches [ReadableScrim.MIN_CONTRAST].
 */
object AccentSuggestions {
    const val MAX_SUGGESTIONS = 5

    private const val MIN_SATURATION = 0.12f
    private const val MIN_HUE_GAP = 18f
    private const val DARK_BACKGROUND = 0xFF000000.toInt()
    private const val LIGHT_BACKGROUND = 0xFFFFFFFF.toInt()

    /**
     * @param wallpaperColors the wallpaper's main colours as ARGB ints, most important first.
     * @return up to [MAX_SUGGESTIONS] opaque ARGB colours, distinct, readable on the theme.
     */
    fun suggest(wallpaperColors: List<Int>, themeIsDark: Boolean): List<Int> {
        val background = if (themeIsDark) DARK_BACKGROUND else LIGHT_BACKGROUND
        val toned = wallpaperColors
            .map { opaque(it) }
            .distinct()
            .map { tone(it, background, themeIsDark) }
        val colourful = toned.filter { hsl(it)[1] >= MIN_SATURATION }
        // A grey wallpaper still gets its greys rather than an empty list.
        val pool = colourful.ifEmpty { toned }
        val picked = mutableListOf<Int>()
        for (colour in pool) {
            if (picked.none { sameHue(it, colour) } && colour !in picked) picked += colour
            if (picked.size == MAX_SUGGESTIONS) break
        }
        return picked
    }

    /** [argb] moved in lightness, keeping hue and saturation, until it reaches the contrast on [background]. */
    fun tone(argb: Int, background: Int, lighten: Boolean): Int {
        val (h, s, l) = hsl(argb).let { Triple(it[0], it[1], it[2]) }
        var lightness = l
        var result = fromHsl(h, s, lightness)
        var steps = 0
        while (ReadableScrim.contrast(result, background) < ReadableScrim.MIN_CONTRAST && steps < 100) {
            lightness = if (lighten) min(1f, lightness + 0.01f) else max(0f, lightness - 0.01f)
            result = fromHsl(h, s, lightness)
            steps++
        }
        return result
    }

    private fun sameHue(a: Int, b: Int): Boolean {
        val ha = hsl(a)
        val hb = hsl(b)
        if (ha[1] < MIN_SATURATION || hb[1] < MIN_SATURATION) return a == b
        val diff = abs(ha[0] - hb[0])
        return min(diff, 360f - diff) < MIN_HUE_GAP
    }

    private fun opaque(argb: Int): Int = argb or (0xFF shl 24)

    /** [hue 0-360, saturation 0-1, lightness 0-1] of an RGB colour. */
    fun hsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d == 0f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) {
            r -> ((g - b) / d) % 6f
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return floatArrayOf(if (h < 0f) h + 360f else h, s.coerceIn(0f, 1f), l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = Math.round((v + m) * 255f).coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }
}
