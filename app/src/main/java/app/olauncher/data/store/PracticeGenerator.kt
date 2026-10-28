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

    /**
     * Some chunks stored and billed before an error stopped the batch (review
     * R2 B): [itemCount] items are in the store for [words], and [kind] is why
     * the rest stopped. Callers honor what arrived — the digest timestamps the
     * covered words, Review reloads the store — and the still-thin words stay
     * in the derived queue for the next run.
     */
    data class Partial(
        override val words: List<Word>,
        val itemCount: Int,
        val kind: AiResult.ErrorKind,
    ) : PracticeGeneration()

    enum class Reason { DISABLED, NO_KEY, NO_MODEL, CAP_REACHED, NOTHING_QUEUED, KEY_REJECTED }
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
     * items each) are generated inline before the round, under [timeoutMs] —
     * the deadline is bounded by the per-request read timeout on the HTTP
     * connection (connect time + read timeout; see [AiRequest.readTimeoutMs]),
     * not by an immediate interrupt of a blocked socket read (review R1 #2, R2 C;
     * the benchmark measured p50 36.8 s for 20 words, far past any "1–2 s" hope).
     * Only [ON_DEMAND_MAX_WORDS] words go out — one small chunk fits the ~10 s
     * ceiling, a full 5-word chunk mostly times out billed (review R2 D). The
     * path is skipped entirely while [AiPracticePrefs.aiKeyRejected] is set, like
     * the worker gate. On failure or timeout the caller falls back to whatever
     * the pool has; the words stay under-stocked, so the derived queue hands
     * them to the background top-up. Never call this on the UI thread.
     */
    suspend fun onDemand(
        dueWords: List<Word>,
        prefs: AiPracticePrefs,
        today: LocalDate,
        timeoutMs: Long = ON_DEMAND_TIMEOUT_MS,
    ): PracticeGeneration {
        // A standing key rejection pauses every call until a new key is saved
        // (review R2 D): the on-demand path honours it like the worker gate.
        if (prefs.aiKeyRejected) {
            return PracticeGeneration.Skipped(dueWords, PracticeGeneration.Reason.KEY_REJECTED)
        }
        val thin = PracticeQueue.thinWords(dueWords, store.all())
        if (thin.isEmpty()) return PracticeGeneration.Skipped(dueWords, PracticeGeneration.Reason.NOTHING_QUEUED)
        // Pending owner decision (review R2 D): skip on-demand entirely when the
        // day's background top-up has already covered these words.
        return generate(thin.take(ON_DEMAND_MAX_WORDS), prefs, today, timeoutMs)
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
     * `AI_LAST_DIGEST_AT` ([prefs.aiLastDigestAt] = [nowMs]) updates on success
     * and on a partial success — the covered words are done, so re-digesting
     * them would only duplicate `source = digest` items (review R2 B). A skip
     * or a full failure leaves the timestamp, so the digest re-triggers. The
     * digest's type list is the app's weak-spot choice, so per-word type
     * narrowing is off here.
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
            narrowTypes = false,
        )
        if (outcome is PracticeGeneration.Generated || outcome is PracticeGeneration.Partial) {
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
        narrowTypes: Boolean = true,
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
            generationMutex.withLock { generateLocked(words, prefs, today, types, source, BACKGROUND_READ_TIMEOUT_MS, allowRetry = true, narrowTypes = narrowTypes) }
        } else {
            // Timed out: the caller stops waiting — cancellation interrupts the
            // thread, and a socket read already blocked in the kernel ends at
            // the per-request read timeout (review R2 C) — the pool is
            // untouched, the words stay queued. No retry-on-missing here: the
            // ~10 s ceiling cannot fit a follow-up call, the cloze fallbacks fill.
            withTimeoutOrNull(timeoutMs) {
                generationMutex.withLock { generateLocked(words, prefs, today, types, source, timeoutMs.toInt(), allowRetry = false, narrowTypes = narrowTypes) }
            } ?: PracticeGeneration.Failed(words, AiResult.ErrorKind.NETWORK, timedOut = true)
        }
    }

    /**
     * The chunk loop; the mutex makes the cap check and the sent-counter atomic.
     * Each request starts at the model's measured-best response format
     * ([PracticeSchemaModes.initialMode]: glm-5.3-flash in `json_object` — the
     * round-3 winning configuration — everything else in strict `json_schema`)
     * and falls back to `json_object` on the provider's capability codes
     * ([PracticeSchemaModes]). When [allowRetry], a word that ends up with fewer
     * than [PracticeContract.ITEMS_PER_WORD] accepted items after the quality
     * filters gets its missing slots requested once: one small follow-up call
     * per chunk covering the thin words (never repeated), within the same cap
     * accounting — each thin word's slot was already counted when the chunk went
     * out, so the follow-up adds calls, not cap counts, and is bounded by one
     * per word per run. Cloze fallbacks fill whatever is still missing.
     */
    private suspend fun generateLocked(
        words: List<Word>,
        prefs: AiPracticePrefs,
        today: LocalDate,
        types: List<DrillType>,
        source: String,
        readTimeoutMs: Int,
        allowRetry: Boolean,
        narrowTypes: Boolean,
    ): PracticeGeneration {
        // The cap is a behavioral guardrail, never removed: trim the batch to what
        // is left of today's budget; an empty budget blocks the attempt entirely.
        val remaining = prefs.aiDailyWordCap - prefs.aiWordsSentToday(today.toEpochDay())
        val batch = words.take(remaining.coerceAtLeast(0))
        if (batch.isEmpty()) return PracticeGeneration.Skipped(words, PracticeGeneration.Reason.CAP_REACHED)

        // Per-word type narrowing (round 3): only on the word paths that send the
        // full list anyway — a caller with an explicit type list (the digest)
        // keeps its own choice.
        val typesByWord: Map<Long, List<DrillType>> =
            if (narrowTypes && types == DrillType.values().toList()) {
                batch.associate { it.id to PracticeTypeNarrowing.narrow(it) }.filterValues { it.isNotEmpty() }
            } else {
                emptyMap()
            }

        val covered = mutableListOf<Word>()
        var storedCount = 0
        var sawBadResponse = false
        for (chunk in batch.chunked(WORDS_PER_REQUEST)) {
            // The request is billed once its body is written — timeouts and
            // non-OK replies included — so the cap counts the words at send
            // time, not only on a parsed response (review R1 #2). The cap
            // counts WORDS (chunk.size), never tokens.
            prefs.recordAiWordsSent(chunk.size, today.toEpochDay())
            // The model's measured-best mode first; a model that rejects the
            // strict schema falls back to json_object for the rest of the batch
            // ([PracticeSchemaModes]).
            var schemaMode = PracticeSchemaModes.initialMode(model)
            val result: AiResult
            while (true) {
                val request = AiRequest(
                    prompt = PracticeContract.buildPrompt(chunk, types, typesByWord),
                    maxTokens = chunk.size * MAX_TOKENS_PER_WORD,
                    readTimeoutMs = readTimeoutMs,
                    jsonSchema = if (schemaMode == PracticeSchemaModes.JSON_SCHEMA) PracticeContract.responseSchemaJson() else null,
                )
                val attempt = provider.complete(request)
                val fallback = (attempt as? AiResult.Error)?.let { PracticeSchemaModes.fallbackMode(schemaMode, it) }
                if (fallback != null) {
                    schemaMode = fallback
                    continue
                }
                result = attempt
                break
            }
            when (result) {
                is AiResult.Ok -> {
                    // A billed call clears the error states even before parsing.
                    prefs.aiKeyRejected = false
                    prefs.aiBillingBlocked = false
                    // No padding yet: the retry pass (below) decides what is
                    // still missing before the cloze fallbacks fill the rest.
                    val parsed = PracticeContract.parse(result.text, chunk, model, source, padFallbacks = false)
                    if (parsed.malformed) {
                        // Truncated or wholly unusable response: nothing was
                        // stored for this chunk, but earlier chunks already
                        // stored and billed theirs — skip it and keep going
                        // instead of discarding their success (review R2 B).
                        sawBadResponse = true
                        continue
                    }
                    val accepted = parsed.items.toMutableList()
                    if (allowRetry) {
                        accepted += retryMissingSlots(chunk, accepted, types, typesByWord, source, readTimeoutMs, schemaMode)
                    }
                    val fallbacks = chunk.flatMap { word ->
                        val have = accepted.count { it.wordId == word.id }
                        val missing = PracticeContract.ITEMS_PER_WORD - have
                        if (missing > 0) PracticeContract.fallbacksFor(word, missing, source) else emptyList()
                    }
                    storedCount += store.append(accepted + fallbacks).size
                    covered += chunk
                }
                is AiResult.Error -> when (result.kind) {
                    // A junk reply for one chunk (truncated, cut off at
                    // max_tokens) must not stop the batch: later chunks may
                    // still succeed (review R2 B).
                    AiResult.ErrorKind.BAD_RESPONSE -> sawBadResponse = true
                    AiResult.ErrorKind.AUTH -> {
                        prefs.aiKeyRejected = true
                        return stoppedAfterStored(result.kind, batch, covered, storedCount)
                    }
                    AiResult.ErrorKind.BILLING -> {
                        // Out of prepaid credits: retrying cannot fix a balance, so
                        // the state is surfaced for the "top up credits" message
                        // and the caller stops instead of hot-looping.
                        prefs.aiBillingBlocked = true
                        return stoppedAfterStored(result.kind, batch, covered, storedCount)
                    }
                    else -> return stoppedAfterStored(result.kind, batch, covered, storedCount)
                }
            }
        }
        // A batch that never stored anything is a failure, not an empty success
        // — the digest must not fire off an empty pool (review R1 #5, R2 B).
        if (sawBadResponse && storedCount == 0) {
            return PracticeGeneration.Failed(batch, AiResult.ErrorKind.BAD_RESPONSE)
        }
        return PracticeGeneration.Generated(covered, storedCount)
    }

    /**
     * The one retry-on-missing pass for a chunk (round 3): words with fewer than
     * [PracticeContract.ITEMS_PER_WORD] accepted items after the quality filters
     * get one small follow-up call covering exactly them — never repeated. Only
     * the missing slots are taken from the follow-up response; a failed or junk
     * follow-up is ignored (the cloze fallbacks fill). Runs only on the
     * background paths ([allowRetry]); the on-demand ~10 s ceiling cannot fit it.
     *
     * The retry prompt still asks for the cloze first, so the response's first
     * item is usually a second copy of the word's existing cloze (round-3 review
     * condition 3): a word that already has an accepted cloze therefore skips
     * CLOZE items here, and every candidate is duplicate-checked against the
     * word's whole accepted pool from the first call — not just within the
     * retry response, which [PracticeContract.parse] cannot see.
     */
    private suspend fun retryMissingSlots(
        chunk: List<Word>,
        accepted: List<PracticeItem>,
        types: List<DrillType>,
        typesByWord: Map<Long, List<DrillType>>,
        source: String,
        readTimeoutMs: Int,
        schemaMode: String,
    ): List<PracticeItem> {
        val thin = chunk.filter { word ->
            accepted.count { it.wordId == word.id } < PracticeContract.ITEMS_PER_WORD
        }
        if (thin.isEmpty()) return emptyList()
        val request = AiRequest(
            prompt = PracticeContract.buildPrompt(thin, types, typesByWord),
            maxTokens = thin.size * MAX_TOKENS_PER_WORD,
            readTimeoutMs = readTimeoutMs,
            jsonSchema = if (schemaMode == PracticeSchemaModes.JSON_SCHEMA) PracticeContract.responseSchemaJson() else null,
        )
        val result = provider.complete(request)
        if (result !is AiResult.Ok) return emptyList()
        val parsed = PracticeContract.parse(result.text, thin, model, source, padFallbacks = false)
        val fresh = mutableListOf<PracticeItem>()
        for (word in thin) {
            val pool = accepted.filter { it.wordId == word.id } + fresh.filter { it.wordId == word.id }
            val missing = PracticeContract.ITEMS_PER_WORD - pool.size
            if (missing <= 0) continue
            val hasCloze = pool.any { it.type == DrillType.CLOZE }
            val poolAnswers = pool.map { PracticeFilters.normalize(it.answer) }.toSet()
            fresh += parsed.items
                .filter { it.wordId == word.id }
                // The word already has its cloze: a second copy repeats the card.
                .filter { !(hasCloze && it.type == DrillType.CLOZE) }
                // A repeat of any accepted answer adds no new card.
                .filter { PracticeFilters.normalize(it.answer) !in poolAnswers }
                .take(missing)
        }
        return fresh
    }

    /**
     * The outcome when an error stops the chunk loop: chunks that already
     * stored and billed keep their success as a [PracticeGeneration.Partial]
     * (review R2 B); with nothing stored the batch fails as before.
     */
    private fun stoppedAfterStored(
        kind: AiResult.ErrorKind,
        batch: List<Word>,
        covered: List<Word>,
        storedCount: Int,
    ): PracticeGeneration =
        if (storedCount > 0) PracticeGeneration.Partial(covered, storedCount, kind)
        else PracticeGeneration.Failed(batch, kind)

    companion object {
        /** Owner decision (2026-10-27): on-demand answers within ~10 s; that is the ceiling. */
        const val ON_DEMAND_TIMEOUT_MS = 10_000L

        /**
         * On-demand sends one small chunk only (review R2 D): the benchmark's p50
         * puts a 5-word chunk at ~9–10 s — the whole ceiling — so a full chunk
         * would mostly time out billed on every Review start. 1–2 words fit.
         */
        const val ON_DEMAND_MAX_WORDS = 2

        /**
         * The background read timeout per chunked request (review R1 #1): the
         * benchmark (#600) needed a 300 s timeout — p95 72 s for 20 words — so a
         * chunk gets the same room. WorkManager still owns the overall deadline.
         */
        const val BACKGROUND_READ_TIMEOUT_MS = 300_000

        /** Words per request: small chunks finish well inside the token budget. */
        const val WORDS_PER_REQUEST = 5

        /**
         * Output tokens per word. Round-3 final review (2026-10-28): raised from
         * 1200 — the passing json_object benchmark run measured 34,849–38,434
         * output tokens for 60 items, about 580–640 per item or 1,750–1,920 per
         * word, so the old 1,200/word budget cut real chunks off mid-JSON and a
         * cut-off chunk is a billed BAD_RESPONSE (review condition 2). The
         * daily word cap is untouched: it counts words ([generateLocked] sends
         * `chunk.size`), never tokens.
         */
        const val MAX_TOKENS_PER_WORD = 2400

        /** Serializes every generation call app-wide (both paths, all instances). */
        private val generationMutex = Mutex()
    }
}
