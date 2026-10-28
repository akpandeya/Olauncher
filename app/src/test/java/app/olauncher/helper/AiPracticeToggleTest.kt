package app.olauncher.helper

import app.olauncher.data.store.AiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worker wiring behind the settings switch (#602, 9.9), tested against a fake
 * scheduler — WorkManager itself is not under test. On means the periodic top-up is
 * scheduled (starting a day later) plus one immediate run; off means everything is
 * cancelled and idle.
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

    // --- PracticeWorkerPolicy (review R1 #6) ---

    @Test
    fun `the worker runs only when the gate allows and no key stands rejected`() {
        assertTrue(PracticeWorkerPolicy.mayCall(enabled = true, hasKey = true, model = "m", keyRejected = false))
        // A standing AUTH rejection pauses every call until a new key is saved.
        assertFalse(PracticeWorkerPolicy.mayCall(enabled = true, hasKey = true, model = "m", keyRejected = true))
        assertFalse(PracticeWorkerPolicy.mayCall(enabled = false, hasKey = true, model = "m", keyRejected = false))
        assertFalse(PracticeWorkerPolicy.mayCall(enabled = true, hasKey = false, model = "m", keyRejected = false))
        assertFalse(PracticeWorkerPolicy.mayCall(enabled = true, hasKey = true, model = "", keyRejected = false))
    }

    @Test
    fun `auth and billing end the worker run before the digest`() {
        assertTrue(PracticeWorkerPolicy.stopsRun(AiResult.ErrorKind.AUTH))
        assertTrue(PracticeWorkerPolicy.stopsRun(AiResult.ErrorKind.BILLING))
        // Everything retryable or transient lets the digest proceed.
        assertFalse(PracticeWorkerPolicy.stopsRun(AiResult.ErrorKind.RATE_LIMIT))
        assertFalse(PracticeWorkerPolicy.stopsRun(AiResult.ErrorKind.NETWORK))
        assertFalse(PracticeWorkerPolicy.stopsRun(AiResult.ErrorKind.BAD_RESPONSE))
        assertFalse(PracticeWorkerPolicy.stopsRun(AiResult.ErrorKind.SERVER))
    }
}
