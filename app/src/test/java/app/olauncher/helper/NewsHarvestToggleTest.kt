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
    fun `switching German off cancels news harvest work regardless of news sentences flag`() {
        val schedulerWithNewsOn = FakeScheduler()
        NewsHarvestToggle.onGermanToggled(germanEnabled = false, newsSentencesEnabled = true, scheduler = schedulerWithNewsOn)
        assertEquals(listOf("cancel"), schedulerWithNewsOn.calls)

        val schedulerWithNewsOff = FakeScheduler()
        NewsHarvestToggle.onGermanToggled(germanEnabled = false, newsSentencesEnabled = false, scheduler = schedulerWithNewsOff)
        assertEquals(listOf("cancel"), schedulerWithNewsOff.calls)
    }

    @Test
    fun `switching German on schedules periodic harvest only when news sentences is enabled`() {
        val schedulerWithNewsOn = FakeScheduler()
        NewsHarvestToggle.onGermanToggled(germanEnabled = true, newsSentencesEnabled = true, scheduler = schedulerWithNewsOn)
        assertEquals(listOf("schedule"), schedulerWithNewsOn.calls)

        val schedulerWithNewsOff = FakeScheduler()
        NewsHarvestToggle.onGermanToggled(germanEnabled = true, newsSentencesEnabled = false, scheduler = schedulerWithNewsOff)
        assertTrue(schedulerWithNewsOff.calls.isEmpty())
    }

    @Test
    fun `sync schedules only when German and news sentences are both on, otherwise cancels`() {
        val cases = mapOf(
            Pair(true, true) to listOf("schedule"),
            Pair(true, false) to listOf("cancel"),
            Pair(false, true) to listOf("cancel"),
            Pair(false, false) to listOf("cancel"),
        )
        for ((flags, expected) in cases) {
            val scheduler = FakeScheduler()
            NewsHarvestToggle.sync(germanEnabled = flags.first, newsSentencesEnabled = flags.second, scheduler = scheduler)
            assertEquals("german=${flags.first} news=${flags.second}", expected, scheduler.calls)
        }
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
