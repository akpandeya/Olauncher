package app.olauncher.helper

object TouchTarget {
    const val MIN_TARGET_DP = 48

    /**
     * Calculates the extra padding per side in pixels needed to bring a view of size [viewSizePx]
     * up to a minimum touch target dimension of [minPx].
     *
     * @param viewSizePx the current content or visible size of the view in pixels.
     * @param minPx the required minimum touch target dimension in pixels.
     * @return 0 when [viewSizePx] is greater than or equal to [minPx], else half the difference ((minPx - viewSizePx) / 2).
     */
    fun extraPaddingPx(viewSizePx: Int, minPx: Int): Int {
        if (viewSizePx >= minPx) return 0
        return (minPx - viewSizePx) / 2
    }

    /**
     * Calculates the total target dimension in pixels: [viewSizePx] plus twice the extra padding.
     */
    fun targetSizePx(viewSizePx: Int, minPx: Int): Int =
        viewSizePx + (2 * extraPaddingPx(viewSizePx, minPx))
}
