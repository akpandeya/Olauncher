package app.olauncher.data.store

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which chat session the engine has open (docs/design/ask-ai-chat.md §Threads):
 * a fresh, not-yet-persisted chat ("thread existence starts at first send") or a
 * stored thread resumed by id.
 */
sealed class AiChatOpen {
    data class Fresh(
        val subject: AiChatSubject,
        val groundings: List<AiChatGrounding> = emptyList(),
        /**
         * The alternative candidate of a fresh both-candidates thread (design
         * §Threads: "When a fresh thread could be word or rule"). [subject] stays
         * the fallback (the rule); when the first reply names [proposal]'s kind,
         * the thread is filed under [proposal] instead.
         */
        val proposal: AiChatSubject? = null,
    ) : AiChatOpen()

    data class Resumed(val threadId: String) : AiChatOpen()
}

/**
 * In-memory snapshot of one open chat session, published after every engine step.
 *
 * - [thread] is the working copy the engine mutates and persists; null while the
 *   session is fresh and nothing was sent yet.
 * - [pendingQuestion] is the in-flight question — design §Reliability "waiting":
 *   user tile + "Thinking…" placeholder, Send disabled, Cancel available. Null
 *   when idle; a settled failure instead shows on the turn's [AiChatMessage.failed]
 *   ("Not sent · Retry").
 * - [storeError] is the inline "Couldn't save this conversation" state: the last
 *   storage write failed, so a visible message is never silently lost — it stays
 *   in [thread] and a later successful write persists the whole working copy.
 * - [omittedUpTo] is the "Earlier turns omitted" marker: the message index up to
 *   which the oldest prose turns were left out of the request after a compaction
 *   failure. Session-scoped; a later successful compaction replaces it.
 * - [lastUsage] is the latest chat request's usage for the usage line.
 */
data class AiChatState(
    val thread: AiChatThread?,
    val pendingQuestion: String?,
    val storeError: Boolean,
    val omittedUpTo: Int?,
    val lastUsage: AiUsage?,
) {
    companion object {
        fun idle() = AiChatState(
            thread = null,
            pendingQuestion = null,
            storeError = false,
            omittedUpTo = null,
            lastUsage = null,
        )
    }
}

/** Outcome of [AiChatEngine.send]. The settled state carries success or failure. */
sealed class AiChatSend {
    /** The turn was persisted, the request fired and the result is applied. */
    data class Settled(val state: AiChatState) : AiChatSend()

    /** No request went out: one at a time (design §Reliability), or no sendable session. */
    data class Refused(val reason: Reason) : AiChatSend()

    enum class Reason { BUSY, NO_THREAD }
}

/** Outcome of [AiChatEngine.retry]. */
sealed class AiChatRetry {
    /**
     * The request re-fired for the newest failed turn — in place, or as an
     * ask-again turn ([askAgain]) that quotes the question as a new send.
     */
    data class Fired(val state: AiChatState, val askAgain: Boolean) : AiChatRetry()

    /** Nothing to retry: no failed turn, or a request is already running. */
    object NotRetryable : AiChatRetry()
}

/** Outcome of [AiChatEngine.cancel]. */
sealed class AiChatCancel {
    /** The stored turn is gone and [restoredDraft] is back in the composer. */
    data class Cancelled(val restoredDraft: String, val threadDeleted: Boolean) : AiChatCancel()

    /** Nothing was in flight. */
    object NothingPending : AiChatCancel()
}

/**
 * The Context-free core of the Ask-AI chat screen (docs/design/ask-ai-chat.md
 * §Chat screen Cancel, §Reliability whole table, §Threads compaction bullets),
 * JVM-testable with a fake [AiProvider] and an in-memory [AiChatPersistence]:
 *
 * - **Persist before firing.** Send appends the user turn and writes the thread
 *   to the store BEFORE the request goes out, so process death leaves the turn
 *   on disk (§Reliability). A storage failure surfaces as [AiChatState.storeError]
 *   and never blocks the send — the turn stays visible in the working copy.
 * - **Failure marks the turn.** A provider error flips the turn to `failed`
 *   ("Not sent · Retry"): it stays in the transcript but is excluded from the AI
 *   context ([AiChatRequestBuilder]) and from the delivered-turn counts
 *   ([AiChatThreads.deliveredTurns]) until a retry succeeds.
 * - **Only the newest failed turn retries.** Once a newer turn exists, an older
 *   failed turn converts to "Ask again": the retry sends a NEW turn quoting the
 *   question, so history never orders a retry after later messages.
 * - **A fresh both-candidates thread is filed by its first reply.** [AiChatOpen.Fresh]
 *   carries an optional [AiChatOpen.Fresh.proposal]; until a reply lands every send
 *   rides the SUBJECT instruction, and the first successful reply is parsed, its
 *   SUBJECT line stripped before render, and a word verdict re-files the thread
 *   under the word candidate (rule fallback otherwise). A failed first reply keeps
 *   the thread provisional — the retry's reply is still parsed.
 * - **Cancel** removes the in-flight tile, deletes the stored turn (nothing of a
 *   cancelled turn remains) and restores the draft; a first-turn cancel deletes
 *   the thread. The late reply of a cancelled request is discarded.
 * - **Compaction runs after the answer lands**, never on a send's critical path:
 *   when the projected next request crosses 10k, the oldest turns are summarised
 *   FROM THE STORED ORIGINALS (never summary-of-summary) until the projection is
 *   ≤ 7k. A compaction failure is not a user-facing error: the oldest prose
 *   leaves the request and "Earlier turns omitted" marks the gap — the send is
 *   never blocked by compaction either way. A summarise result is applied only
 *   while the session still shows the thread it was computed for, and it never
 *   clears a send that fired meanwhile.
 * - **Counts never regress**: nothing here removes a delivered turn; a summary
 *   replaces its turns in the request only.
 *
 * Every state mutation runs under one lock and every fired request carries a
 * generation, so a cancel (or re-open) invalidates a reply that comes back late.
 * The engine never touches an [android.content.Context].
 */
class AiChatEngine(
    private val persistence: AiChatPersistence,
    private val provider: AiProvider,
    private val time: () -> Long = { System.currentTimeMillis() },
) {

    private val lock = Any()
    private val stateFlow = MutableStateFlow(AiChatState.idle())

    /** The session state, published after every step; the chat screen collects it. */
    val state: StateFlow<AiChatState> = stateFlow.asStateFlow()

    /** Bumped by every operation that must invalidate an in-flight request's result. */
    private var generation = 0L

    private var storeError = false

    /** What a first-turn cancel restores the session to. */
    private var freshSubject: AiChatSubject? = null
    private var freshGroundings: List<AiChatGrounding> = emptyList()

    /**
     * The alternative candidate still awaiting the thread's first reply (design
     * §Threads: a failed first reply is still followed by a parsed retry — the
     * thread stays provisional until a reply lands, so this survives settled
     * failures and only a successful reply, a cancel, or a re-open consumes it).
     */
    private var pendingProposal: AiChatSubject? = null

    /** Opens a session: a fresh, unpersisted chat or a stored thread by id. */
    fun open(open: AiChatOpen): AiChatState = synchronized(lock) {
        generation++
        storeError = false
        pendingProposal = null
        val thread = when (open) {
            is AiChatOpen.Fresh -> {
                freshSubject = open.subject
                freshGroundings = open.groundings
                pendingProposal = open.proposal
                null
            }
            is AiChatOpen.Resumed -> {
                freshSubject = null
                freshGroundings = emptyList()
                persistence.thread(open.threadId)?.let { repairTrailingUnansweredTurn(it) }
            }
        }
        publish(
            thread = thread,
            pendingQuestion = null,
            omittedUpTo = null,
            lastUsage = newestReplyUsage(thread),
        )
    }

    /**
     * Sends [question]: persists the user turn first, then fires the request.
     * [grounding], when present, joins the thread's facts and the user turn
     * carries its id ([AiChatMessage.groundingId] — the turn that introduced it).
     */
    suspend fun send(question: String, grounding: AiChatGrounding? = null): AiChatSend {
        val prepared = synchronized(lock) {
            val current = stateFlow.value
            if (current.pendingQuestion != null) {
                return@synchronized Prepared.Refuse(AiChatSend.Reason.BUSY)
            }
            val now = time()
            val base = current.thread
                ?: freshThread(current, now)
                ?: return@synchronized Prepared.Refuse(AiChatSend.Reason.NO_THREAD)
            val withGrounding = grounding?.let { base.copy(groundings = base.groundings + it) } ?: base
            val turn = AiChatMessage(
                role = AiChatMessage.Role.USER,
                text = question,
                ts = now,
                groundingId = grounding?.id,
            )
            val withTurn = withGrounding.copy(messages = withGrounding.messages + turn, updatedAt = now)
            // Design §Reliability: the question is persisted when Send fires.
            recordWrite(persistence.upsert(withTurn))
            generation++
            freshSubject = null
            freshGroundings = emptyList()
            publish(
                thread = withTurn,
                pendingQuestion = question,
                omittedUpTo = current.omittedUpTo,
                lastUsage = current.lastUsage,
            )
            Prepared.Fire(
                prompt = promptFor(withTurn, question, current.omittedUpTo),
                firedGeneration = generation,
            )
        }
        return when (prepared) {
            is Prepared.Refuse -> AiChatSend.Refused(prepared.reason)
            is Prepared.Fire -> {
                fire(prepared)
                AiChatSend.Settled(stateFlow.value)
            }
        }
    }

    /**
     * Retries the newest failed turn: in place while it is the thread's last
     * message, otherwise as an ask-again turn quoting the question (design
     * §Reliability — history never orders a retry after later messages).
     */
    suspend fun retry(): AiChatRetry {
        val prepared = synchronized(lock) {
            val current = stateFlow.value
            if (current.pendingQuestion != null) return AiChatRetry.NotRetryable
            val thread = current.thread ?: return AiChatRetry.NotRetryable
            val turn = thread.messages.lastOrNull { it.role == AiChatMessage.Role.USER && it.failed }
                ?: return AiChatRetry.NotRetryable
            val isLast = thread.messages.lastOrNull() == turn
            generation++
            if (isLast) {
                // In place: the failed turn stays where it is and waits again.
                publish(
                    thread = thread,
                    pendingQuestion = turn.text,
                    omittedUpTo = current.omittedUpTo,
                    lastUsage = current.lastUsage,
                )
                Prepared.Fire(promptFor(thread, turn.text, current.omittedUpTo), generation, askAgain = false)
            } else {
                // Ask again: a new turn quoting the question, persisted first.
                val now = time()
                val quoted = AiChatMessage(role = AiChatMessage.Role.USER, text = turn.text, ts = now)
                val withTurn = thread.copy(messages = thread.messages + quoted, updatedAt = now)
                recordWrite(persistence.upsert(withTurn))
                publish(
                    thread = withTurn,
                    pendingQuestion = turn.text,
                    omittedUpTo = current.omittedUpTo,
                    lastUsage = current.lastUsage,
                )
                Prepared.Fire(promptFor(withTurn, turn.text, current.omittedUpTo), generation, askAgain = true)
            }
        }
        fire(prepared)
        return AiChatRetry.Fired(stateFlow.value, askAgain = prepared.askAgain)
    }

    /** The in-flight request is cancelled: the stored turn is deleted, the draft restored. */
    suspend fun cancel(): AiChatCancel = synchronized(lock) {
        val current = stateFlow.value
        val thread = current.thread ?: return AiChatCancel.NothingPending
        val question = current.pendingQuestion ?: return AiChatCancel.NothingPending
        generation++
        val turn = thread.messages.lastOrNull()
        // The grounding the cancelled turn introduced goes with it — nothing of a
        // cancelled turn remains (design §Chat screen).
        val groundings = turn?.groundingId
            ?.let { id -> thread.groundings.filterNot { it.id == id } }
            ?: thread.groundings
        val trimmed = thread.copy(messages = thread.messages.dropLast(1), groundings = groundings, updatedAt = time())
        if (trimmed.messages.isEmpty()) {
            // First-turn cancel deletes the thread (design §Chat screen); the
            // session returns to the fresh chat it started as.
            recordWrite(persistence.delete(thread.id))
            freshSubject = thread.subject
            freshGroundings = thread.groundings
            publish(thread = null, pendingQuestion = null, omittedUpTo = null, lastUsage = null)
            AiChatCancel.Cancelled(restoredDraft = question, threadDeleted = true)
        } else {
            recordWrite(persistence.upsert(trimmed))
            publish(
                thread = trimmed,
                pendingQuestion = null,
                omittedUpTo = current.omittedUpTo,
                lastUsage = current.lastUsage,
            )
            AiChatCancel.Cancelled(restoredDraft = question, threadDeleted = false)
        }
    }

    // ---- firing and applying ---------------------------------------------------

    /**
     * Fires [prepared] and applies its result: success files the reply after the
     * pending turn (which becomes delivered), an error marks the turn failed.
     * Compaction runs afterwards, off the critical path.
     */
    private suspend fun fire(prepared: Prepared.Fire) {
        val result = provider.complete(
            AiRequest(prompt = prepared.prompt, jsonMode = false, readTimeoutMs = CHAT_READ_TIMEOUT_MS)
        )
        applyChatResult(prepared.firedGeneration, result)
        compactIfOverBudget()
    }

    /**
     * Applies the provider's reply to the request fired at [firedGeneration]. A
     * stale generation — the request was cancelled or the session re-opened —
     * changes nothing.
     */
    private fun applyChatResult(firedGeneration: Long, result: AiResult) {
        synchronized(lock) {
            if (firedGeneration != generation) return
            val thread = stateFlow.value.thread ?: return
            val pending = thread.messages.lastOrNull()
            if (pending == null || pending.role != AiChatMessage.Role.USER) return
            val now = time()
            val updated = when (result) {
                is AiResult.Ok -> {
                    // A fresh both-candidates thread is filed by its first reply
                    // (design §Threads): the SUBJECT line is parsed, stripped
                    // before render, and a word verdict re-files the thread under
                    // the word candidate; malformed or missing falls back to the
                    // rule subject the thread was created with.
                    val hadReply = thread.messages.any { it.role == AiChatMessage.Role.ASSISTANT }
                    val proposal = pendingProposal?.takeUnless { hadReply }
                    val parsed = if (proposal != null) {
                        pendingProposal = null
                        AiChatRequestBuilder.parseFirstReply(result.text)
                    } else {
                        null
                    }
                    var filed = thread.copy(
                        messages = thread.messages.dropLast(1) +
                            pending.copy(failed = false, errorKind = null) +
                            AiChatMessage(
                                role = AiChatMessage.Role.ASSISTANT,
                                text = parsed?.renderText ?: result.text,
                                ts = now,
                                usage = result.usage,
                            ),
                        updatedAt = now,
                    )
                    if (proposal != null && parsed?.subject == proposal.kind) {
                        filed = filed.copy(subject = proposal)
                    }
                    filed
                }
                is AiResult.Error -> thread.copy(
                    messages = thread.messages.dropLast(1) +
                        pending.copy(failed = true, errorKind = result.kind),
                    updatedAt = now,
                )
            }
            recordWrite(persistence.upsert(updated))
            publish(
                thread = updated,
                pendingQuestion = null,
                omittedUpTo = stateFlow.value.omittedUpTo,
                lastUsage = (result as? AiResult.Ok)?.usage ?: stateFlow.value.lastUsage,
            )
        }
    }

    // ---- compaction (design §Threads) --------------------------------------------

    /**
     * Runs after an answer lands: while the projected next request (no pending
     * question) crosses the 10k trigger, the oldest turns are summarised from the
     * STORED originals until the projection fits under 7k. When compaction cannot
     * run — the summarise call fails or returns nothing usable, or even full
     * coverage cannot fit — the oldest prose leaves the request and the state
     * records the omission. The full-view check keeps retrying the repair after
     * every answer even while a previous fallback keeps the actual request small.
     */
    private suspend fun compactIfOverBudget() {
        var passes = 0
        while (true) {
            val plan = synchronized(lock) {
                val thread = stateFlow.value.thread ?: return
                val projected = AiChatRequestBuilder.projectedRequestTokens(thread, newQuestion = null)
                val threshold = if (passes == 0) {
                    AiChatRequestBuilder.COMPACTION_TRIGGER_TOKENS
                } else {
                    AiChatRequestBuilder.POST_COMPACTION_TARGET_TOKENS
                }
                if (projected <= threshold) return
                val covers = AiChatRequestBuilder.compactionCoversUpTo(thread)
                when {
                    covers != null -> CompactionPlan.Summarise(thread, covers, generation)
                    // The 7k target is unreachable, but the request still fits
                    // under the trigger: keep it, never truncate for less.
                    projected <= AiChatRequestBuilder.COMPACTION_TRIGGER_TOKENS -> return
                    else -> CompactionPlan.Truncate(thread.id)
                }
            }
            when (plan) {
                is CompactionPlan.Summarise -> {
                    val result = provider.complete(
                        AiRequest(
                            prompt = AiChatRequestBuilder.buildSummarisePrompt(plan.thread, plan.coversUpTo),
                            jsonMode = false,
                            maxTokens = AiChatRequestBuilder.SUMMARY_MAX_TOKENS,
                            readTimeoutMs = CHAT_READ_TIMEOUT_MS,
                        )
                    )
                    val summaryText = (result as? AiResult.Ok)?.text?.trim().orEmpty()
                    if (summaryText.isEmpty()) {
                        applyTruncationFallback(plan.thread.id)
                        return
                    }
                    synchronized(lock) {
                        val current = stateFlow.value.thread ?: return
                        // The plan was computed for [plan.thread]: if the session
                        // switched threads while the summarise call was in flight
                        // (up to the read timeout), applying it would splice
                        // thread A's summary and coverage indexes onto thread B —
                        // bail instead, the next answer re-plans compaction.
                        if (current.id != plan.thread.id) return
                        val summary = AiChatSummary(text = summaryText, coversUpTo = plan.coversUpTo, createdAt = time())
                        val merged = current.copy(summaries = current.summaries + summary)
                        // A write failure keeps the summary in memory (a send is
                        // never blocked by compaction) and surfaces the error.
                        recordWrite(persistence.upsert(merged))
                        // A successful compaction replaces an earlier "Earlier
                        // turns omitted" marker with the summary's own marker.
                        publish(
                            thread = merged,
                            // A send may have fired while the summarise ran (a
                            // send is never blocked by compaction): keep its
                            // waiting state instead of clearing it.
                            pendingQuestion = stateFlow.value.pendingQuestion,
                            omittedUpTo = null,
                            // Design §Threads: the usage line reports the LATEST
                            // request, a compaction cost included — so when no
                            // send fired while the summarise ran (the fired
                            // generation still stands), its usage becomes the
                            // latest. A send that fired meanwhile reports its own
                            // usage when its reply lands.
                            lastUsage = if (plan.firedGeneration == generation) {
                                (result as? AiResult.Ok)?.usage ?: stateFlow.value.lastUsage
                            } else {
                                stateFlow.value.lastUsage
                            },
                        )
                    }
                    passes++
                    if (passes >= MAX_COMPACTION_PASSES) {
                        applyTruncationFallback(plan.thread.id)
                        return
                    }
                }
                is CompactionPlan.Truncate -> {
                    applyTruncationFallback(plan.threadId)
                    return
                }
            }
        }
    }

    /**
     * Compaction could not run: the oldest prose leaves the request ("Earlier
     * turns omitted"). [expectedThreadId], when given, is the thread the failed
     * summarise plan was computed for — if the session has since moved to
     * another thread, the omission must not mark the new thread's prose.
     */
    private fun applyTruncationFallback(expectedThreadId: String? = null) {
        synchronized(lock) {
            val thread = stateFlow.value.thread ?: return
            if (expectedThreadId != null && thread.id != expectedThreadId) return
            val omit = AiChatRequestBuilder.truncationOmitUpTo(thread) ?: return
            publish(
                thread = thread,
                pendingQuestion = stateFlow.value.pendingQuestion,
                omittedUpTo = omit,
                lastUsage = stateFlow.value.lastUsage,
            )
        }
    }

    // ---- internals -----------------------------------------------------------------

    /** The thread a send on a fresh session creates, or null when none is sendable. */
    private fun freshThread(current: AiChatState, now: Long): AiChatThread? {
        if (current.thread != null) return null
        val subject = freshSubject ?: return null
        return AiChatThread(
            id = AiChatThread.newId(),
            subject = subject,
            createdAt = now,
            updatedAt = now,
            groundings = freshGroundings,
        )
    }

    /** The request prompt for [thread] with [question] pending and any active omission. */
    private fun promptFor(thread: AiChatThread, question: String, omittedUpTo: Int?): String {
        return AiChatRequestBuilder.build(
            thread,
            newQuestion = question,
            // The both-candidates instruction rides every send of a thread whose
            // first reply has not landed yet (design §Threads).
            subjectProposal = pendingProposal != null,
            omitTurnsUpTo = omittedUpTo ?: AiChatRequestBuilder.NO_OMITTED_TURNS,
        )
    }

    /**
     * A resume that finds a trailing non-failed USER turn — process death between
     * the persisted send and the settled result (design §Reliability) — marks it
     * failed and persists the repair: by the `failed` ⇔ "Not sent · Retry"
     * contract the turn must not render as an ordinary sent question that no
     * answer is coming for, and retry() filters on `failed`.
     */
    private fun repairTrailingUnansweredTurn(thread: AiChatThread): AiChatThread {
        val last = thread.messages.lastOrNull() ?: return thread
        if (last.role != AiChatMessage.Role.USER || last.failed) return thread
        val repaired = thread.copy(
            messages = thread.messages.dropLast(1) + last.copy(failed = true),
            updatedAt = time(),
        )
        recordWrite(persistence.upsert(repaired))
        return repaired
    }

    /** Records a storage outcome; a failure surfaces as the inline store-error state. */
    private fun recordWrite(write: AiChatWrite) {
        storeError = write is AiChatWrite.Failed
    }

    private fun publish(
        thread: AiChatThread?,
        pendingQuestion: String?,
        omittedUpTo: Int?,
        lastUsage: AiUsage?,
    ): AiChatState {
        val published = AiChatState(
            thread = thread,
            pendingQuestion = pendingQuestion,
            storeError = storeError,
            omittedUpTo = omittedUpTo,
            lastUsage = lastUsage,
        )
        stateFlow.value = published
        return published
    }

    private fun newestReplyUsage(thread: AiChatThread?): AiUsage? {
        return thread?.messages
            ?.lastOrNull { it.role == AiChatMessage.Role.ASSISTANT && it.usage != null }
            ?.usage
    }

    /** Everything one fired request needs to apply its result. */
    private sealed class Prepared {
        data class Fire(
            val prompt: String,
            val firedGeneration: Long,
            val askAgain: Boolean = false,
        ) : Prepared()

        data class Refuse(val reason: AiChatSend.Reason) : Prepared()
    }

    private sealed class CompactionPlan {
        /** [firedGeneration] is the generation the summarise call rides (design §Threads usage line). */
        data class Summarise(
            val thread: AiChatThread,
            val coversUpTo: Int,
            val firedGeneration: Long,
        ) : CompactionPlan()

        /** [threadId] guards the fallback against a session that moved threads meanwhile. */
        data class Truncate(val threadId: String) : CompactionPlan()
    }

    companion object {
        /**
         * Chat replies are short prose (design §Chat screen), but a slow provider
         * gets the same room a background practice chunk gets — the user sees
         * "Thinking…" and can Cancel meanwhile.
         */
        const val CHAT_READ_TIMEOUT_MS = 300_000

        /**
         * Safety cap on the summarise-until-≤7k loop: each pass covers strictly
         * more originals, so the loop terminates; the cap only bounds a
         * pathological provider that keeps returning bloated summaries.
         */
        const val MAX_COMPACTION_PASSES = 3
    }
}
