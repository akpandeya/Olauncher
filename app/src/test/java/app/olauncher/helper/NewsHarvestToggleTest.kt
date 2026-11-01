package app.olauncher.helper

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsHarvestToggleTest {

    private class FakeScheduler : NewsHarvestScheduler {
        val calls = mutableListOf<String>()
        override fun schedulePeriodic() { calls += "schedule" }
        override fun runOnce() { calls += "runOnce" }
        override fun cancel() { calls += "cancel" }
    }

    @Test
    fun `turning on schedules the periodic work and runs once in order`() {
        val scheduler = FakeScheduler()

        NewsHarvestToggle.apply(on = true, scheduler = scheduler)

        assertEquals(listOf("schedule", "runOnce"), scheduler.calls)
    }

    @Test
    fun `turning off cancels work only`() {
        val scheduler = FakeScheduler()
        scheduler.schedulePeriodic()
        scheduler.runOnce()
        scheduler.calls.clear()

        NewsHarvestToggle.apply(on = false, scheduler = scheduler)

        assertEquals(listOf("cancel"), scheduler.calls)
    }

    @Test
    fun `NewsHarvestPolicy mayRun truth table - only true and true runs`() {
        assertTrue(NewsHarvestPolicy.mayRun(enabled = true, germanEnabled = true))
        assertFalse(NewsHarvestPolicy.mayRun(enabled = true, germanEnabled = false))
        assertFalse(NewsHarvestPolicy.mayRun(enabled = false, germanEnabled = true))
        assertFalse(NewsHarvestPolicy.mayRun(enabled = false, germanEnabled = false))
    }

    @Test
    fun `articlesLeftToday returns remaining budget coerced to at least zero`() {
        assertEquals(30, NewsHarvestPolicy.articlesLeftToday(30, 0))
        assertEquals(1, NewsHarvestPolicy.articlesLeftToday(30, 29))
        assertEquals(0, NewsHarvestPolicy.articlesLeftToday(30, 31))
    }

    @Test
    fun `run guard skips a second run while one is in progress and frees itself afterwards`() = runBlocking {
        val guard = NewsHarvestRunGuard()
        val ran = mutableListOf<String>()

        val outer = guard.runExclusive {
            ran += "outer"
            val nested = guard.runExclusive { ran += "nested"; 7 }
            assertNull(nested)
            42
        }
        assertEquals(42, outer)

        try {
            guard.runExclusive<Int> { ran += "failing"; throw IllegalStateException("boom") }
        } catch (_: IllegalStateException) {
        }
        val after = guard.runExclusive { ran += "after"; 9 }

        assertEquals(9, after)
        assertEquals(listOf("outer", "failing", "after"), ran)
    }
}
