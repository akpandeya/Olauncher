package app.olauncher.data.store

/**
 * One planned fill that needs no AI call: the word keeps a linked harvested sentence as-is.
 */
data class AsIsFill(
    val word: Word,
    val sentence: RecentSentence,
)

/**
 * The selection outcome for one run: linked sentences used unchanged plus rewrite jobs
 * ([RewriteJob]) for words with no usable linked sentence. The run step that applies the
 * plan lives elsewhere; this planner only decides.
 */
data class ContextRewritePlan(
    val asIs: List<AsIsFill>,
    val rewrites: List<RewriteJob>,
)

/**
 * Chooses, word by word in [WordNeedRanker] order, how each eligible word gets a better
 * context sentence (issue #693). Word-first and cost-first:
 *
 * 1. A word that already has a linked harvested sentence ([RecentSentence.wordIds], the link
 *    [NewsBackfill] wrote during harvest) uses the newest strong one as-is — no AI call at
 *    all. Sentences whose text equals the word's current context (normalized) or that are
 *    themselves weak ([ContextQuality.isWeak]) are not candidates. Within the length window
 *    the first (newest) in-range sentence wins, else the first remaining one. Consumed state
 *    does not matter for an as-is fill — reusing a sentence for practice harms nothing.
 * 2. Only a word with no usable linked sentence and a NON-blank context becomes a
 *    [RewriteJob]: its weak context is worth an AI rewrite. A blank-context word never gets
 *    a job — it waits for a real linked sentence instead of paying for one.
 * 3. Rewrite sources come from the pool of unconsumed sentences in the same length window
 *    ([NewsBackfill.MIN_CONTEXT_CHARS]..[NewsBackfill.MAX_CONTEXT_CHARS]), newest
 *    `publishedAt` first, shortest text first on a tie. Each job takes the first pool
 *    sentence not already used by another job or as-is fill in this plan, so two jobs never
 *    share a source; at most [MAX_REWRITES_PER_RUN] jobs per run. Pool exhausted → no more
 *    jobs.
 *
 * Pure and Context-free: no store access, no I/O, deterministic for the same input.
 */
object ContextRewritePlanner {

    const val MIN_CHARS = NewsBackfill.MIN_CONTEXT_CHARS
    const val MAX_CHARS = NewsBackfill.MAX_CONTEXT_CHARS

    /** AI rewrites are capped per run so one worker pass cannot burn the daily word cap. */
    const val MAX_REWRITES_PER_RUN = 5

    fun plan(
        words: List<Word>,
        practiceCounts: Map<Long, Int>,
        sentences: List<RecentSentence>,
        maxRewrites: Int = MAX_REWRITES_PER_RUN,
    ): ContextRewritePlan {
        val candidates = WordNeedRanker.rank(words, practiceCounts)

        // Rewrite sources: unconsumed, in the context length window, newest first and
        // shortest first on a publishedAt tie.
        val sourcePool = sentences
            .filter { !it.consumed && it.text.length in MIN_CHARS..MAX_CHARS }
            .sortedWith(
                compareByDescending<RecentSentence> { it.publishedAt }
                    .thenBy { it.text.length }
            )

        val asIs = mutableListOf<AsIsFill>()
        val rewrites = mutableListOf<RewriteJob>()
        val usedSentenceIds = mutableSetOf<String>()

        for (word in candidates) {
            // Linked sentences for this word, newest first, minus the ones that are the
            // word's current context or too weak to learn from.
            val linked = sentences
                .filter { word.id in it.wordIds }
                .sortedByDescending { it.publishedAt }
                .filter { sentence ->
                    RecentSentencesCodec.normalizeText(sentence.text) !=
                        RecentSentencesCodec.normalizeText(word.context) &&
                        !ContextQuality.isWeak(sentence.text, word.text)
                }
            val fill = linked.firstOrNull { it.text.length in MIN_CHARS..MAX_CHARS }
                ?: linked.firstOrNull()
            if (fill != null) {
                asIs += AsIsFill(word, fill)
                usedSentenceIds += fill.id
            } else if (word.context.isNotBlank() && rewrites.size < maxRewrites) {
                val source = sourcePool.firstOrNull { it.id !in usedSentenceIds } ?: continue
                rewrites += RewriteJob(word, source)
                usedSentenceIds += source.id
            }
        }
        return ContextRewritePlan(asIs = asIs, rewrites = rewrites)
    }
}
