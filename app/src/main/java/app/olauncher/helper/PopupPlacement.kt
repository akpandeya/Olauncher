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

    /** Height the menu window gets: its measured [contentHeight], but never taller than the visible area (it scrolls then). */
    fun fitHeight(contentHeight: Int, visibleTop: Int, visibleBottom: Int): Int =
        minOf(contentHeight, maxOf(0, visibleBottom - visibleTop))

    /**
     * Left edge of a menu of [menuWidth]: its end edge lines up with the anchor's end edge
     * (right in left-to-right, left in right-to-left), kept [margin] inside a screen of [screenWidth].
     */
    fun menuLeft(anchorLeft: Int, anchorRight: Int, menuWidth: Int, screenWidth: Int, margin: Int, isRtl: Boolean): Int {
        val left = if (isRtl) anchorLeft else anchorRight - menuWidth
        val maxLeft = screenWidth - margin - menuWidth
        return if (maxLeft < margin) maxOf(0, (screenWidth - menuWidth) / 2) else left.coerceIn(margin, maxLeft)
    }

    /** Share of the screen width the menu takes. */
    const val MENU_WIDTH_FRACTION = 0.68f

    /** Menu width: [MENU_WIDTH_FRACTION] of [screenWidth], never wider than the screen minus both [margin]s. */
    fun menuWidth(screenWidth: Int, margin: Int): Int =
        minOf((screenWidth * MENU_WIDTH_FRACTION).toInt(), maxOf(0, screenWidth - 2 * margin))

    /**
     * Left edge of a menu that sits on the same side of the screen as the anchor: start edges line up
     * when the anchor's centre is in the left half, end edges when it is in the right half.
     * Uses window coordinates, so right-to-left layouts mirror on their own. Kept [margin] inside the screen.
     */
    fun menuLeftBySide(anchorLeft: Int, anchorRight: Int, menuWidth: Int, screenWidth: Int, margin: Int): Int {
        val onLeftHalf = anchorLeft + anchorRight < screenWidth
        val left = if (onLeftHalf) anchorLeft else anchorRight - menuWidth
        val maxLeft = screenWidth - margin - menuWidth
        return if (maxLeft < margin) maxOf(0, (screenWidth - menuWidth) / 2) else left.coerceIn(margin, maxLeft)
    }
}
