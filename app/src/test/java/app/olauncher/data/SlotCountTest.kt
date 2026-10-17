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
    fun `clamp coerces values above 8 to 8`() {
        assertEquals(8, SlotCount.clamp(9))
        assertEquals(8, SlotCount.clamp(10))
        assertEquals(8, SlotCount.clamp(100))
        assertEquals(8, SlotCount.clamp(Int.MAX_VALUE))
    }

    @Test
    fun `clamp preserves values between 3 and 8 inclusive`() {
        for (v in 3..8) {
            assertEquals(v, SlotCount.clamp(v))
        }
    }

    @Test
    fun `default slot count is 6`() {
        assertEquals(6, SlotCount.DEFAULT)
        assertEquals(6, SlotCount.clamp(SlotCount.DEFAULT))
    }
}
