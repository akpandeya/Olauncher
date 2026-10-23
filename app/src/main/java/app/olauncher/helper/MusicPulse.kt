package app.olauncher.helper

/** A colour swatch from album art, reduced to what the glow choice needs. */
data class SwatchColor(
    val argb: Int,
    val population: Int,
    /** HSL saturation, 0 to 1. */
    val saturation: Float,
    /** HSL lightness, 0 to 1. */
    val lightness: Float,
)

/** View alpha range the glow breathes between. [low] is the dimmest point, [high] the brightest. */
data class GlowPulseRange(val low: Float, val high: Float)

/** Playback progress maths and glow colour choice for the Home music pulse. Context-free. */
object MusicPulse {
    /** Glows that are nearly black or nearly white disappear on Home, so they are skipped. */
    const val MIN_LIGHTNESS = 0.12f
    const val MAX_LIGHTNESS = 0.85f

    /** Saturation a swatch needs to be preferred over a grey one. */
    const val MIN_SATURATION = 0.15f

    /** Glow strength in percent. The default (67) gives centre alpha 0xAA, the original look. */
    const val GLOW_STRENGTH_MIN = 0
    const val GLOW_STRENGTH_MAX = 100
    const val GLOW_STRENGTH_DEFAULT = 67

    /** Breathing range at the default strength. */
    const val GLOW_PULSE_LOW = 0.35f
    const val GLOW_PULSE_HIGH = 0.8f

    /** Keeps a stored or slider strength inside 0 to 100. */
    fun clampGlowStrength(percent: Int): Int = percent.coerceIn(GLOW_STRENGTH_MIN, GLOW_STRENGTH_MAX)

    /** True when the glow should be drawn at all; 0 hides it. */
    fun isGlowVisible(percent: Int): Boolean = clampGlowStrength(percent) > 0

    /** Alpha (0 to 255) of the glow centre for a strength in percent. 67 gives 0xAA, 100 gives 0xFF. */
    fun glowCentreAlpha(percent: Int): Int = clampGlowStrength(percent) * 255 / GLOW_STRENGTH_MAX

    /**
     * Breathing range for a strength. Both ends scale with the strength relative to the default,
     * so the low-to-high ratio (the breathing feel) stays the same. The high end is capped at 1,
     * and the low end is scaled by the same factor so it never passes the high end.
     */
    fun glowPulseRange(percent: Int): GlowPulseRange {
        val factor = clampGlowStrength(percent).toFloat() / GLOW_STRENGTH_DEFAULT
        val high = (GLOW_PULSE_HIGH * factor).coerceAtMost(1f)
        val low = (GLOW_PULSE_LOW * factor).coerceAtMost(high)
        return GlowPulseRange(low, high)
    }

    /**
     * Playback position now. While playing, the last reported position moves on with the clock
     * at the playback speed. [positionAtMs] is when [positionMs] was reported (elapsed realtime);
     * 0 means it was never reported, so the position is used as it is.
     */
    fun positionMs(
        positionMs: Long,
        positionAtMs: Long,
        nowMs: Long,
        speed: Float,
        playing: Boolean,
        durationMs: Long,
    ): Long {
        val base = positionMs.coerceAtLeast(0L)
        val estimated = if (!playing || positionAtMs <= 0L) {
            base
        } else {
            val elapsed = (nowMs - positionAtMs).coerceAtLeast(0L)
            base + (elapsed * speed.coerceAtLeast(0f)).toLong()
        }
        return if (durationMs > 0L) estimated.coerceIn(0L, durationMs) else estimated.coerceAtLeast(0L)
    }

    /** Fraction of the track played, 0 to 1. Null when the duration is unknown, so no bar is drawn. */
    fun progress(
        positionMs: Long,
        positionAtMs: Long,
        nowMs: Long,
        speed: Float,
        playing: Boolean,
        durationMs: Long,
    ): Float? {
        if (durationMs <= 0L) return null
        val position = positionMs(positionMs, positionAtMs, nowMs, speed, playing, durationMs)
        return (position.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /**
     * Artwork the launcher can load: web links, and the content:// and file:// links that
     * Android tells apps to use for artwork. Anything else (or a blank value) is skipped.
     */
    fun isLoadableArtwork(uri: String?): Boolean {
        val trimmed = uri?.trim().orEmpty()
        return LOADABLE_SCHEMES.any { trimmed.startsWith("$it:", ignoreCase = true) }
    }

    private val LOADABLE_SCHEMES = listOf("https", "http", "content", "file", "android.resource")

    /** True for web links; these are fetched over the network. */
    fun isWebArtwork(uri: String): Boolean =
        uri.startsWith("https://", ignoreCase = true) || uri.startsWith("http://", ignoreCase = true)

    /**
     * First loadable artwork URI in preference order (album art, art, display icon), trimmed.
     * Players such as YouTube Music often send artwork only as a URI, not as a bitmap.
     */
    fun loadableArtworkUri(vararg candidates: String?): String? =
        candidates.map { it?.trim() }.firstOrNull { isLoadableArtwork(it) }

    /** How long a failed artwork download waits before it is tried again. */
    const val ARTWORK_RETRY_MS = 60_000L

    /**
     * True when artwork should be fetched: it never failed ([failedAtMs] null), or it failed at
     * least [ARTWORK_RETRY_MS] ago. Times are on one clock (elapsed realtime on the phone).
     */
    fun shouldFetchArtwork(failedAtMs: Long?, nowMs: Long): Boolean =
        failedAtMs == null || nowMs - failedAtMs >= ARTWORK_RETRY_MS

    /**
     * Cache key for the glow colour of a track. Built from stable values only: players hand out a
     * new bitmap object on every read, so object identity would miss the cache on every refresh.
     * The art size is included so art that arrives after the title (0 x 0 before) still gets a colour.
     */
    fun glowCacheKey(
        packageName: String,
        title: String,
        artist: String?,
        artworkUri: String?,
        artworkWidth: Int,
        artworkHeight: Int,
    ): String = listOf(packageName, title, artist.orEmpty(), artworkUri.orEmpty(), "${artworkWidth}x$artworkHeight")
        .joinToString("|")

    /** Most album colours the glow fades between. */
    const val GLOW_MAX_COLORS = 3

    /** How long the glow rests on one colour before it starts fading to the next. */
    const val GLOW_COLOR_HOLD_MS = 6_000L

    /** How long one fade between two album colours takes. Hold plus fade is about 9 seconds per colour. */
    const val GLOW_COLOR_FADE_MS = 3_000L

    /**
     * Album colours the glow fades between, at most [max]. The first is [glowColor], so a single-colour
     * glow looks as before. The rest are the other saturated swatches in the usable lightness range,
     * most populous first. With no saturated swatch the glow keeps to the one grey fallback.
     */
    fun glowColors(swatches: List<SwatchColor>, max: Int = GLOW_MAX_COLORS): List<Int> {
        if (max <= 0) return emptyList()
        val first = glowColor(swatches) ?: return emptyList()
        val others = swatches
            .filter { it.lightness in MIN_LIGHTNESS..MAX_LIGHTNESS && it.population > 0 && it.saturation >= MIN_SATURATION }
            .sortedByDescending { it.population }
            .map { it.argb }
            .filter { it != first }
            .distinct()
        return (listOf(first) + others).take(max)
    }

    /** Index of the colour after [index] in a list of [count] colours, wrapping to the start. 0 for an empty list. */
    fun nextGlowColorIndex(index: Int, count: Int): Int =
        if (count <= 0) 0 else (index + 1).mod(count)

    /** Time for the progress wave to move on by one wavelength. Slow, so it reads as calm motion. */
    const val WAVE_PERIOD_MS = 4_000L

    /** Frames per second of the wave motion. Low to save battery; the wave moves slowly anyway. */
    const val WAVE_MAX_FPS = 20
    const val WAVE_FRAME_INTERVAL_MS = 1000L / WAVE_MAX_FPS

    /** Alpha (0 to 255) of the straight track line, before the line colour's own alpha. About 44%, so it stays quieter than the played wave. */
    const val WAVE_TRACK_ALPHA = 0x70

    /** Stroke width of the wave, in dp. Ends are rounded. */
    const val WAVE_STROKE_DP = 3f

    /** Stroke width of the straight track, in dp. Thinner than the wave, so the played part reads first. */
    const val WAVE_TRACK_STROKE_DP = 2f

    /** Height of the wave from the centre line to a crest, in dp. */
    const val WAVE_AMPLITUDE_DP = 3f

    /** Length of one wave cycle, in dp. */
    const val WAVE_LENGTH_DP = 32f

    /** Width of the divider handle at the played/unplayed boundary, in dp. */
    const val WAVE_DIVIDER_WIDTH_DP = 4f

    /** Total height of the divider handle, in dp. Taller than the wave, so it reads as a handle. */
    const val WAVE_DIVIDER_HEIGHT_DP = 14f

    /** Clear space between the divider and each side of the wave and the track, in dp, outlines not included. */
    const val WAVE_DIVIDER_GAP_DP = 3f

    /** Width of the dark outline on each side of the stroke, in dp. Keeps the colour visible on light wallpapers. */
    const val WAVE_OUTLINE_DP = 1f

    /** Alpha (0 to 255) of the dark outline, before the stroke colour's own alpha. Subtle, so the colour stays the main look. */
    const val WAVE_OUTLINE_ALPHA = 0x59

    /** Height of the progress view in the layouts, in dp. Must fit [waveDrawHeightDp]. */
    const val WAVE_VIEW_HEIGHT_DP = 16f

    /** Full height the wave needs: both crests, the stroke and the outline on both sides. */
    fun waveDrawHeightDp(): Float = 2 * WAVE_AMPLITUDE_DP + WAVE_STROKE_DP + 2 * WAVE_OUTLINE_DP

    /** Full height the divider handle needs, with its outline. */
    fun dividerDrawHeightDp(): Float = WAVE_DIVIDER_HEIGHT_DP + 2 * WAVE_OUTLINE_DP

    /** Wave phase, 0 (inclusive) to 1 (exclusive), after [elapsedMs] of motion. 0 when [periodMs] is not positive. */
    fun wavePhase(elapsedMs: Long, periodMs: Long = WAVE_PERIOD_MS): Float {
        if (periodMs <= 0L) return 0f
        return (elapsedMs.mod(periodMs)).toFloat() / periodMs
    }

    /**
     * Vertical offset of the progress wave at [x]. A sine of [amplitude] with one cycle per [wavelength];
     * a growing [phase] (0 to 1 per wavelength) moves the wave forward. 0 for a non-positive wavelength.
     */
    fun waveOffset(x: Float, wavelength: Float, amplitude: Float, phase: Float): Float {
        if (wavelength <= 0f) return 0f
        return amplitude * kotlin.math.sin(2.0 * Math.PI * (x / wavelength - phase)).toFloat()
    }

    /** Contrast the progress line needs against the wallpaper; the WCAG minimum for graphics. */
    const val LINE_MIN_CONTRAST = 3f

    /** Step used when mixing a too-faint album colour towards the text colour. */
    private const val LINE_MIX_STEP = 0.05f

    /** WCAG relative luminance of [argb], 0 (black) to 1 (white). Alpha is ignored. */
    fun relativeLuminance(argb: Int): Float {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        val r = channel((argb shr 16) and 0xFF)
        val g = channel((argb shr 8) and 0xFF)
        val b = channel(argb and 0xFF)
        return (0.2126 * r + 0.7152 * g + 0.0722 * b).toFloat()
    }

    /** WCAG contrast ratio of two colours, 1 to 21. */
    fun contrastRatio(a: Int, b: Int): Float {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    /**
     * Wallpaper colour the line is judged against. The now-playing text is already chosen to read on
     * the wallpaper, so light text means a dark wallpaper (black) and dark text a light one (white).
     */
    fun lineBackground(textColor: Int): Int =
        if (relativeLuminance(textColor) > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    /** Opaque mix of [from] towards [to]; [t] 0 gives [from], 1 gives [to]. */
    fun mixColor(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return Math.round(a + (b - a) * k) and 0xFF
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /**
     * Colour of the progress line. Of the album colours, the one with the best contrast against the
     * wallpaper ([lineBackground]) wins. If even that is below [LINE_MIN_CONTRAST], it is mixed towards
     * the now-playing text colour just enough to reach it. With no album colour, the text colour is used.
     * The result is one steady colour per track; the glow keeps fading between album colours on its own.
     */
    fun progressLineColor(albumColors: List<Int>, textColor: Int): Int {
        if (albumColors.isEmpty()) return textColor
        val background = lineBackground(textColor)
        val best = albumColors.maxBy { contrastRatio(it, background) } or (0xFF shl 24)
        var t = 0f
        var color = best
        while (contrastRatio(color, background) < LINE_MIN_CONTRAST && t < 1f) {
            t = (t + LINE_MIX_STEP).coerceAtMost(1f)
            color = mixColor(best, textColor, t)
        }
        return color
    }

    /** Track colour: [argb] with its alpha scaled by [WAVE_TRACK_ALPHA] out of 255. Constant for the whole song. */
    fun waveTrackColor(argb: Int): Int =
        (scaledAlpha(argb, WAVE_TRACK_ALPHA) shl 24) or (argb and 0x00FFFFFF)

    /** True when both the played wave and the unplayed track show, so the divider between them is drawn. */
    fun waveDividerVisible(progress: Float): Boolean = progress > 0f && progress < 1f

    /**
     * Dark outline drawn under a stroke of colour [argb]: black at [WAVE_OUTLINE_ALPHA] out of 255,
     * scaled by the stroke's own alpha so a faded stroke gets a fainter outline.
     */
    fun waveOutlineColor(argb: Int): Int = scaledAlpha(argb, WAVE_OUTLINE_ALPHA) shl 24

    private fun scaledAlpha(argb: Int, alpha: Int): Int = (argb ushr 24) * alpha / 255

    /**
     * The glow colour: the most populous swatch that is saturated and not too dark or light.
     * Falls back to a plain grey swatch in the same lightness range. Null when none fit.
     */
    fun glowColor(swatches: List<SwatchColor>): Int? {
        val usable = swatches.filter { it.lightness in MIN_LIGHTNESS..MAX_LIGHTNESS && it.population > 0 }
        if (usable.isEmpty()) return null
        val coloured = usable.filter { it.saturation >= MIN_SATURATION }
        val pool = coloured.ifEmpty { usable }
        return pool.maxWithOrNull(compareBy<SwatchColor> { it.population * (0.5f + it.saturation) })?.argb
    }
}
