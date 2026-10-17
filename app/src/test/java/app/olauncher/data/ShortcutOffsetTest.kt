package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutOffsetTest {

    @Test
    fun `clamp coerces negative values to 0`() {
        assertEquals(0, ShortcutOffset.clamp(Int.MIN_VALUE))
        assertEquals(0, ShortcutOffset.clamp(-100))
        assertEquals(0, ShortcutOffset.clamp(-1))
    }

    @Test
    fun `clamp coerces values above 6 to 6`() {
        assertEquals(6, ShortcutOffset.clamp(7))
        assertEquals(6, ShortcutOffset.clamp(8))
        assertEquals(6, ShortcutOffset.clamp(100))
        assertEquals(6, ShortcutOffset.clamp(Int.MAX_VALUE))
    }

    @Test
    fun `clamp preserves values between 0 and 6 inclusive`() {
        for (step in 0..6) {
            assertEquals(step, ShortcutOffset.clamp(step))
        }
    }

    @Test
    fun `default step is 0`() {
        assertEquals(0, ShortcutOffset.DEFAULT_STEP)
        assertEquals(0, ShortcutOffset.clamp(ShortcutOffset.DEFAULT_STEP))
    }

    @Test
    fun `bottomMarginDp calculates 48dp base plus 24dp per step`() {
        assertEquals(48, ShortcutOffset.bottomMarginDp(0))
        assertEquals(72, ShortcutOffset.bottomMarginDp(1))
        assertEquals(96, ShortcutOffset.bottomMarginDp(2))
        assertEquals(120, ShortcutOffset.bottomMarginDp(3))
        assertEquals(144, ShortcutOffset.bottomMarginDp(4))
        assertEquals(168, ShortcutOffset.bottomMarginDp(5))
        assertEquals(192, ShortcutOffset.bottomMarginDp(6))
    }

    @Test
    fun `bottomMarginDp clamps out of bound steps`() {
        assertEquals(48, ShortcutOffset.bottomMarginDp(-1))
        assertEquals(192, ShortcutOffset.bottomMarginDp(7))
    }

    @Test
    fun `format formats step 0 as 0 and positive steps with plus prefix`() {
        assertEquals("0", ShortcutOffset.format(0))
        assertEquals("+1", ShortcutOffset.format(1))
        assertEquals("+2", ShortcutOffset.format(2))
        assertEquals("+3", ShortcutOffset.format(3))
        assertEquals("+4", ShortcutOffset.format(4))
        assertEquals("+5", ShortcutOffset.format(5))
        assertEquals("+6", ShortcutOffset.format(6))
    }
}
