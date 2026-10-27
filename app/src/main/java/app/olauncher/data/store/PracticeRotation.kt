package app.olauncher.data.store

/**
 * Rotation over a word's practice pool (#603, 9.10; docs/design/ai-practice.md
 * "Pipeline" stage 5). Scheduling stays on the word (#452) — rotation adds no
 * state of its own: the pool index is the word's review count modulo the pool
 * size, so the cursor is derived from the review event log and survives a store
 * reload. An empty pool returns null, and the caller keeps the existing
 * non-AI word content (the items are additive, never a replacement).
 */
object PracticeRotation {

    /**
     * The [word]'s item for its next review: the pool (the word's items in stored
     * order) at index = review count mod pool size. Deterministic and testable —
     * the same [reviewEventsForWord] always picks the same item.
     */
    fun pick(
        word: Word,
        items: List<PracticeItem>,
        reviewEventsForWord: List<ReviewEvent>,
    ): PracticeItem? {
        return pick(word, items, reviewEventsForWord.size)
    }

    /** [reviewCount] form of [pick] for callers that already counted the events. */
    fun pick(word: Word, items: List<PracticeItem>, reviewCount: Int): PracticeItem? {
        val pool = items.filter { it.wordId == word.id }
        if (pool.isEmpty()) return null
        return pool[reviewCount.mod(pool.size)]
    }
}
