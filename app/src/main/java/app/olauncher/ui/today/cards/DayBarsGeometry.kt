package app.olauncher.ui.today.cards

/**
 * Bar sizes for [DayBarsView]. Every day uses the same rules; today is marked by its label only.
 */
object DayBarsGeometry {

    /** Smallest bar drawn, in dp, so a day with little or no time still shows a thin baseline. */
    const val MIN_BAR_DP = 2f

    /** Largest corner radius, in dp. */
    const val MAX_RADIUS_DP = 4f

    /**
     * Bar height in pixels: [minutes] scaled against [scaleMinutes] over [chartHeight], grown by
     * [progress] (0..1), and never lower than [minHeight] or higher than [chartHeight].
     */
    fun barHeight(chartHeight: Float, minutes: Int, scaleMinutes: Int, progress: Float, minHeight: Float): Float {
        val scale = scaleMinutes.coerceAtLeast(1)
        val raw = chartHeight * minutes.coerceAtLeast(0) / scale * progress.coerceIn(0f, 1f)
        return raw.coerceIn(minHeight, chartHeight.coerceAtLeast(minHeight))
    }

    /** Corner radius that never exceeds half the bar height, so short bars stay flat lines, not pills. */
    fun cornerRadius(barHeight: Float, maxRadius: Float): Float = minOf(maxRadius, barHeight / 2f)
}
