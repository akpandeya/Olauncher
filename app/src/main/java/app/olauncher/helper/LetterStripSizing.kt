package app.olauncher.helper

object LetterStripSizing {

    const val SCALE_ACTIVE = 1.4f
    const val SCALE_NEIGHBOUR_1 = 1.2f
    const val SCALE_NEIGHBOUR_2 = 1.08f
    const val SCALE_REST = 1.0f

    /** Letter size that fits [count] slots in [heightPx], never above [maxPx]. */
    fun textSizePx(heightPx: Int, count: Int, maxPx: Float): Float {
        if (heightPx <= 0 || count <= 0) return maxPx
        // A glyph needs about 1.2x its size in line height.
        return minOf(maxPx, heightPx.toFloat() / count / 1.2f)
    }

    /**
     * Fisheye scale factor for a letter at [distance] slots from the active slot.
     * Distance 0 is the touched letter, falling off over two neighbours on each side,
     * and returning to 1.0f at distance 3 or greater.
     */
    fun scaleForDistance(distance: Int): Float = when (kotlin.math.abs(distance)) {
        0 -> SCALE_ACTIVE
        1 -> SCALE_NEIGHBOUR_1
        2 -> SCALE_NEIGHBOUR_2
        else -> SCALE_REST
    }

    fun fisheyeScale(distance: Int): Float = scaleForDistance(distance)
}
