package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCounterTest {

    @Test
    fun `empty timestamps returns 0`() {
        assertEquals(0, countOpens(emptyList(), 2000L))
        assertEquals(0, countOpens(emptyList()))
    }

    @Test
    fun `single timestamp returns 1`() {
        assertEquals(1, countOpens(listOf(1000L), 2000L))
        assertEquals(1, countOpens(listOf(0L), 2000L))
        assertEquals(1, countOpens(listOf(123456789L)))
    }

    @Test
    fun `burst within 2 s is de-duplicated to 1`() {
        // Events spaced within 2000ms from the first open
        val burst = listOf(1000L, 1200L, 1500L, 2000L, 2999L)
        assertEquals(1, countOpens(burst, 2000L))

        // Multiple rapid events within 500ms
        val rapidBurst = listOf(0L, 50L, 120L, 300L, 499L)
        assertEquals(1, countOpens(rapidBurst, 2000L))
    }

    @Test
    fun `spaced timestamps count each open`() {
        val spaced = listOf(1000L, 5000L, 10000L, 25000L)
        assertEquals(4, countOpens(spaced, 2000L))
    }

    @Test
    fun `timestamps exactly gapMs apart are counted as separate opens`() {
        val exactlyGap = listOf(1000L, 3000L, 5000L)
        assertEquals(3, countOpens(exactlyGap, 2000L))
    }

    @Test
    fun `timestamps less than gapMs apart are de-duplicated`() {
        // 2999 - 1000 = 1999 < 2000
        val justUnderGap = listOf(1000L, 2999L)
        assertEquals(1, countOpens(justUnderGap, 2000L))
    }

    @Test
    fun `unsorted timestamps are ordered before counting`() {
        val unsorted = listOf(10000L, 1000L, 5000L, 1500L)
        // Ordered: 1000L, 1500L (deduped), 5000L (open 2), 10000L (open 3)
        assertEquals(3, countOpens(unsorted, 2000L))
    }

    @Test
    fun `multiple bursts separated by gaps are counted correctly`() {
        val multipleBursts = listOf(
            // Burst 1: 1000, 1200, 1800 -> 1
            1000L, 1200L, 1800L,
            // Burst 2: 5000, 5500 -> 2
            5000L, 5500L,
            // Burst 3: 12000, 12800, 13500 -> 3
            12000L, 12800L, 13500L
        )
        assertEquals(3, countOpens(multipleBursts, 2000L))
    }

    @Test
    fun `companion object delegates to countOpens`() {
        assertEquals(2, UsageRepository.countOpens(listOf(1000L, 4000L), 2000L))
    }
}
