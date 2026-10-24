package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class DialogListSizingTest {
    @Test
    fun capsAtHalfTheScreenByDefault() {
        assertEquals(1170, DialogListSizing.maxListHeightPx(2340))
    }

    @Test
    fun usesGivenFraction() {
        assertEquals(720, DialogListSizing.maxListHeightPx(2400, 0.3f))
    }

    @Test
    fun neverNegative() {
        assertEquals(0, DialogListSizing.maxListHeightPx(-100))
    }

    @Test
    fun applyLabelShowsCountOnlyWhenPicked() {
        assertEquals("Apply", DialogListSizing.applyLabel("Apply", 0))
        assertEquals("Apply (7)", DialogListSizing.applyLabel("Apply", 7))
    }
}
