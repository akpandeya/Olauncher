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

/** Playback progress maths and glow colour choice for the Home music pulse. Context-free. */
object MusicPulse {
    /** Glows that are nearly black or nearly white disappear on Home, so they are skipped. */
    const val MIN_LIGHTNESS = 0.12f
    const val MAX_LIGHTNESS = 0.85f

    /** Saturation a swatch needs to be preferred over a grey one. */
    const val MIN_SATURATION = 0.15f

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
