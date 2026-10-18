package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SlotCountTest {

    @Test
    fun `clamp coerces values below 3 to 3`() {
        assertEquals(3, SlotCount.clamp(Int.MIN_VALUE))
        assertEquals(3, SlotCount.clamp(-100))
        assertEquals(3, SlotCount.clamp(-1))
        assertEquals(3, SlotCount.clamp(0))
        assertEquals(3, SlotCount.clamp(1))
        assertEquals(3, SlotCount.clamp(2))
    }

    @Test
    fun `clamp coerces values above 12 to 12`() {
        assertEquals(12, SlotCount.clamp(13))
        assertEquals(12, SlotCount.clamp(14))
        assertEquals(12, SlotCount.clamp(100))
        assertEquals(12, SlotCount.clamp(Int.MAX_VALUE))
    }

    @Test
    fun `clamp preserves values between 3 and 12 inclusive`() {
        for (v in 3..12) {
            assertEquals(v, SlotCount.clamp(v))
        }
    }

    @Test
    fun `default slot count is 6`() {
        assertEquals(6, SlotCount.DEFAULT)
        assertEquals(6, SlotCount.clamp(SlotCount.DEFAULT))
    }

    @Test
    fun `fitting calculates slot count that fits on screen`() {
        assertEquals(6, SlotCount.fitting(6, 700, 48, 48))
        assertEquals(7, SlotCount.fitting(12, 600, 56, 48))
        assertEquals(3, SlotCount.fitting(12, 200, 56, 48))
    }
}
