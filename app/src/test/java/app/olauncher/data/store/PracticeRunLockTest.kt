package app.olauncher.data.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PracticeRunLockTest {

    @Test
    fun `a free lock runs the block and returns its value`() = runBlocking {
        val lock = PracticeRunLock()
        val first = lock.runOrSkip { 7 }
        assertEquals(7, first)
        val second = lock.runOrSkip { 8 }
        assertEquals(8, second)
    }

    @Test
    fun `a held lock skips at once without running the block`() = runBlocking {
        val lock = PracticeRunLock()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        val jobA = launch {
            lock.runOrSkip {
                entered.complete(Unit)
                release.await()
            }
        }

        entered.await()
        var ran = false
        val skipped = lock.runOrSkip {
            ran = true
            1
        }
        assertNull(skipped)
        assertFalse(ran)

        release.complete(Unit)
        jobA.join()

        val after = lock.runOrSkip { 2 }
        assertEquals(2, after)
    }

    @Test
    fun `the lock is released when the block throws`() = runBlocking {
        val lock = PracticeRunLock()
        runCatching {
            lock.runOrSkip<Int> { throw IllegalStateException("boom") }
        }
        val after = lock.runOrSkip { 3 }
        assertEquals(3, after)
    }
}
