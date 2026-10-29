package app.olauncher.data.store

/**
 * Where the capture overlay's meaning card goes relative to the picked word (#592): the
 * card sits at the bottom of the screen by default and moves to the top when the word
 * lives in the bottom half — and it never covers the word box when that can be avoided.
 *
 * Context-free and JVM-testable. The caller draws the card full-width, so only the
 * vertical placement is decided here: the returned box mirrors the word box's horizontal
 * span, and the caller only reads [ScreenBox.top].
 */
object MeaningCardPlacement {

    /** Gap between the card and the screen edge, in pixels. */
    const val MARGIN_PX = 8

    /**
     * The card's placement box for [wordBox] on a screen [screenHeight] px tall with a
     * [cardHeight] px tall card, leaving [bottomInset] px for system bars at the bottom.
     * Bottom-aligned with [MARGIN_PX] above [bottomInset] unless the word box's center
     * is in the bottom half of the screen, then top-aligned. When the preferred placement
     * would cover the word box, the other side is used; when both would (a card taller
     * than the free space on either side), the placement with the smaller vertical overlap
     * with the word box wins.
     */
    fun place(
        wordBox: ScreenBox,
        screenHeight: Int,
        cardHeight: Int,
        bottomInset: Int = 0,
    ): ScreenBox {
        val bottomMargin = MARGIN_PX + maxOf(0, bottomInset)
        val bottom = ScreenBox(
            wordBox.left,
            screenHeight - bottomMargin - cardHeight,
            wordBox.right,
            screenHeight - bottomMargin,
        )
        val top = ScreenBox(wordBox.left, MARGIN_PX, wordBox.right, MARGIN_PX + cardHeight)
        // Exactly on the midline counts as the bottom half — the card moves out of the way.
        val preferBottom = wordBox.top + wordBox.height / 2 < screenHeight / 2
        val (preferred, other) = if (preferBottom) bottom to top else top to bottom
        val preferredOverlap = overlapWithWord(preferred, wordBox)
        if (preferredOverlap == 0) return preferred
        val otherOverlap = overlapWithWord(other, wordBox)
        return if (otherOverlap < preferredOverlap) other else preferred
    }

    /** Vertical overlap between the card band and the word box; the card is full-width. */
    private fun overlapWithWord(card: ScreenBox, word: ScreenBox): Int =
        maxOf(0, minOf(card.bottom, word.bottom) - maxOf(card.top, word.top))
}
