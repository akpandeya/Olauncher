package app.olauncher.data.store

/**
 * What makes two stored practice items the same card for the learner: the same
 * word, type, prompt and answer. Hint, choices, source and model aside, the
 * learner sees and says the same thing, so a second copy adds nothing.
 */
data class PracticeCardKey(
    val wordId: Long,
    val type: DrillType,
    val prompt: String,
    val answer: String,
) {
    companion object {
        fun of(item: PracticeItem) = PracticeCardKey(item.wordId, item.type, item.prompt, item.answer)
    }
}

/**
 * The read-time view of `practice_items.json` (#749 follow-up): stored items are
 * frozen at creation and never re-validated, so cards written by older builds can
 * be duplicates, bare gaps or cards a later filter rejects. This view hides them
 * without deleting anything from the store:
 *
 * - an exact duplicate of an earlier visible card ([PracticeCardKey]; the
 *   earliest visible copy stays, so a hidden copy never hides a valid one),
 * - a bare `___` prompt (built before #750 gave the cloze fallback its cue),
 * - an AI card (model set) that fails [PracticeFilters.check] run on its stored
 *   fields — no `full` sentence is stored, and the cloze-duplicate check is
 *   skipped. Locally built fallbacks (model null) skip the filters: the contract
 *   builds them and never ran the filters on them.
 *
 * A known word whose stored items are all hidden gets one in-memory cloze
 * fallback ([PracticeContract.fallbacksFor], id 0), so it keeps a card.
 */
object PracticeVisibleItems {

    /** The visible items in stored order, then one fallback per word left without a card. */
    fun of(items: List<PracticeItem>, words: List<Word>): List<PracticeItem> {
        val wordsById = words.associateBy { it.id }
        val seen = HashSet<PracticeCardKey>()
        val visible = mutableListOf<PracticeItem>()
        val storedWordIds = LinkedHashSet<Long>()
        val visibleWordIds = HashSet<Long>()
        for (item in items) {
            storedWordIds += item.wordId
            // Hidden first, then the copy check: the first VISIBLE copy wins, so
            // a hidden AI card (a hint leak, say) never hides a later valid
            // fallback with the same prompt and answer.
            if (isHidden(item, wordsById[item.wordId])) continue
            if (!seen.add(PracticeCardKey.of(item))) continue
            visible += item
            visibleWordIds += item.wordId
        }
        for (wordId in storedWordIds) {
            if (wordId in visibleWordIds) continue
            val word = wordsById[wordId] ?: continue
            val source = items.first { it.wordId == wordId }.source ?: PracticeItem.Source.WORD
            visible += PracticeContract.fallbacksFor(word, 1, source)
        }
        return visible
    }

    /** Bare gap, or an AI card the current filters reject. Duplicates are [of]'s job. */
    private fun isHidden(item: PracticeItem, word: Word?): Boolean {
        if (item.prompt.trim() == DrillItem.GAP_MARKER) return true
        if (item.model == null) return false
        return PracticeFilters.check(
            type = item.type,
            prompt = item.prompt,
            answer = item.answer,
            hint = item.hint,
            full = null,
            clozeAnswers = emptySet(),
            word = word?.text,
        ).rejected
    }
}
