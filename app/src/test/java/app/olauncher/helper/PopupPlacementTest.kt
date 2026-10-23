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

    @Test
    fun fitHeightKeepsMeasuredHeightWhenItFits() {
        assertEquals(640, PopupPlacement.fitHeight(contentHeight = 640, visibleTop = 0, visibleBottom = 1130))
    }

    @Test
    fun fitHeightCapsTallCardToVisibleArea() {
        // Large font: the card scrolls instead of running under the keyboard.
        assertEquals(1055, PopupPlacement.fitHeight(contentHeight = 1500, visibleTop = 75, visibleBottom = 1130))
        assertEquals(0, PopupPlacement.fitHeight(contentHeight = 300, visibleTop = 500, visibleBottom = 400))
    }

    @Test
    fun menuLeftAlignsEndEdgeWithAnchorInLeftToRight() {
        assertEquals(530, PopupPlacement.menuLeft(anchorLeft = 100, anchorRight = 1030, menuWidth = 500, screenWidth = 1080, margin = 24, isRtl = false))
    }

    @Test
    fun menuLeftAlignsWithAnchorStartInRightToLeft() {
        assertEquals(130, PopupPlacement.menuLeft(anchorLeft = 130, anchorRight = 1030, menuWidth = 500, screenWidth = 1080, margin = 24, isRtl = true))
    }

    @Test
    fun menuLeftStaysInsideScreenMargins() {
        // Narrow anchor near the left edge: pushed right to the margin.
        assertEquals(24, PopupPlacement.menuLeft(anchorLeft = 0, anchorRight = 200, menuWidth = 500, screenWidth = 1080, margin = 24, isRtl = false))
        // RTL anchor near the right edge: pulled left so the end stays inside the margin.
        assertEquals(556, PopupPlacement.menuLeft(anchorLeft = 900, anchorRight = 1080, menuWidth = 500, screenWidth = 1080, margin = 24, isRtl = true))
    }

    @Test
    fun menuLeftCentresCardWiderThanTheMarginsAllow() {
        assertEquals(10, PopupPlacement.menuLeft(anchorLeft = 0, anchorRight = 300, menuWidth = 1060, screenWidth = 1080, margin = 24, isRtl = false))
    }

    @Test
    fun menuWidthIsSixtyEightPercentOfTheScreen() {
        assertEquals(734, PopupPlacement.menuWidth(screenWidth = 1080, margin = 24))
    }

    @Test
    fun menuWidthNeverExceedsScreenMinusMargins() {
        assertEquals(60, PopupPlacement.menuWidth(screenWidth = 100, margin = 20))
        assertEquals(0, PopupPlacement.menuWidth(screenWidth = 40, margin = 30))
    }

    @Test
    fun menuLeftBySideAlignsStartWithAnchorOnTheLeftHalf() {
        assertEquals(60, PopupPlacement.menuLeftBySide(anchorLeft = 60, anchorRight = 400, menuWidth = 734, screenWidth = 1080, margin = 24))
    }

    @Test
    fun menuLeftBySideAlignsEndWithAnchorOnTheRightHalf() {
        // Right-to-left home apps sit on the right: end edges line up at 1020.
        assertEquals(286, PopupPlacement.menuLeftBySide(anchorLeft = 700, anchorRight = 1020, menuWidth = 734, screenWidth = 1080, margin = 24))
    }

    @Test
    fun menuLeftBySideStaysInsideMargins() {
        assertEquals(24, PopupPlacement.menuLeftBySide(anchorLeft = 0, anchorRight = 200, menuWidth = 734, screenWidth = 1080, margin = 24))
        assertEquals(322, PopupPlacement.menuLeftBySide(anchorLeft = 900, anchorRight = 1080, menuWidth = 734, screenWidth = 1080, margin = 24))
    }
}
