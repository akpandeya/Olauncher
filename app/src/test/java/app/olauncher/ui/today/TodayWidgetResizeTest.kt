package app.olauncher.ui.today

import org.junit.Assert.assertEquals
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
    fun `encode then decode preserves sizes`() {
        val sizes = mapOf(12 to TodayWidgetResize.Size(2, 96), 7 to TodayWidgetResize.Size(null, 128))
        assertEquals(sizes, TodayWidgetResize.decode(TodayWidgetResize.encode(sizes)))
    }

    @Test
    fun `encode is stable for an empty map`() {
        assertEquals("", TodayWidgetResize.encode(emptyMap()))
        assertEquals(emptyMap<Int, TodayWidgetResize.Size>(), TodayWidgetResize.decode(""))
    }

    @Test
    fun `decode skips malformed, negative and zero entries`() {
        val decoded = TodayWidgetResize.decode("12:96,bad,,-3:40,5:0,14:2:64,junk:20,:30,13:x")
        assertEquals(mapOf(12 to TodayWidgetResize.Size(null, 96), 14 to TodayWidgetResize.Size(2, 64)), decoded)
    }

    @Test
    fun `legacy height-only entries decode as full width`() {
        assertEquals(
            mapOf(5 to TodayWidgetResize.Size(null, 96)),
            TodayWidgetResize.decode("5:96"),
        )
    }

    @Test
    fun `width spans outside the grid degrade to full width`() {
        assertEquals(
            mapOf(5 to TodayWidgetResize.Size(null, 96), 6 to TodayWidgetResize.Size(null, 64)),
            TodayWidgetResize.decode("5:0:96,6:9:64"),
        )
    }

    @Test
    fun `encode is deterministic regardless of map order`() {
        assertEquals(
            TodayWidgetResize.encode(mapOf(2 to TodayWidgetResize.Size(1, 64), 1 to TodayWidgetResize.Size(null, 48))),
            TodayWidgetResize.encode(mapOf(1 to TodayWidgetResize.Size(null, 48), 2 to TodayWidgetResize.Size(1, 64))),
        )
    }

    // span range and span snapping

    @Test
    fun `unset width limits give the full column range`() {
        assertEquals(TodayWidgetResize.SpanRange(1, 4), TodayWidgetResize.spanRange(0, 0, 95f))
    }

    @Test
    fun `provider width limits translate into columns`() {
        // 100dp cell: minResizeWidth 150 -> at least 2 columns; maxResizeWidth 250 -> at most 2
        assertEquals(TodayWidgetResize.SpanRange(2, 2), TodayWidgetResize.spanRange(150, 250, 100f))
        assertEquals(TodayWidgetResize.SpanRange(1, 2), TodayWidgetResize.spanRange(0, 250, 100f))
        assertEquals(TodayWidgetResize.SpanRange(2, 4), TodayWidgetResize.spanRange(150, 0, 100f))
    }

    @Test
    fun `provider limits beyond the grid are capped at the column count`() {
        assertEquals(TodayWidgetResize.SpanRange(1, 4), TodayWidgetResize.spanRange(0, 4000, 100f))
    }

    @Test
    fun `a minimum wider than the grid collapses to the last column`() {
        assertEquals(TodayWidgetResize.SpanRange(4, 4), TodayWidgetResize.spanRange(1000, 0, 100f))
    }

    @Test
    fun `spanFor rounds to the nearest column and clamps into the range`() {
        val range = TodayWidgetResize.SpanRange(1, 3)
        assertEquals(2, TodayWidgetResize.spanFor(190f, 100f, range))
        assertEquals(3, TodayWidgetResize.spanFor(900f, 100f, range))
        assertEquals(1, TodayWidgetResize.spanFor(10f, 100f, range))
    }
}
