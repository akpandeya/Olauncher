package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupOriginTest {

    @Test
    fun leftSideTapPivotsNearCardStart() {
        // Anchor centred at x=150, card from x=100, 680 wide.
        assertEquals(50f, PopupOrigin.pivotX(150, 100, 680), 0f)
    }

    @Test
    fun rightSideTapPivotsNearCardEnd() {
        // Card from x=300 to 980; anchor centred at x=950.
        assertEquals(650f, PopupOrigin.pivotX(950, 300, 680), 0f)
    }

    @Test
    fun pivotYIsRelativeToCardTop() {
        assertEquals(40f, PopupOrigin.pivotY(540, 500, 400), 0f)
    }

    @Test
    fun cardAboveAnchorPivotsAtBottomEdge() {
        assertEquals(400f, PopupOrigin.pivotY(900, 100, 400), 0f)
    }

    @Test
    fun pivotStaysOnCard() {
        assertEquals(0f, PopupOrigin.pivotX(10, 100, 680), 0f)
        assertEquals(0f, PopupOrigin.pivotY(10, 100, 400), 0f)
    }
}
