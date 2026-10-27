package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.time.LocalDate
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
 */
class PracticeGenerator(
    private val provider: AiProvider,
    private val store: PracticeItemsStore,
    private val model: String,
    private val keySource: KeySource,
) {

    /**
     * On-demand: the thin due words (fewer than [PracticeQueue.ON_DEMAND_MIN_POOL]
     * items each) are generated inline before the round, under [timeoutMs] (~10 s —
     * a flash-class model answers in 1–2 s). On failure or timeout the caller falls
     * back to whatever the pool has; the words stay under-stocked, so the derived
     * queue hands them to the background top-up. Never call this on the UI thread.
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

    /** Background top-up: one batched request, no timeout (WorkManager owns the deadline). */
    suspend fun topUp(
        words: List<Word>,
        prefs: AiPracticePrefs,
        today: LocalDate,
    ): PracticeGeneration = generate(words, prefs, today, timeoutMs = null)

    /**
     * The digest (#604, 9.11): one batched call for the weak-spot [weakWords],
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

        // The cap is a behavioral guardrail, never removed: trim the batch to what
        // is left of today's budget; an empty budget blocks the attempt entirely.
        val remaining = prefs.aiDailyWordCap - prefs.aiWordsSentToday(today.toEpochDay())
        val batch = words.take(remaining.coerceAtLeast(0))
        if (batch.isEmpty()) return PracticeGeneration.Skipped(words, PracticeGeneration.Reason.CAP_REACHED)

        val prompt = PracticeContract.buildPrompt(batch, types)
        val request = AiRequest(prompt)
        val result = if (timeoutMs == null) {
            provider.complete(request)
        } else {
            // Timed out: the call is abandoned, the pool is untouched, the words stay queued.
            withTimeoutOrNull(timeoutMs) { provider.complete(request) }
                ?: return PracticeGeneration.Failed(
                    batch, AiResult.ErrorKind.NETWORK, timedOut = true,
                )
        }
        return when (result) {
            is AiResult.Ok -> {
                // The request was billed whether or not the response parsed, so the
                // cap counts the words sent, and success clears the error states.
                prefs.recordAiWordsSent(batch.size, today.toEpochDay())
                prefs.aiKeyRejected = false
                prefs.aiBillingBlocked = false
                val parsed = PracticeContract.parse(result.text, batch, model, source)
                val stored = store.append(parsed.items + parsed.clozeFallbacks)
                PracticeGeneration.Generated(batch, stored.size)
            }
            is AiResult.Error -> when (result.kind) {
                AiResult.ErrorKind.AUTH -> {
                    prefs.aiKeyRejected = true
                    PracticeGeneration.Failed(batch, AiResult.ErrorKind.AUTH)
                }
                AiResult.ErrorKind.BILLING -> {
                    // Out of prepaid credits: retrying cannot fix a balance, so the
                    // state is surfaced for the "top up credits" message and the
                    // caller stops instead of hot-looping.
                    prefs.aiBillingBlocked = true
                    PracticeGeneration.Failed(batch, AiResult.ErrorKind.BILLING)
                }
                else -> PracticeGeneration.Failed(batch, result.kind)
            }
        }
    }

    companion object {
        /** Owner decision (2026-10-27): on-demand answers in 1–2 s; ~10 s is the ceiling. */
        const val ON_DEMAND_TIMEOUT_MS = 10_000L
    }
}
