package app.olauncher.data.store

/**
 * Ranks saved words by how much they still need practice (issue #693): the fewest practice
 * cards first, so the next generation round spends its budget on the least-practised words.
 *
 * A word at or above [PRACTICE_CAP] cards is dropped here, before any sentence lookup or AI
 * budget is spent: nine cards ([PracticeContract.ITEMS_PER_WORD] is 3, so 9 is about three
 * generation rounds) mean the word is practised enough to leave alone for now. Per-rule
 * ranking is a later follow-up because practice cards carry no rule tag.
 *
 * Deliberately Context-free and pure: same input, same order, no store access.
 */
object WordNeedRanker {

    /**
     * A word with at least this many practice cards is not ranked for generation.
     * [PracticeContract.ITEMS_PER_WORD] is 3, so 9 cards is about three generation rounds.
     */
    const val PRACTICE_CAP = 9

    /** Same leading-article shape [SentenceWordMatcher] strips before matching. */
    private val ARTICLE_REGEX = Regex("""^(?i)(?:der|die|das|ein|eine)\s+""")

    /**
     * Keeps only words that still need work and orders them by need:
     *
     * 1. Keep a word only when [ContextQuality.isReplaceable] (blank or weak context, never
     *    a user-typed one), it is not learned, its text without a leading der/die/das/ein/eine
     *    still has [SentenceWordMatch.MIN_WORD_CHARS] characters (no practising "der" or "ei"),
     *    and its practice count is below [PRACTICE_CAP]. A missing [practiceCounts] entry
     *    counts as 0 cards.
     * 2. Order by practice count ascending (fewest cards first), then `addedAt` descending
     *    (newest word first among equals), then `id` ascending as the stable tiebreaker.
     */
    fun rank(words: List<Word>, practiceCounts: Map<Long, Int>): List<Word> =
        words.asSequence()
            .filter { word -> eligible(word, practiceCounts[word.id] ?: 0) }
            .sortedWith(
                compareBy<Word> { practiceCounts[it.id] ?: 0 }
                    .thenByDescending { it.addedAt }
                    .thenBy { it.id }
            )
            .toList()

    /** One word's keep/drop decision, given its practice card count. */
    private fun eligible(word: Word, practiceCount: Int): Boolean {
        if (practiceCount >= PRACTICE_CAP) return false
        if (word.learnedAt != null) return false
        if (!ContextQuality.isReplaceable(word)) return false
        val trimmed = word.text.trim()
        val stripped = ARTICLE_REGEX.replaceFirst(trimmed, "").trim()
            .ifEmpty { trimmed } // The text was only an article; judge the whole text instead.
        return stripped.length >= SentenceWordMatch.MIN_WORD_CHARS
    }
}
