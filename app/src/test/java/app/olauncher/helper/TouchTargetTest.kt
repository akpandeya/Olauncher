package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchTargetTest {

    @Test
    fun `extraPaddingPx returns 0 when view size equals minPx`() {
        assertEquals(0, TouchTarget.extraPaddingPx(viewSizePx = 48, minPx = 48))
        assertEquals(0, TouchTarget.extraPaddingPx(viewSizePx = 100, minPx = 100))
        assertEquals(0, TouchTarget.extraPaddingPx(viewSizePx = 0, minPx = 0))
    }

    @Test
    fun `extraPaddingPx returns 0 when view size is larger than minPx`() {
        assertEquals(0, TouchTarget.extraPaddingPx(viewSizePx = 56, minPx = 48))
        assertEquals(0, TouchTarget.extraPaddingPx(viewSizePx = 64, minPx = 48))
        assertEquals(0, TouchTarget.extraPaddingPx(viewSizePx = 200, minPx = 48))
    }

    @Test
    fun `extraPaddingPx returns half the difference when view size is smaller than minPx`() {
        // Standard case: 40dp icon in 48dp target -> 4dp padding per side
        assertEquals(4, TouchTarget.extraPaddingPx(viewSizePx = 40, minPx = 48))
        assertEquals(8, TouchTarget.extraPaddingPx(viewSizePx = 32, minPx = 48))
        assertEquals(12, TouchTarget.extraPaddingPx(viewSizePx = 24, minPx = 48))
        assertEquals(24, TouchTarget.extraPaddingPx(viewSizePx = 0, minPx = 48))
    }

    @Test
    fun `extraPaddingPx performs integer division when difference is odd`() {
        // Difference is 7: 7 / 2 = 3
        assertEquals(3, TouchTarget.extraPaddingPx(viewSizePx = 41, minPx = 48))
        // Difference is 1: 1 / 2 = 0
        assertEquals(0, TouchTarget.extraPaddingPx(viewSizePx = 47, minPx = 48))
    }

    @Test
    fun `targetSizePx expands small views to at least minPx`() {
        assertEquals(48, TouchTarget.targetSizePx(viewSizePx = 40, minPx = 48))
        assertEquals(48, TouchTarget.targetSizePx(viewSizePx = 48, minPx = 48))
        assertEquals(56, TouchTarget.targetSizePx(viewSizePx = 56, minPx = 48))
    }

    @Test
    fun `MIN_TARGET_DP is 48`() {
        assertEquals(48, TouchTarget.MIN_TARGET_DP)
    }
}
