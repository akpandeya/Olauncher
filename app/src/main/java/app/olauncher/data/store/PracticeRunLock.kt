package app.olauncher.data.store

import kotlinx.coroutines.sync.Mutex

/**
 * One generation run at a time, app-wide (#725). Every PracticeWorker run —
 * periodic `ai_practice`, `ai_practice_now` ("Generate now", switch on) and
 * `ai_practice_soon` (after capture, on review open) — takes [APP] for its whole
 * run: queue, top-up and digest. A run that finds it held does not wait; it ends
 * without a request, and its words stay in the derived queue for the next run.
 * `PracticeGenerator`'s per-call `generationMutex` stays as it is inside a run.
 */
class PracticeRunLock {
    private val mutex = Mutex()

    /**
     * Runs [block] and returns its result when no other run holds the lock.
     * Returns null at once, without running [block], when one does. The lock is
     * released when [block] returns or throws.
     */
    suspend fun <T : Any> runOrSkip(block: suspend () -> T): T? {
        if (!mutex.tryLock()) return null
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }

    companion object {
        /** The lock every PracticeWorker run takes. Tests build their own [PracticeRunLock]. */
        val APP = PracticeRunLock()
    }
}
