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
}
