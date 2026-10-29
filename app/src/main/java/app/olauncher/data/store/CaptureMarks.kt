package app.olauncher.data.store

/**
 * How a captured word is marked in the reader and the circle-to-search overlay.
 *
 * The three states must stay visually distinct: a pending word carries a translucent
 * background fill, a saved word an underline, a plain word no span at all. A fill has
 * its own opacity and ignores the font setting, so the pending mark stays visible even
 * when the theme resolves to a bold face — bold-on-bold would be invisible.
 */
object CaptureMarks {

    enum class Mark { PLAIN, PENDING, SAVED }

    /** Saved wins: a saved word cannot be pending (ReaderSelection refuses it). */
    fun markOf(isSaved: Boolean, isPending: Boolean): Mark = when {
        isSaved -> Mark.SAVED
        isPending -> Mark.PENDING
        else -> Mark.PLAIN
    }

    /** ARGB of the pending fill: [base] (accent, or primary when there is none) at [alpha]. */
    fun pendingFill(base: Int, alpha: Int = PENDING_ALPHA): Int = (alpha shl 24) or (base and 0x00FFFFFF)

    /** 25 % — translucent enough to keep the text readable, strong enough to show on e-ink. */
    const val PENDING_ALPHA = 0x40
}
