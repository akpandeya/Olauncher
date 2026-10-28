package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The outcome of one [PracticeGenerator] attempt. [words] are the words the
 * attempt covered, so the caller can fall back to them (on-demand) or know what
 * stays queued (background): the queue is derived, so an under-stocked word is
 * automatically "enqueued" — it is picked again by [PracticeQueue.next].
 */
sealed class PracticeGeneration {
    abstract val words: List<Word>

    /** The request went out and the parsed items are in the store. */
    data class Generated(
        override val words: List<Word>,
        val itemCount: Int,
    ) : PracticeGeneration()

    /** No call was made. [Reason.CAP_REACHED] may have trimmed [words] — see the generator. */
    data class Skipped(
        override val words: List<Word>,
        val reason: Reason,
    ) : PracticeGeneration()

    /** The call failed. RATE_LIMIT and NETWORK are retryable; the words stay queued. */
    data class Failed(
        override val words: List<Word>,
        val kind: AiResult.ErrorKind,
        val timedOut: Boolean = false,
    ) : PracticeGeneration()

    enum class Reason { DISABLED, NO_KEY, NO_MODEL, CAP_REACHED, NOTHING_QUEUED }
}

/**
 * The one generation path behind both triggers of the hybrid design (docs/design/
 * ai-practice.md "Budgets", #601, 9.8): the synchronous on-demand call when a
 * practice round starts with a thin pool, and the WorkManager background top-up.
 *
 * Every attempt applies the same guard the settings gate (#602) encodes — feature
 * on, key present, model set — and the same daily word cap, counted from the
 * persisted "words sent today" counter ([AiPracticePrefs.aiWordsSentToday]) so it
 * holds across both paths and resets by calendar day. AUTH sets
 * [AiPracticePrefs.aiKeyRejected]; BILLING (out of credits) sets
 * [AiPracticePrefs.aiBillingBlocked]; a successful call clears both. The key only
 * ever flows through the [KeySource] abstraction — never into logs or errors.
 *
 * Batches are chunked into small per-request groups sized for the token and time
 * budget (review R1 #1): one request carries [WORDS_PER_REQUEST] words and asks
 * for [MAX_TOKENS_PER_WORD] output tokens per word, so a 50-word queue becomes
 * several small calls instead of one call that is cut off at the default cap.
 * All generation is serialized behind one app-wide mutex so the on-demand path
 * and the worker can never send the same queue at once (review R1 #3).
 */
class PracticeGenerator(
    private val provider: AiProvider,
    private val store: PracticeItemsStore,
    private val model: String,
    private val keySource: KeySource,
) {

    /**
     * On-demand: the thin due words (fewer than [PracticeQueue.ON_DEMAND_MIN_POOL]
     * items each) are generated inline before the round, under [timeoutMs] — the
     * whole chunk loop shares the ceiling, and the deadline is enforced on the
     * HTTP connection itself (via [AiRequest.readTimeoutMs]) plus coroutine
     * cancellation, so a slow provider actually stops (review R1 #2; the benchmark
     * measured p50 36.8 s for 20 words, far past any "1–2 s" hope). On failure or
     * timeout the caller falls back to whatever the pool has; the words stay
     * under-stocked, so the derived queue hands them to the background top-up.
     * Never call this on the UI thread.
     */
    suspend fun onDemand(
        dueWords: List<Word>,
        prefs: AiPracticePrefs,
        today: LocalDate,
        timeoutMs: Long = ON_DEMAND_TIMEOUT_MS,
    ): PracticeGeneration {
        val thin = PracticeQueue.thinWords(dueWords, store.all())
        if (thin.isEmpty()) return PracticeGeneration.Skipped(dueWords, PracticeGeneration.Reason.NOTHING_QUEUED)
        return generate(thin, prefs, today, timeoutMs)
    }

    /**
     * Background top-up: the queue in chunked requests under the daily word cap,
     * each with a generous read timeout (WorkManager owns the overall deadline).
     */
    suspend fun topUp(
        words: List<Word>,
        prefs: AiPracticePrefs,
        today: LocalDate,
    ): PracticeGeneration = generate(words, prefs, today, timeoutMs = null)

    /**
     * The digest (#604, 9.11): the weak-spot [weakWords] in chunked calls,
     * restricted to the [weakTypes] that are actually weak, stored with
     * `source = digest`. Same gate and daily word cap as the other paths, and
     * `AI_LAST_DIGEST_AT` ([prefs.aiLastDigestAt] = [nowMs]) updates only on
     * success — a skip or failure leaves the timestamp, so the digest re-triggers.
     */
    suspend fun digest(
        weakWords: List<Word>,
        weakTypes: List<DrillType>,
        prefs: AiPracticePrefs,
        today: LocalDate,
        nowMs: Long = System.currentTimeMillis(),
    ): PracticeGeneration {
        val outcome = generate(
            words = weakWords,
            prefs = prefs,
            today = today,
            timeoutMs = null,
            types = weakTypes.ifEmpty { DrillType.values().toList() },
            source = PracticeItem.Source.DIGEST,
        )
        if (outcome is PracticeGeneration.Generated) {
            prefs.aiLastDigestAt = nowMs
        }
        return outcome
    }

    private suspend fun generate(
        words: List<Word>,
        prefs: AiPracticePrefs,
        today: LocalDate,
        timeoutMs: Long?,
        types: List<DrillType> = DrillType.values().toList(),
        source: String = PracticeItem.Source.WORD,
    ): PracticeGeneration {
        // The shared gate (also the settings screen's, #602): off means no call, ever.
        when (AiPracticeGate.block(prefs.aiPracticeEnabled, !keySource.get().isNullOrBlank(), model)) {
            AiPracticeGate.Block.DISABLED -> return PracticeGeneration.Skipped(words, PracticeGeneration.Reason.DISABLED)
            AiPracticeGate.Block.NO_KEY -> return PracticeGeneration.Skipped(words, PracticeGeneration.Reason.NO_KEY)
            AiPracticeGate.Block.NO_MODEL -> return PracticeGeneration.Skipped(words, PracticeGeneration.Reason.NO_MODEL)
            null -> {}
        }
        // One app-wide mutex: the cap's read-then-write and the queue are only
        // safe when the on-demand path and the worker never generate at once.
        return if (timeoutMs == null) {
            generationMutex.withLock { generateLocked(words, prefs, today, types, source, BACKGROUND_READ_TIMEOUT_MS) }
        } else {
            // Timed out: the call is interrupted (the provider releases on
            // cancellation), the pool is untouched, the words stay queued.
            withTimeoutOrNull(timeoutMs) {
                generationMutex.withLock { generateLocked(words, prefs, today, types, source, timeoutMs.toInt()) }
            } ?: PracticeGeneration.Failed(words, AiResult.ErrorKind.NETWORK, timedOut = true)
        }
    }

    /** The chunk loop; the mutex makes the cap check and the sent-counter atomic. */
    private suspend fun generateLocked(
        words: List<Word>,
        prefs: AiPracticePrefs,
        today: LocalDate,
        types: List<DrillType>,
        source: String,
        readTimeoutMs: Int,
    ): PracticeGeneration {
        // The cap is a behavioral guardrail, never removed: trim the batch to what
        // is left of today's budget; an empty budget blocks the attempt entirely.
        val remaining = prefs.aiDailyWordCap - prefs.aiWordsSentToday(today.toEpochDay())
        val batch = words.take(remaining.coerceAtLeast(0))
        if (batch.isEmpty()) return PracticeGeneration.Skipped(words, PracticeGeneration.Reason.CAP_REACHED)

        val covered = mutableListOf<Word>()
        var storedCount = 0
        for (chunk in batch.chunked(WORDS_PER_REQUEST)) {
            // The request is billed once its body is written — timeouts and
            // non-OK replies included — so the cap counts the words at send
            // time, not only on a parsed response (review R1 #2).
            prefs.recordAiWordsSent(chunk.size, today.toEpochDay())
            val request = AiRequest(
                prompt = PracticeContract.buildPrompt(chunk, types),
                maxTokens = chunk.size * MAX_TOKENS_PER_WORD,
                readTimeoutMs = readTimeoutMs,
            )
            when (val result = provider.complete(request)) {
                is AiResult.Ok -> {
                    // A billed call clears the error states even before parsing.
                    prefs.aiKeyRejected = false
                    prefs.aiBillingBlocked = false
                    val parsed = PracticeContract.parse(result.text, chunk, model, source)
                    if (parsed.items.isEmpty() && parsed.clozeFallbacks.isEmpty()) {
                        // Truncated or wholly rejected response: nothing was
                        // stored, so this is a failure, not a success — the
                        // digest must not fire off an empty pool (review R1 #5).
                        return PracticeGeneration.Failed(batch, AiResult.ErrorKind.BAD_RESPONSE)
                    }
                    storedCount += store.append(parsed.items + parsed.clozeFallbacks).size
                    covered += chunk
                }
                is AiResult.Error -> return when (result.kind) {
                    AiResult.ErrorKind.AUTH -> {
                        prefs.aiKeyRejected = true
                        PracticeGeneration.Failed(batch, AiResult.ErrorKind.AUTH)
                    }
                    AiResult.ErrorKind.BILLING -> {
                        // Out of prepaid credits: retrying cannot fix a balance, so
                        // the state is surfaced for the "top up credits" message
                        // and the caller stops instead of hot-looping.
                        prefs.aiBillingBlocked = true
                        PracticeGeneration.Failed(batch, AiResult.ErrorKind.BILLING)
                    }
                    else -> PracticeGeneration.Failed(batch, result.kind)
                }
            }
        }
        return PracticeGeneration.Generated(covered, storedCount)
    }

    companion object {
        /** Owner decision (2026-10-27): on-demand answers within ~10 s; that is the ceiling. */
        const val ON_DEMAND_TIMEOUT_MS = 10_000L

        /**
         * The background read timeout per chunked request (review R1 #1): the
         * benchmark (#600) needed a 300 s timeout — p95 72 s for 20 words — so a
         * chunk gets the same room. WorkManager still owns the overall deadline.
         */
        const val BACKGROUND_READ_TIMEOUT_MS = 300_000

        /** Words per request: small chunks finish well inside the token budget. */
        const val WORDS_PER_REQUEST = 5

        /** Output tokens per word, sized from the benchmark's items-per-word usage. */
        const val MAX_TOKENS_PER_WORD = 600

        /** Serializes every generation call app-wide (both paths, all instances). */
        private val generationMutex = Mutex()
    }
}
