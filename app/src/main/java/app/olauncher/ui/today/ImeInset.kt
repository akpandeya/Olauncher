package app.olauncher.ui.today

/** Context-free rule for lifting pages above the keyboard. */
object ImeInset {
    /** Bottom padding for the scroll view: the keyboard height while it shows, else none. */
    fun bottomPadding(imeVisible: Boolean, imeBottomPx: Int): Int =
        if (imeVisible) imeBottomPx.coerceAtLeast(0) else 0

    /**
     * Bottom padding for a pinned bottom row (a composer) whose hidden-keyboard
     * base clears the navigation bar (callers pass the live navigation-bars
     * inset): while the keyboard is visible the row rides the IME inset — the
     * keyboard covers the nav bar, so the base must not stack on it — and the
     * base returns once it hides.
     */
    fun pinnedBottomPadding(imeVisible: Boolean, imeBottomPx: Int, basePx: Int): Int =
        if (imeVisible) imeBottomPx.coerceAtLeast(0) else basePx

    /**
     * How far to raise a bottom-anchored dialog window so it clears the keyboard.
     * The window already sits above the navigation bar, which the keyboard covers,
     * so that part is not lifted twice.
     */
    fun windowLift(imeVisible: Boolean, imeBottomPx: Int, navBottomPx: Int): Int =
        if (imeVisible) (imeBottomPx - navBottomPx.coerceAtLeast(0)).coerceAtLeast(0) else 0

    /** Sheet content that never scrolls (the action row below the scroll view). */
    private const val NON_SCROLL_DP = 180
    /** Smallest scroll area a keyboard-lifted sheet still gets. */
    private const val MIN_SCROLL_DP = 100

    /** Lift for the window and cap for the sheet's scroll area at one keyboard state. */
    data class Ride(val lift: Int, val scrollCap: Int)

    /**
     * The [Ride] for one insets dispatch. Only meaningful on a keyboard visibility
     * change: the reported overlap is measured against the dialog's own window,
     * which the lift itself moves, so recomputing it on every dispatch oscillates.
     */
    fun ride(
        imeVisible: Boolean,
        imeBottomPx: Int,
        navBottomPx: Int,
        statusTopPx: Int,
        screenHeightPx: Int,
        density: Float,
    ): Ride {
        val scrollCap = if (imeVisible) {
            val min = (density * MIN_SCROLL_DP).toInt()
            (screenHeightPx - imeBottomPx - statusTopPx - (density * NON_SCROLL_DP).toInt()).coerceAtLeast(min)
        } else 0
        return Ride(windowLift(imeVisible, imeBottomPx, navBottomPx), scrollCap)
    }
}
