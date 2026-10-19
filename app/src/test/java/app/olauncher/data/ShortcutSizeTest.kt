package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutSizeTest {

    @Test
    fun `clamp coerces negative values to SMALL`() {
        assertEquals(ShortcutSize.SMALL, ShortcutSize.clamp(Int.MIN_VALUE))
        assertEquals(ShortcutSize.SMALL, ShortcutSize.clamp(-100))
        assertEquals(ShortcutSize.SMALL, ShortcutSize.clamp(-1))
    }

    @Test
    fun `clamp coerces values above LARGE to LARGE`() {
        assertEquals(ShortcutSize.LARGE, ShortcutSize.clamp(3))
        assertEquals(ShortcutSize.LARGE, ShortcutSize.clamp(10))
        assertEquals(ShortcutSize.LARGE, ShortcutSize.clamp(Int.MAX_VALUE))
    }

    @Test
    fun `clamp preserves values between SMALL and LARGE inclusive`() {
        for (step in ShortcutSize.SMALL..ShortcutSize.LARGE) {
            assertEquals(step, ShortcutSize.clamp(step))
        }
    }

    @Test
    fun `default size step is MEDIUM`() {
        assertEquals(ShortcutSize.MEDIUM, ShortcutSize.DEFAULT)
        assertEquals(ShortcutSize.MEDIUM, ShortcutSize.DEFAULT_STEP)
        assertEquals(ShortcutSize.MEDIUM, ShortcutSize.clamp(ShortcutSize.DEFAULT))
    }

    @Test
    fun `viewSizeDp returns 40 for SMALL, 48 for MEDIUM, 56 for LARGE`() {
        assertEquals(40, ShortcutSize.viewSizeDp(ShortcutSize.SMALL))
        assertEquals(48, ShortcutSize.viewSizeDp(ShortcutSize.MEDIUM))
        assertEquals(56, ShortcutSize.viewSizeDp(ShortcutSize.LARGE))
    }

    @Test
    fun `viewSizeDp clamps out of bounds steps`() {
        assertEquals(40, ShortcutSize.viewSizeDp(-1))
        assertEquals(56, ShortcutSize.viewSizeDp(5))
    }

    @Test
    fun `columnAllowanceDp returns 56 for SMALL, 56 for MEDIUM, 64 for LARGE`() {
        assertEquals(56, ShortcutSize.columnAllowanceDp(ShortcutSize.SMALL))
        assertEquals(56, ShortcutSize.columnAllowanceDp(ShortcutSize.MEDIUM))
        assertEquals(64, ShortcutSize.columnAllowanceDp(ShortcutSize.LARGE))
    }

    @Test
    fun `columnAllowanceDp clamps out of bounds steps`() {
        assertEquals(56, ShortcutSize.columnAllowanceDp(-1))
        assertEquals(64, ShortcutSize.columnAllowanceDp(10))
    }
}
