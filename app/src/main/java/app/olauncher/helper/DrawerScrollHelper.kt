package app.olauncher.helper

object DrawerScrollHelper {
    /**
     * Determines whether the list is currently positioned at the search row end of the drawer.
     * When search is at the bottom (drawerBottomSearch = true), the search row sits at the bottom,
     * so being at search means we cannot scroll down any further (!canScrollDown).
     * When search is at the top (drawerBottomSearch = false), being at search means we cannot
     * scroll up any further (!canScrollUp).
     */
    fun isAtSearch(drawerBottomSearch: Boolean, canScrollUp: Boolean, canScrollDown: Boolean): Boolean {
        return if (drawerBottomSearch) !canScrollDown else !canScrollUp
    }

    /**
     * Determines whether the list is currently positioned at the far end (away from search).
     */
    fun isAtFarEnd(drawerBottomSearch: Boolean, canScrollUp: Boolean, canScrollDown: Boolean): Boolean {
        return if (drawerBottomSearch) !canScrollUp else !canScrollDown
    }

    /**
     * Bottom padding for the drawer root so the bottom search row sits just above the keyboard.
     * [imeBottom] is the IME inset in px (0 when the keyboard is hidden). The search row's own
     * [searchBottomMargin] keeps it clear of the navigation bar when the keyboard is hidden;
     * the keyboard already covers that area, so the margin is taken off the padding.
     * With search at the top the drawer is not padded.
     */
    fun imeBottomPadding(drawerBottomSearch: Boolean, imeBottom: Int, searchBottomMargin: Int): Int {
        if (!drawerBottomSearch || imeBottom <= 0) return 0
        return (imeBottom - searchBottomMargin.coerceAtLeast(0)).coerceAtLeast(0)
    }
}
