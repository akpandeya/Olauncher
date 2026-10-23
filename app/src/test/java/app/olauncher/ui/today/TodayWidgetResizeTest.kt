package app.olauncher.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWidgetResizeTest {

    private fun range(default: Int, minResize: Int = 0, maxResize: Int = 0) =
        TodayWidgetResize.range(default, minResize, maxResize)

    // range()

    @Test
    fun `default is provider minHeight clamped to the 48dp floor`() {
        assertEquals(TodayWidgetResize.Range(48, 4096, 110), range(110))
        assertEquals(TodayWidgetResize.Range(48, 4096, 48), range(20))
    }

    @Test
    fun `unset minResizeHeight falls back to the 48dp floor`() {
        assertEquals(48, range(110).minDp)
        assertEquals(48, range(110, minResize = 0).minDp)
    }

    @Test
    fun `set minResizeHeight raises the floor even above the default`() {
        assertEquals(64, range(110, minResize = 64).minDp)
    }

    @Test
    fun `unset maxResizeHeight gets the generous default ceiling`() {
        assertEquals(TodayWidgetResize.NO_MAX_DP, range(110).maxDp)
    }

    @Test
    fun `set maxResizeHeight caps the ceiling and stays above the floor`() {
        assertEquals(240, range(110, maxResize = 240).maxDp)
        // A provider declaring max below min must not produce an inverted range.
        val odd = range(110, minResize = 200, maxResize = 100)
        assertTrue(odd.minDp <= odd.maxDp)
        assertEquals(odd.minDp, odd.maxDp)
    }

    // snap()

    @Test
    fun `snap rounds to the grid then clamps into the range`() {
        val r = range(110, minResize = 60, maxResize = 240)
        assertEquals(112, TodayWidgetResize.snap(110, r))   // rounds up to the 8dp grid
        assertEquals(60, TodayWidgetResize.snap(57, r))     // rounds to 56, then the 60 floor wins
        assertEquals(240, TodayWidgetResize.snap(900, r))   // ceiling wins
        assertEquals(64, TodayWidgetResize.snap(61, r))     // rounds up to the grid, still inside the range
    }

    @Test
    fun `snap keeps values already on the grid`() {
        val r = range(110)
        assertEquals(128, TodayWidgetResize.snap(128, r))
        assertEquals(48, TodayWidgetResize.snap(48, r))
    }

    // encode/decode round trip

    @Test
    fun `encode then decode preserves heights`() {
        val heights = mapOf(12 to 96, 7 to 128)
        assertEquals(heights, TodayWidgetResize.decode(TodayWidgetResize.encode(heights)))
    }

    @Test
    fun `encode is stable for an empty map`() {
        assertEquals("", TodayWidgetResize.encode(emptyMap()))
        assertEquals(emptyMap<Int, Int>(), TodayWidgetResize.decode(""))
    }

    @Test
    fun `decode skips malformed, negative and zero entries`() {
        val decoded = TodayWidgetResize.decode("12:96,bad,,-3:40,5:0,14:64,junk:20,:30,13:x")
        assertEquals(mapOf(12 to 96, 14 to 64), decoded)
        assertFalse(decoded.containsKey(-3))
    }

    @Test
    fun `encode is deterministic regardless of map order`() {
        assertEquals(TodayWidgetResize.encode(mapOf(2 to 64, 1 to 48)), TodayWidgetResize.encode(mapOf(1 to 48, 2 to 64)))
    }
}
