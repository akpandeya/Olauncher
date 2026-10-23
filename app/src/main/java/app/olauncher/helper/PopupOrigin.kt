package app.olauncher.helper

/** Where a popup card grows from: the tapped view's centre, in card coordinates. Context-free for unit tests. */
object PopupOrigin {

    const val START_SCALE = 0.9f

    /** Pivot X inside a card at [cardLeft] of [cardWidth], for a tap centred at window x [anchorCenterX]; kept on the card. */
    fun pivotX(anchorCenterX: Int, cardLeft: Int, cardWidth: Int): Float =
        (anchorCenterX - cardLeft).coerceIn(0, maxOf(0, cardWidth)).toFloat()

    /** Pivot Y inside a card at [cardTop] of [cardHeight], for a tap centred at window y [anchorCenterY]; kept on the card. */
    fun pivotY(anchorCenterY: Int, cardTop: Int, cardHeight: Int): Float =
        (anchorCenterY - cardTop).coerceIn(0, maxOf(0, cardHeight)).toFloat()
}
