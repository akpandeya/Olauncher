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
}
