package app.olauncher.data.store

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The per-capture-session AI suggestion budget (#523 tier 3), held per capture session so
 * rotation cannot fork it (#698): the counter lives here, in the process, keyed by a session
 * id the activity carries through its saved instance state. A rotation rebinds the recreated
 * activity to the same [AtomicInteger], so an in-flight request from the previous instance
 * still increments a counter the restored instance reads — the bundle write can no longer
 * land between the cap check and the increment. [release] drops the counter when a capture
 * session really ends; a session id without a counter (process death) is seeded from the
 * count saved in the bundle.
 */
class AiJoinBudget(private val cap: Int) {

    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    /** The counter for [sessionId], seeded with [restoredUsed] only when none exists yet. */
    fun counter(sessionId: String, restoredUsed: Int): AtomicInteger =
        counters.computeIfAbsent(sessionId) { AtomicInteger(restoredUsed) }

    /** Ends [sessionId]'s budget; a later [counter] call for the id starts from scratch. */
    fun release(sessionId: String) {
        counters.remove(sessionId)
    }

    /**
     * Counts one request as issued, atomically: false when [cap] is already reached, so
     * overlapping callers can never both read below the cap and issue together.
     */
    fun tryIssue(counter: AtomicInteger): Boolean =
        if (counter.incrementAndGet() > cap) {
            counter.decrementAndGet()
            false
        } else {
            true
        }
}
