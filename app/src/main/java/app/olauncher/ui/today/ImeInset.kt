package app.olauncher.ui.today

/** Context-free rule for lifting the Today page above the keyboard. */
object ImeInset {
    /** Bottom padding for the scroll view: the keyboard height while it shows, else none. */
    fun bottomPadding(imeVisible: Boolean, imeBottomPx: Int): Int =
        if (imeVisible) imeBottomPx.coerceAtLeast(0) else 0
}
