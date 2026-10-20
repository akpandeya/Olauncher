package app.olauncher.helper

import kotlin.math.min

/**
 * Sizing for the big "poster" home clock: as large as the dimen asks, but never wider than
 * the row (so "23:59" never clips) and never taller than its share of the screen.
 */
object PosterClock {
    /** Widest time the clock can show. Sizing to it keeps the size steady through the day. */
    const val WIDEST_SAMPLE = "23:59"

    /** Leaves a little room for glyph overhang and rounding in text measuring. */
    const val WIDTH_SAFETY = 0.97f

    /**
     * @param desiredPx the size from the dimen (font scale already applied).
     * @param sampleWidthAtDesiredPx width of [WIDEST_SAMPLE] drawn at [desiredPx].
     * @param availableWidthPx room for the clock row; 0 or less means unknown (no width cap).
     * @param maxLineHeightPx tallest the clock line may be; 0 or less means no height cap.
     * @param lineHeightRatio line height divided by text size for the clock font.
     */
    fun fitTextSizePx(
        desiredPx: Float,
        sampleWidthAtDesiredPx: Float,
        availableWidthPx: Float,
        maxLineHeightPx: Float,
        lineHeightRatio: Float,
    ): Float {
        if (desiredPx <= 0f) return desiredPx
        var size = desiredPx
        val room = availableWidthPx * WIDTH_SAFETY
        if (sampleWidthAtDesiredPx > 0f && availableWidthPx > 0f && sampleWidthAtDesiredPx > room) {
            // Text width grows in step with text size (letter spacing is in ems too).
            size = desiredPx * room / sampleWidthAtDesiredPx
        }
        if (maxLineHeightPx > 0f) {
            size = min(size, maxLineHeightPx / lineHeightRatio.coerceAtLeast(1f))
        }
        return size
    }
}
