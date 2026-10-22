package app.olauncher.helper

/**
 * Where an anchored popup menu goes relative to the row it hangs off.
 * Context-free so the decision can be unit tested.
 */
object PopupPlacement {

    /**
     * True when the menu should open above the anchor: it does not fit below
     * the anchor, and there is more room above than below.
     */
    fun showsAbove(anchorTop: Int, anchorBottom: Int, screenHeight: Int, menuHeight: Int): Boolean {
        val roomBelow = screenHeight - anchorBottom
        val roomAbove = anchorTop
        return menuHeight > roomBelow && roomAbove > roomBelow
    }

    /** Top edge the menu needs so it ends at the anchor's top, never above the screen. */
    fun topWhenAbove(anchorTop: Int, menuHeight: Int): Int = maxOf(0, anchorTop - menuHeight)
}
