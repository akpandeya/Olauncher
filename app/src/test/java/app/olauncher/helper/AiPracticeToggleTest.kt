package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worker wiring behind the settings switch (#602, 9.9), tested against a fake
 * scheduler — WorkManager itself is not under test. On means the periodic top-up is
 * scheduled plus one immediate run; off means everything is cancelled and idle.
 */
class AiPracticeToggleTest {

    /** Records what the wiring asked for, with distinguishing call order. */
    private class FakeScheduler : AiPracticeScheduler {
        val calls = mutableListOf<String>()
        override fun schedulePeriodic() { calls += "schedule" }
        override fun runOnce() { calls += "runOnce" }
        override fun cancel() { calls += "cancel" }
    }

    @Test
    fun `turning on schedules the periodic top-up and runs once`() {
        val scheduler = FakeScheduler()

        AiPracticeToggle.apply(on = true, scheduler = scheduler)

        // The one-shot run comes after the periodic work is in place (first top-up).
        assertTrue(scheduler.calls.indexOf("schedule") < scheduler.calls.indexOf("runOnce"))
        assertFalse("cancel" in scheduler.calls)
    }

    @Test
    fun `turning off cancels both work names and schedules nothing`() {
        val scheduler = FakeScheduler()
        scheduler.schedulePeriodic()
        scheduler.runOnce()

        AiPracticeToggle.apply(on = false, scheduler = scheduler)

        assertEquals(setOf("cancel"), scheduler.calls.drop(2).toSet())
    }
}
