package app.olauncher.helper

/**
 * Layout rule for the "DATE · WEATHER" line under the home clock.
 * When both do not fit on one row, weather moves under the date (no separator dot),
 * so a long German date can wrap instead of pushing the weather off screen.
 */
object HomeMetaLine {

    /** Show the dot only when both parts are shown side by side. */
    fun showSeparator(dateShown: Boolean, weatherShown: Boolean, stacked: Boolean): Boolean =
        dateShown && weatherShown && !stacked

    /**
     * @return true when the weather should go on its own line under the date.
     * Widths are in px and include each view's padding. [availableWidthPx] 0 or less means unknown.
     */
    fun shouldStack(
        dateWidthPx: Float,
        separatorWidthPx: Float,
        weatherWidthPx: Float,
        weatherShown: Boolean,
        availableWidthPx: Float,
    ): Boolean {
        if (!weatherShown || availableWidthPx <= 0f) return false
        return dateWidthPx + separatorWidthPx + weatherWidthPx > availableWidthPx
    }
}
