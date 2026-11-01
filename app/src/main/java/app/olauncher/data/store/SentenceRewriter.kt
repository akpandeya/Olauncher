package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.time.LocalDate
import kotlinx.coroutines.sync.withLock

/** One pending rewrite: the saved [word] whose context to replace and the harvested [source] sentence. */
data class RewriteJob(
    val word: Word,
    val source: RecentSentence,
)

/** One rejected rewrite attempt: which [filter] dropped it, why, and on which attempt. */
data class RewriteRejection(
    val attempt: Int,
    val filter: String,
    val reason: String,
)

/** The end state of one [RewriteJob] in a rewrite run. */
sealed class RewriteOutcome {
    abstract val job: RewriteJob

    /** The rewrite passed parse, the guards and the context check; [attempts] calls were billed. */
    data class Accepted(
        override val job: RewriteJob,
        val text: String,
        val attempts: Int,
        val rejections: List<RewriteRejection>,
    ) : RewriteOutcome()

    /** All [MAX_ATTEMPTS] attempts were rejected; the source is consumed so it is never retried in a loop. */
    data class Discarded(
        override val job: RewriteJob,
        val rejections: List<RewriteRejection>,
    ) : RewriteOutcome()

    /** No call was made for this job. */
    data class Skipped(
        override val job: RewriteJob,
        val reason: Reason,
    ) : RewriteOutcome()

    /** The provider call failed; the source stays unconsumed so the next worker run retries it. */
    data class Failed(
        override val job: RewriteJob,
        val kind: AiResult.ErrorKind,
    ) : RewriteOutcome()

    /** Why a job made no call. */
    enum class Reason { DISABLED, NO_KEY, NO_MODEL, KEY_REJECTED, CAP_REACHED, STOPPED }
}

/** The outcomes of one rewrite run, in job order. */
data class RewriteRun(val outcomes: List<RewriteOutcome>) {
    val accepted: List<RewriteOutcome.Accepted> get() = outcomes.filterIsInstance<RewriteOutcome.Accepted>()
}

/**
 * The one rewrite loop behind `replaceWeakContexts` (issue #693): per saved word and harvested
 * sentence, ask the AI for a minimally edited sentence ([RewriteContract] prompt, [RewriteGuards]
 * checks) and store only a rewrite that is itself a strong context.
 *
 * Behaviour, mirroring [PracticeGenerator]:
 *
 * 1. Gate first ([AiPracticeGate]: feature on, key present, model set), then
 *    [AiPracticePrefs.aiKeyRejected]. Blocked → every job [RewriteOutcome.Skipped] with the reason,
 *    zero provider calls, no counter change.
 * 2. Jobs run in the given order. Each job's whole attempt loop holds
 *    [PracticeGenerator.generationMutex], so the daily cap's read-then-write stays safe when
 *    `NewsHarvestWorker` and `PracticeWorker` run concurrently.
 * 3. At most [MAX_ATTEMPTS] calls per job (1 + 2 reprompts). Before **each** call the remaining
 *    daily budget is checked ([AiPracticePrefs.aiDailyWordCap] minus [AiPracticePrefs.aiWordsSentToday]):
 *    exhausted → this job and all remaining jobs are [RewriteOutcome.Reason.CAP_REACHED] skipped and
 *    the run ends, the source not consumed. Otherwise one word is recorded as sent *before* sending
 *    (billed at send time, like [PracticeGenerator]), then the request goes out.
 * 4. A successful reply clears [AiPracticePrefs.aiKeyRejected] and [AiPracticePrefs.aiBillingBlocked];
 *    [RewriteContract.parse] rejections and [RewriteGuards] rejections are recorded as
 *    [RewriteRejection]s and reprompted with their reason. A rewrite that also passes the guards but
 *    would itself be a weak context ([ContextQuality.isWeak]) is rejected with filter `weak` the same
 *    way, so an accepted rewrite can always be stored. On acceptance the source is consumed exactly
 *    once via [markConsumed].
 * 5. After the third rejected attempt the source is consumed exactly once and the job is
 *    [RewriteOutcome.Discarded] — never retried in a loop.
 * 6. A provider error emits [RewriteOutcome.Failed] for this job (not consumed) and
 *    [RewriteOutcome.Reason.STOPPED] for all remaining jobs, then ends the run — no hot loop; the
 *    next worker run retries. AUTH sets [AiPracticePrefs.aiKeyRejected], BILLING sets
 *    [AiPracticePrefs.aiBillingBlocked].
 *
 * The key only travels via [keySource] ([KeySource]) and is never logged.
 */
class SentenceRewriter(
    private val provider: AiProvider,
    private val model: String,
    private val keySource: KeySource,
    private val matcher: SentenceWordMatcher,
    private val markConsumed: (sentenceId: String) -> Unit,
) {

    /**
     * Rewrites every [jobs] entry in order under the shared gate, daily word cap and
     * [PracticeGenerator.generationMutex]; returns the outcomes in job order.
     */
    suspend fun rewrite(jobs: List<RewriteJob>, prefs: AiPracticePrefs, today: LocalDate): RewriteRun {
        // The shared gate (also the settings screen's): off means no call, ever. A standing
        // key rejection pauses the rewriter too — the user must fix the key first.
        val blocked = when (AiPracticeGate.block(prefs.aiPracticeEnabled, !keySource.get().isNullOrBlank(), model)) {
            AiPracticeGate.Block.DISABLED -> RewriteOutcome.Reason.DISABLED
            AiPracticeGate.Block.NO_KEY -> RewriteOutcome.Reason.NO_KEY
            AiPracticeGate.Block.NO_MODEL -> RewriteOutcome.Reason.NO_MODEL
            null -> if (prefs.aiKeyRejected) RewriteOutcome.Reason.KEY_REJECTED else null
        }
        if (blocked != null) {
            return RewriteRun(jobs.map { RewriteOutcome.Skipped(it, blocked) })
        }

        val outcomes = mutableListOf<RewriteOutcome>()
        var index = 0
        while (index < jobs.size) {
            val job = jobs[index]
            when (val attempt = rewriteLocked(job, prefs, today)) {
                is Attempt.Done -> outcomes += attempt.outcome
                Attempt.CapReached -> {
                    // This job and all remaining jobs stay out of today's budget.
                    outcomes += jobs.drop(index).map { RewriteOutcome.Skipped(it, RewriteOutcome.Reason.CAP_REACHED) }
                    return RewriteRun(outcomes)
                }
                is Attempt.Errored -> {
                    // The provider call failed: stop instead of hot-looping; the next
                    // worker run retries the unconsumed sources.
                    outcomes += RewriteOutcome.Failed(job, attempt.kind)
                    outcomes += jobs.drop(index + 1).map { RewriteOutcome.Skipped(it, RewriteOutcome.Reason.STOPPED) }
                    return RewriteRun(outcomes)
                }
            }
            index++
        }
        return RewriteRun(outcomes)
    }

    /**
     * One job's attempt loop; the mutex makes the cap check and the sent-counter atomic.
     * Each attempt is billed at send time (one word), so a rejected or failed attempt still
     * counted against today's budget.
     */
    private suspend fun rewriteLocked(job: RewriteJob, prefs: AiPracticePrefs, today: LocalDate): Attempt {
        val rejections = mutableListOf<RewriteRejection>()
        var lastReason: String? = null
        var attempts = 0
        while (attempts < MAX_ATTEMPTS) {
            // The cap is a behavioral guardrail, never removed: an empty budget blocks
            // this and every remaining attempt, with the source unconsumed.
            if (prefs.aiDailyWordCap - prefs.aiWordsSentToday(today.toEpochDay()) <= 0) {
                return Attempt.CapReached
            }
            // Billed once the request body is written — a rejection or error still counted.
            prefs.recordAiWordsSent(1, today.toEpochDay())
            attempts++
            val result = provider.complete(
                AiRequest(
                    prompt = RewriteContract.buildPrompt(job.source.text, job.word.text, lastReason),
                    jsonMode = true,
                    maxTokens = RewriteContract.MAX_TOKENS,
                    readTimeoutMs = RewriteContract.READ_TIMEOUT_MS,
                ),
            )
            when (result) {
                is AiResult.Ok -> {
                    // A billed call clears the error states even before parsing.
                    prefs.aiKeyRejected = false
                    prefs.aiBillingBlocked = false
                    when (val parsed = RewriteContract.parse(result.text)) {
                        is RewriteParse.Rejected -> {
                            rejections += RewriteRejection(attempts, parsed.filter, parsed.reason)
                            lastReason = parsed.reason
                        }
                        is RewriteParse.Ok -> {
                            val verdict = RewriteGuards.check(job.source.text, parsed.text, job.word.text, matcher)
                            if (verdict.rejected) {
                                rejections += RewriteRejection(attempts, verdict.filter!!, verdict.reason!!)
                                lastReason = verdict.reason
                                continue
                            }
                            // The rewrite replaces the word's context sentence: it must
                            // itself be a strong context, or replaceWeakContexts could
                            // not store it.
                            if (ContextQuality.isWeak(parsed.text, job.word.text)) {
                                rejections += RewriteRejection(attempts, FILTER_WEAK, WEAK_CONTEXT_REASON)
                                lastReason = WEAK_CONTEXT_REASON
                                continue
                            }
                            markConsumed(job.source.id)
                            return Attempt.Done(
                                RewriteOutcome.Accepted(job, parsed.text, attempts, rejections),
                            )
                        }
                    }
                }
                is AiResult.Error -> {
                    when (result.kind) {
                        AiResult.ErrorKind.AUTH -> prefs.aiKeyRejected = true
                        AiResult.ErrorKind.BILLING -> prefs.aiBillingBlocked = true
                        else -> {}
                    }
                    return Attempt.Errored(result.kind)
                }
            }
        }
        // Every attempt was rejected: the source is consumed so this job is never
        // retried in a loop; the word keeps its (weak) context sentence.
        markConsumed(job.source.id)
        return Attempt.Done(RewriteOutcome.Discarded(job, rejections))
    }

    /** Internal loop result: a final outcome, or one of the two run-stopping conditions. */
    private sealed class Attempt {
        data class Done(val outcome: RewriteOutcome) : Attempt()
        object CapReached : Attempt()
        data class Errored(val kind: AiResult.ErrorKind) : Attempt()
    }

    companion object {
        /**
         * At most three provider calls per job: the first ask plus two reprompts, so a
         * stubbornly bad model cannot burn the daily word cap on one sentence.
         */
        const val MAX_ATTEMPTS = 3

        /** Filter id: the rewrite passed [RewriteGuards] but is itself a weak context ([ContextQuality.isWeak]). */
        const val FILTER_WEAK = "weak"

        /** Retry instruction for [FILTER_WEAK], phrased as a short fix like the guards' reasons. */
        const val WEAK_CONTEXT_REASON =
            "the rewrite is not a strong practice context — write a complete German sentence" +
                " (at least 4 words, a verb, sentence punctuation, no numbers or English words)"
    }
}
