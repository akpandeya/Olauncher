package app.olauncher.data.store

import org.json.JSONObject

/**
 * What one [GermanWebResponse] means for the entry that was sent and the rest
 * of the run (epic #835, task 03), Context-free and unit-tested. The policy
 * follows the launcher spike (docs section (a)): a 2xx removes the entry; a
 * 400/404/409/other 4xx can never succeed and is dropped to the failed list; a
 * 401 means the device was revoked — the run stops and every entry is kept for
 * after re-pairing; a 429 or 5xx stops the run with the entry kept and deferred.
 */
sealed class GermanReplayDecision {
    /** The write made it (the server replays the first 2xx for a stored key): remove the entry. */
    object RemoveEntry : GermanReplayDecision()

    /** A 4xx the server will always reject: drop the entry to the failed list. */
    data class DropToFailed(val status: Int) : GermanReplayDecision()

    /** A 401: the device was revoked. Stop the run, keep every entry, re-pair; never loop retries. */
    object StopUnauthorized : GermanReplayDecision()

    /** A 429: stop the run, keep the entry, next attempt after [retryAfterSeconds] or the backoff. */
    data class StopRateLimited(val retryAfterSeconds: Long?) : GermanReplayDecision()

    /** A 5xx or network failure: stop the run, keep the entry, exponential backoff. */
    object StopServerError : GermanReplayDecision()
}

enum class GermanReplayStopReason { UNAUTHORIZED, RATE_LIMITED, SERVER_ERROR }

/** The outcome of one replay run: entries that got a final outcome, and why it stopped. */
data class GermanOutboxRunResult(
    val handledCount: Int,
    val stopReason: GermanReplayStopReason?,
)

object GermanOutboxReplay {

    /**
     * At most this many entries per run, so a catch-up burst stays under the
     * server's 20 writes/min limit.
     */
    const val MAX_ENTRIES_PER_RUN = 15

    const val INITIAL_BACKOFF_SECONDS = 30L
    const val MAX_BACKOFF_SECONDS = 3_600L

    fun decide(response: GermanWebResponse): GermanReplayDecision = when (response) {
        is GermanWebResponse.NetworkFailure -> GermanReplayDecision.StopServerError
        is GermanWebResponse.Http -> when {
            response.status in 200..299 -> GermanReplayDecision.RemoveEntry
            response.status == 401 -> GermanReplayDecision.StopUnauthorized
            response.status == 429 -> GermanReplayDecision.StopRateLimited(
                response.retryAfterSeconds?.toLong()
            )
            response.status in 500..599 -> GermanReplayDecision.StopServerError
            else -> GermanReplayDecision.DropToFailed(response.status)
        }
    }

    /**
     * 30 s doubling per failed attempt, capped at 1 h: 1 → 30 s, 2 → 60 s,
     * 3 → 2 min, … 8 and up → 1 h.
     */
    fun backoffSeconds(failedAttempts: Int): Long {
        var delay = INITIAL_BACKOFF_SECONDS
        repeat((failedAttempts - 1).coerceAtLeast(0)) {
            if (delay < MAX_BACKOFF_SECONDS) delay = minOf(delay * 2, MAX_BACKOFF_SECONDS)
        }
        return delay
    }

    /**
     * Replays the outbox with [api] and its [token]: due entries in FIFO order,
     * at most [MAX_ENTRIES_PER_RUN] HTTP sends per run, stopping at the first
     * 401/429/5xx/network failure. The stored key and body are sent as-is — a
     * new key is never minted and `today` is never rewritten. [onUnauthorized]
     * fires once on a 401, before the run stops.
     */
    suspend fun replayAll(
        outbox: GermanWebOutbox,
        api: GermanWebApi,
        token: String,
        onUnauthorized: () -> Unit,
        clock: () -> Long = { System.currentTimeMillis() },
    ): GermanOutboxRunResult {
        var handledCount = 0
        for (entry in outbox.due(clock())) {
            if (handledCount >= MAX_ENTRIES_PER_RUN) break
            val request = GermanWebRequest(
                method = entry.method,
                path = entry.path,
                bodyJson = entry.bodyJson,
                idempotencyKey = entry.idempotencyKey,
            )
            when (val decision = decide(api.send(request, token))) {
                GermanReplayDecision.RemoveEntry -> {
                    outbox.remove(entry.idempotencyKey)
                    handledCount++
                }
                is GermanReplayDecision.DropToFailed -> {
                    outbox.dropToFailed(entry.idempotencyKey, decision.status.toString(), failedEntryText(entry), clock())
                    handledCount++
                }
                GermanReplayDecision.StopUnauthorized -> {
                    onUnauthorized()
                    return GermanOutboxRunResult(handledCount, GermanReplayStopReason.UNAUTHORIZED)
                }
                is GermanReplayDecision.StopRateLimited -> {
                    val delaySeconds = decision.retryAfterSeconds?.takeIf { it > 0 }
                        ?: backoffSeconds(entry.attempts + 1)
                    outbox.retryIn(entry.idempotencyKey, delaySeconds, clock())
                    return GermanOutboxRunResult(handledCount, GermanReplayStopReason.RATE_LIMITED)
                }
                GermanReplayDecision.StopServerError -> {
                    outbox.retryIn(entry.idempotencyKey, backoffSeconds(entry.attempts + 1), clock())
                    return GermanOutboxRunResult(handledCount, GermanReplayStopReason.SERVER_ERROR)
                }
            }
        }
        return GermanOutboxRunResult(handledCount, null)
    }

    /**
     * What a dropped entry was about, for the failed list: the captured word's
     * `text` when the body carries one, else the request path.
     */
    private fun failedEntryText(entry: GermanOutboxEntry): String {
        val bodyText = try {
            (entry.bodyJson?.let { JSONObject(it) }?.opt("text")) as? String
        } catch (e: Exception) {
            null
        }
        return bodyText?.takeIf { it.isNotBlank() } ?: entry.path
    }
}
