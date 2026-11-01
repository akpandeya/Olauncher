package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.time.LocalDate

/**
 * What one [ContextRewriteRun] pass did: the ids actually replaced per path
 * ([asIsFilled], [rewritten]) and the rewrite outcomes that produced no stored
 * context ([discarded] after all attempts were rejected, [skipped] with no call
 * at all). Provider [AiResult.ErrorKind] failures are billed but counted here
 * nowhere — the next worker run retries them.
 */
data class ContextRewriteSummary(
    val asIsFilled: Int,
    val rewritten: Int,
    val discarded: Int,
    val skipped: Int,
)

/**
 * The apply step of the context rewrite flow (issue #693): takes the plan from
 * [ContextRewritePlanner] and persists it — weak or blank word contexts are
 * filled from a linked harvested sentence as-is, or from an AI rewrite the
 * [SentenceRewriter] already gated and billed. Context-free apart from the four
 * stores: the worker builds them and the rewriter, then calls [run] once.
 *
 * Ordering matters for crash safety. A paid-for rewrite is recorded in
 * [PracticeSentencesStore] *before* it is applied, so a crash between the two
 * writes loses nothing the user paid for; `replaceWeakContexts` is a
 * compare-and-replace on the planned context, so a word whose context the user
 * edited since planning keeps the user's sentence — the paid rewrite stays in
 * the practice history with its [PracticeSentence.originalContext], it is just
 * not applied. As-is fills replace first and record only the ids that were
 * actually replaced, so an unreplaceable word never gets a phantom history
 * entry. As-is fills use the sentence unchanged and never consume it — reusing
 * it for practice harms nothing — while a rewrite's source is consumed inside
 * the rewriter.
 *
 * No gate or cap logic lives here: with `rewriter == null` (feature off, no key
 * or unknown provider id) only as-is fills run; every rewrite call passes
 * through the rewriter's shared gate, daily word cap and generation mutex.
 */
class ContextRewriteRun(
    private val words: WordsStore,
    private val sentences: RecentSentencesStore,
    private val practiceItems: PracticeItemsStore,
    private val practiceSentences: PracticeSentencesStore,
    private val rewriter: SentenceRewriter?,
    private val model: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Plans from the current store contents and applies it; returns what the
     * pass actually wrote.
     */
    suspend fun run(prefs: AiPracticePrefs, today: LocalDate): ContextRewriteSummary {
        val practiceCounts = practiceItems.all().groupingBy { it.wordId }.eachCount()
        val plan = ContextRewritePlanner.plan(words.all(), practiceCounts, sentences.all())

        // One compare-and-replace for the whole as-is plan; a word the user
        // edited since planning is dropped here, before any history entry.
        val asIsReplaced = words.replaceWeakContexts(
            plan.asIs.associate { fill ->
                fill.word.id to ContextReplacement(
                    expectedContext = fill.word.context,
                    newContext = fill.sentence.text,
                )
            },
        )
        for (fill in plan.asIs) {
            if (fill.word.id !in asIsReplaced) continue
            practiceSentences.add(
                PracticeSentence(
                    wordId = fill.word.id,
                    sourceSentenceId = fill.sentence.id,
                    text = fill.sentence.text,
                    originalContext = fill.word.context,
                    model = "",
                    createdAt = clock(),
                ),
            )
        }

        var rewritten = 0
        var discarded = 0
        var skipped = 0
        if (rewriter != null && plan.rewrites.isNotEmpty()) {
            val rewriteRun = rewriter.rewrite(plan.rewrites, prefs, today)
            val accepted = rewriteRun.accepted
            // Record every accepted rewrite first: the call was paid for even if
            // the compare-and-replace below refuses it because the word's context
            // changed since planning.
            for (outcome in accepted) {
                practiceSentences.add(
                    PracticeSentence(
                        wordId = outcome.job.word.id,
                        sourceSentenceId = outcome.job.source.id,
                        text = outcome.text,
                        originalContext = outcome.job.word.context,
                        model = model,
                        createdAt = clock(),
                    ),
                )
            }
            rewritten = words.replaceWeakContexts(
                accepted.associate { outcome ->
                    outcome.job.word.id to ContextReplacement(
                        expectedContext = outcome.job.word.context,
                        newContext = outcome.text,
                    )
                },
            ).size
            discarded = rewriteRun.outcomes.count { it is RewriteOutcome.Discarded }
            skipped = rewriteRun.outcomes.count { it is RewriteOutcome.Skipped }
        }

        return ContextRewriteSummary(
            asIsFilled = asIsReplaced.size,
            rewritten = rewritten,
            discarded = discarded,
            skipped = skipped,
        )
    }
}
