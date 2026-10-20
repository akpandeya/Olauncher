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
