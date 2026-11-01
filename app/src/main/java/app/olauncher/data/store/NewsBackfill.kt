package app.olauncher.data.store

data class NewsBackfillPlan(
    val links: Map<String, List<Long>>,
    val contextFills: Map<Long, String>
)

object NewsBackfill {
    const val MAX_SENTENCES_PER_WORD = 5
    const val MIN_CONTEXT_CHARS = 40
    const val MAX_CONTEXT_CHARS = 200

    /**
     * The words backfill may touch in one run: not learned and fewer than
     * [WordNeedRanker.PRACTICE_CAP] practice cards. A capped or learned word does
     * not need practice, so it gets no sentence link and no context fill — the
     * worker passes this filtered list to [plan] instead of every stored word.
     */
    fun eligibleWords(words: List<Word>, practiceCounts: Map<Long, Int>): List<Word> =
        words.filter { word ->
            word.learnedAt == null && (practiceCounts[word.id] ?: 0) < WordNeedRanker.PRACTICE_CAP
        }

    fun plan(
        words: List<Word>,
        newSentences: List<RecentSentence>,
        matcher: SentenceWordMatcher
    ): NewsBackfillPlan {
        val byWord = matcher.candidates(words, newSentences, MAX_SENTENCES_PER_WORD)
        val inverted = LinkedHashMap<String, MutableList<Long>>()
        for ((wordId, matchedSentences) in byWord) {
            for (sentence in matchedSentences) {
                inverted.getOrPut(sentence.id) { mutableListOf() }.add(wordId)
            }
        }
        val links = inverted.mapValues { (_, wordIds) -> wordIds.distinct().sorted() }

        val contextFills = LinkedHashMap<Long, String>()
        for (word in words) {
            if (word.context.isNotBlank()) continue
            val candidates = byWord[word.id] ?: continue
            if (candidates.isEmpty()) continue
            val chosen = candidates.firstOrNull { it.text.length in MIN_CONTEXT_CHARS..MAX_CONTEXT_CHARS }
                ?: candidates.first()
            contextFills[word.id] = chosen.text
        }

        return NewsBackfillPlan(links = links, contextFills = contextFills)
    }

    fun applyPlan(
        plan: NewsBackfillPlan,
        sentences: RecentSentencesStore,
        words: WordsStore
    ): Int {
        if (plan.links.isNotEmpty()) {
            sentences.linkWords(plan.links)
        }
        // One atomic read-modify-write: a context the user captured after plan() is kept, and
        // a review that changed box/dueDay in the meantime is not reverted.
        return words.fillBlankContexts(plan.contextFills).size
    }
}
