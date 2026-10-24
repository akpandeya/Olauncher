package app.olauncher.helper

/** Context-free sizing for the scrolling list inside app dialogs. */
object DialogListSizing {
    /** Share of the screen height a dialog list may use, so title and buttons stay on screen. */
    const val MAX_SCREEN_FRACTION = 0.5f

    /** Tallest the list may be; never negative. */
    fun maxListHeightPx(screenHeightPx: Int, fraction: Float = MAX_SCREEN_FRACTION): Int =
        (screenHeightPx * fraction).toInt().coerceAtLeast(0)

    /** Apply button text: plain label at zero picks, label with the count otherwise. */
    fun applyLabel(base: String, picked: Int): String =
        if (picked > 0) "$base ($picked)" else base
}
