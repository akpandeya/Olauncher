package app.olauncher.helper

/**
 * Where an anchored popup menu goes relative to the row it hangs off.
 * Context-free so the decision can be unit tested.
 *
 * All values are window pixels. [visibleBottom] is the lowest pixel the menu may
 * use: the screen height minus the keyboard (IME) height, see [visibleBottom].
 */
object PopupPlacement {

    /** Lowest usable pixel: the screen height minus the keyboard height (0 when hidden). */
    fun visibleBottom(screenHeight: Int, imeBottom: Int): Int = screenHeight - maxOf(0, imeBottom)

    /**
     * True when the menu should open above the anchor: it does not fit between
     * the anchor and [visibleBottom], and there is more room above than below.
     */
    fun showsAbove(anchorTop: Int, anchorBottom: Int, visibleBottom: Int, menuHeight: Int): Boolean {
        val roomBelow = visibleBottom - anchorBottom
        val roomAbove = anchorTop
        return menuHeight > roomBelow && roomAbove > roomBelow
    }

    /** Top edge the menu needs so it ends at the anchor's top, never above the screen. */
    fun topWhenAbove(anchorTop: Int, menuHeight: Int): Int = maxOf(0, anchorTop - menuHeight)

    /**
     * Moves [top] so a menu of [menuHeight] stays between [visibleTop] and
     * [visibleBottom]. A menu taller than that area pins to [visibleTop].
     */
    fun clampTop(top: Int, menuHeight: Int, visibleTop: Int, visibleBottom: Int): Int =
        maxOf(visibleTop, minOf(top, visibleBottom - menuHeight))

    /** Top edge of the menu: below the anchor, or above it when [showsAbove], kept in the visible area. */
    fun menuTop(anchorTop: Int, anchorBottom: Int, visibleTop: Int, visibleBottom: Int, menuHeight: Int): Int {
        val top = if (showsAbove(anchorTop, anchorBottom, visibleBottom, menuHeight)) {
            topWhenAbove(anchorTop, menuHeight)
        } else {
            anchorBottom
        }
        return clampTop(top, menuHeight, visibleTop, visibleBottom)
    }
}
