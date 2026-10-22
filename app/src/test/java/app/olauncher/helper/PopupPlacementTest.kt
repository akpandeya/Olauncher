package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPlacementTest {

    @Test
    fun opensBelowWhenItFits() {
        assertFalse(PopupPlacement.showsAbove(anchorTop = 200, anchorBottom = 260, screenHeight = 2000, menuHeight = 700))
    }

    @Test
    fun flipsAboveNearTheBottom() {
        assertTrue(PopupPlacement.showsAbove(anchorTop = 1700, anchorBottom = 1760, screenHeight = 2000, menuHeight = 700))
    }

    @Test
    fun staysBelowWhenAboveHasLessRoom() {
        // Short landscape screen: neither side fits, the larger side (below) wins.
        assertFalse(PopupPlacement.showsAbove(anchorTop = 100, anchorBottom = 160, screenHeight = 800, menuHeight = 900))
    }

    @Test
    fun exactFitBelowDoesNotFlip() {
        assertFalse(PopupPlacement.showsAbove(anchorTop = 1000, anchorBottom = 1300, screenHeight = 2000, menuHeight = 700))
    }

    @Test
    fun topIsClampedToScreen() {
        assertEquals(0, PopupPlacement.topWhenAbove(anchorTop = 300, menuHeight = 700))
        assertEquals(1000, PopupPlacement.topWhenAbove(anchorTop = 1700, menuHeight = 700))
    }
}
