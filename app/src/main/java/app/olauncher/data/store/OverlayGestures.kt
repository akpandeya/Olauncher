package app.olauncher.data.store

import kotlin.math.abs

/**
 * Context-free gesture classification for the capture overlay: what a completed drag
 * means, decided from plain numbers so it stays JVM-testable.
 */
object OverlayGestures {

    /** A drag must cover this fraction of the view's height, straight down, to close. */
    const val CLOSE_SWIPE_FRACTION = 0.25f

    /** Above this duration a drag is a deliberate stroke, not a flick. */
    const val CLOSE_SWIPE_MAX_MS = 400L

    /**
     * A fast, mostly vertical, downward drag over a quarter of the view: close, not
     * select. A deliberate circling stroke is slower and rarely this straight, so the
     * two intents stay apart.
     */
    fun isCloseSwipe(
        viewHeight: Int,
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        elapsedMillis: Long,
    ): Boolean {
        val dy = upY - downY
        val dx = upX - downX
        return dy > viewHeight * CLOSE_SWIPE_FRACTION &&
            elapsedMillis <= CLOSE_SWIPE_MAX_MS &&
            dy > 2 * abs(dx)
    }
}
