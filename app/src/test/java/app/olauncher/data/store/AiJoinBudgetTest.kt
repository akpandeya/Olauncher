package app.olauncher.data.store

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJoinBudgetTest {

    /** A cap unlike the app's 3, so a hard-coded limit cannot pass. */
    private val budget = AiJoinBudget(cap = 2)

    @Test
    fun `exactly cap requests are issued, further ones are refused`() {
        val counter = budget.counter("session-a", 0)

        assertTrue(budget.tryIssue(counter))
        assertTrue(budget.tryIssue(counter))
        assertFalse(budget.tryIssue(counter))

        assertEquals(2, counter.get())
    }

    @Test
    fun `a rotation rebinds to the same counter, not a fresh one`() {
        val before = budget.counter("session-b", 0)
        budget.tryIssue(before)

        // The recreated activity rebinds with the restored count from the bundle,
        // which can lag the live counter (#698): the same session id must yield the
        // same counter, so the in-flight increment is not lost.
        val after = budget.counter("session-b", restoredUsed = 0)

        assertSame(before, after)
        assertEquals(1, after.get())
    }

    @Test
    fun `an unknown session id seeds the count saved in the bundle`() {
        // Process death: the counter is gone, the bundle restores what was issued.
        val counter = budget.counter("session-c", restoredUsed = 1)

        assertEquals(1, counter.get())
        assertTrue(budget.tryIssue(counter))
        assertFalse(budget.tryIssue(counter))
        assertEquals(2, counter.get())
    }

    @Test
    fun `a released session starts from scratch`() {
        val first = budget.counter("session-d", 0)
        budget.tryIssue(first)
        budget.release("session-d")

        val second = budget.counter("session-d", 0)

        assertNotSame(first, second)
        assertEquals(0, second.get())
    }

    @Test
    fun `a session restored at the cap issues nothing more`() {
        val counter = budget.counter("session-e", restoredUsed = 2)

        assertFalse(budget.tryIssue(counter))
        assertEquals(2, counter.get())
    }

    @Test
    fun `overlapping issuances never exceed the cap`() {
        val cap = 5
        val budget = AiJoinBudget(cap)
        val counter = budget.counter("session-f", 0)
        val accepted = AtomicInteger(0)
        val start = CountDownLatch(1)
        val threads = (1..24).map {
            Thread {
                start.await()
                if (budget.tryIssue(counter)) accepted.incrementAndGet()
            }
        }
        threads.forEach { it.start() }
        start.countDown()
        threads.forEach { it.join() }

        assertEquals(cap, accepted.get())
        assertEquals(cap, counter.get())
    }
}
