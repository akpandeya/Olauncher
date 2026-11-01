package app.olauncher.helper

import app.olauncher.data.store.AiResult
import app.olauncher.data.store.PracticeGeneration
import app.olauncher.data.store.Word
import org.junit.Assert.assertNull
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

    @Test
    fun `the digest announcement counts covered words only when something new was stored`() {
        val covered = (61L..64L).map { Word(id = it, text = "w$it", context = "c$it", dueDay = 1, addedAt = it) }
        // Four covered words, seven new items: the announcement counts the words.
        assertEquals(4, PracticeWorkerPolicy.digestAnnouncement(PracticeGeneration.Generated(covered, itemCount = 7)))
        assertEquals(
            4,
            PracticeWorkerPolicy.digestAnnouncement(
                PracticeGeneration.Partial(covered, itemCount = 2, kind = AiResult.ErrorKind.AUTH),
            ),
        )
        // Every card was a copy of a stored one: nothing new to announce.
        assertNull(PracticeWorkerPolicy.digestAnnouncement(PracticeGeneration.Generated(covered, itemCount = 0)))
        assertNull(
            PracticeWorkerPolicy.digestAnnouncement(
                PracticeGeneration.Partial(covered, itemCount = 0, kind = AiResult.ErrorKind.BILLING),
            ),
        )
        assertNull(
            PracticeWorkerPolicy.digestAnnouncement(PracticeGeneration.Failed(covered, AiResult.ErrorKind.NETWORK)),
        )
        assertNull(
            PracticeWorkerPolicy.digestAnnouncement(
                PracticeGeneration.Skipped(covered, PracticeGeneration.Reason.CAP_REACHED),
            ),
        )
    }

    @Test
    fun `capture work has its own name, a two-minute delay and a six-hour periodic net`() {
        assertEquals("ai_practice_soon", PracticeWorker.WORK_NAME_SOON)
        assertTrue(PracticeWorker.WORK_NAME_SOON != PracticeWorker.WORK_NAME_NOW)
        assertTrue(PracticeWorker.WORK_NAME_SOON != PracticeWorker.WORK_NAME_PERIODIC)
        assertEquals(120_000L, PracticeWorker.CAPTURE_DELAY_MS)
        assertEquals(6L, PracticeWorker.PERIODIC_INTERVAL_HOURS)
    }

    @Test
    fun `review kick needs the gate and a short due word`() {
        assertTrue(ReviewKickPolicy.shouldKick(true, "m", false, true, nowMs = 1_000_000, lastKickAtMs = null))
        assertFalse(ReviewKickPolicy.shouldKick(false, "m", false, true, nowMs = 1_000_000, lastKickAtMs = null))
        assertFalse(ReviewKickPolicy.shouldKick(true, "", false, true, nowMs = 1_000_000, lastKickAtMs = null))
        assertFalse(ReviewKickPolicy.shouldKick(true, "m", true, true, nowMs = 1_000_000, lastKickAtMs = null))
        assertFalse(ReviewKickPolicy.shouldKick(true, "m", false, false, nowMs = 1_000_000, lastKickAtMs = null))
    }

    @Test
    fun `review kick waits thirty minutes between kicks`() {
        val last = 5_000_000L
        assertEquals(1_800_000L, ReviewKickPolicy.COOLDOWN_MS)
        assertFalse(
            ReviewKickPolicy.shouldKick(
                enabled = true,
                model = "m",
                keyRejected = false,
                dueNeedItems = true,
                nowMs = last + ReviewKickPolicy.COOLDOWN_MS - 1,
                lastKickAtMs = last,
            )
        )
        assertTrue(
            ReviewKickPolicy.shouldKick(
                enabled = true,
                model = "m",
                keyRejected = false,
                dueNeedItems = true,
                nowMs = last + ReviewKickPolicy.COOLDOWN_MS,
                lastKickAtMs = last,
            )
        )
    }
}
