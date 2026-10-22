package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPlacementTest {

    @Test
    fun opensBelowWhenItFits() {
        assertFalse(PopupPlacement.showsAbove(anchorTop = 200, anchorBottom = 260, visibleBottom = 2000, menuHeight = 700))
    }

    @Test
    fun flipsAboveNearTheBottom() {
        assertTrue(PopupPlacement.showsAbove(anchorTop = 1700, anchorBottom = 1760, visibleBottom = 2000, menuHeight = 700))
    }

    @Test
    fun staysBelowWhenAboveHasLessRoom() {
        // Short landscape screen: neither side fits, the larger side (below) wins.
        assertFalse(PopupPlacement.showsAbove(anchorTop = 100, anchorBottom = 160, visibleBottom = 800, menuHeight = 900))
    }

    @Test
    fun exactFitBelowDoesNotFlip() {
        assertFalse(PopupPlacement.showsAbove(anchorTop = 1000, anchorBottom = 1300, visibleBottom = 2000, menuHeight = 700))
    }

    @Test
    fun topIsClampedToScreen() {
        assertEquals(0, PopupPlacement.topWhenAbove(anchorTop = 300, menuHeight = 700))
        assertEquals(1000, PopupPlacement.topWhenAbove(anchorTop = 1700, menuHeight = 700))
    }

    @Test
    fun visibleBottomSubtractsTheKeyboard() {
        assertEquals(2000, PopupPlacement.visibleBottom(screenHeight = 2000, imeBottom = 0))
        assertEquals(1130, PopupPlacement.visibleBottom(screenHeight = 2000, imeBottom = 870))
        assertEquals(2000, PopupPlacement.visibleBottom(screenHeight = 2000, imeBottom = -5))
    }

    @Test
    fun keyboardUpFlipsAboveAnchorJustOverIt() {
        // Keyboard 870px tall: visible area ends at 1130. Row sits just above it.
        val visibleBottom = PopupPlacement.visibleBottom(screenHeight = 2000, imeBottom = 870)
        assertTrue(PopupPlacement.showsAbove(anchorTop = 1060, anchorBottom = 1120, visibleBottom = visibleBottom, menuHeight = 700))
        // The same row with the keyboard hidden opens below, as before.
        assertFalse(PopupPlacement.showsAbove(anchorTop = 1060, anchorBottom = 1120, visibleBottom = 2000, menuHeight = 700))
        assertEquals(360, PopupPlacement.menuTop(1060, 1120, visibleTop = 0, visibleBottom = visibleBottom, menuHeight = 700))
    }

    @Test
    fun keyboardHiddenKeepsOldResults() {
        val visibleBottom = PopupPlacement.visibleBottom(screenHeight = 2000, imeBottom = 0)
        // Fits below: menu starts at the anchor bottom.
        assertEquals(260, PopupPlacement.menuTop(200, 260, visibleTop = 0, visibleBottom = visibleBottom, menuHeight = 700))
        // Near the bottom: flips, top matches topWhenAbove.
        assertEquals(
            PopupPlacement.topWhenAbove(anchorTop = 1700, menuHeight = 700),
            PopupPlacement.menuTop(1700, 1760, visibleTop = 0, visibleBottom = visibleBottom, menuHeight = 700),
        )
    }

    @Test
    fun clampStaysInsideVisibleArea() {
        // Too low: pulled up so the bottom edge is at visibleBottom.
        assertEquals(430, PopupPlacement.clampTop(top = 900, menuHeight = 700, visibleTop = 75, visibleBottom = 1130))
        // Too high: pushed down to visibleTop.
        assertEquals(75, PopupPlacement.clampTop(top = 10, menuHeight = 700, visibleTop = 75, visibleBottom = 1130))
        // Already inside: unchanged.
        assertEquals(300, PopupPlacement.clampTop(top = 300, menuHeight = 700, visibleTop = 75, visibleBottom = 1130))
    }

    @Test
    fun menuTallerThanVisibleAreaPinsToVisibleTop() {
        assertEquals(75, PopupPlacement.clampTop(top = 500, menuHeight = 1200, visibleTop = 75, visibleBottom = 1130))
        assertEquals(75, PopupPlacement.menuTop(600, 660, visibleTop = 75, visibleBottom = 1130, menuHeight = 1200))
    }

    @Test
    fun belowPlacementIsMovedUpToClearTheKeyboard() {
        // Neither side fits; below is larger, so it stays below but is lifted to end at visibleBottom.
        assertEquals(110, PopupPlacement.menuTop(100, 160, visibleTop = 0, visibleBottom = 710, menuHeight = 600))
    }
}
