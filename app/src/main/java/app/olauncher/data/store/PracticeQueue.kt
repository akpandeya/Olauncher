package app.olauncher.data.store

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * The generation queue (#601, 9.8; docs/design/ai-practice.md "Pipeline"): derived
 * from the saved words and the stored practice items — there is no file, so nothing
 * needs backing up. A word is queued while its pool has fewer than
 * [PracticeContract.ITEMS_PER_WORD] items (the background top-up restores every
 * word's pool to 3), oldest first with jitter, under the daily word cap.
 *
 * A word whose pool already holds its own cloze fallback card is stocked too:
 * the store skips identical cards, so a word the AI could not fill keeps only one
 * fallback copy instead of three, and re-queuing it would only resend it on every
 * run for the same fallback (see [isStocked]).
 *
 * Words with a blank `context` are never queued: nothing is generated without
 * grounding. Deleted words drop out automatically because the queue is derived.
 */
object PracticeQueue {

    /**
     * The jitter window: a word's sort key is its `addedAt` plus a random offset in
     * [0, [JITTER_MS]), so two words can only swap when their `addedAt` is less than
     * the window apart. A word never jumps ahead of one older by more than that.
     */
    const val JITTER_MS = 60_000L

    /**
     * The batch of words to generate for now: pool-thin, oldest first with jitter,
     * and bounded by [cap] minus the words that already got items today. [today] and
     * the stored items decide the cap; the caller's persisted sent-counter (see
     * [app.olauncher.data.AiPracticePrefs.aiWordsSentToday]) is the second, stricter
     * guard applied before any request goes out.
     */
    fun next(
        words: List<Word>,
        items: List<PracticeItem>,
        today: LocalDate,
        cap: Int,
        random: Random,
    ): List<Word> {
        val remaining = cap - wordsGeneratedToday(items, today)
        if (remaining <= 0) return emptyList()
        val pools = items.groupBy { it.wordId }
        return words.asSequence()
            .filter { it.context.isNotBlank() }
            .filter { !isStocked(it, pools[it.id].orEmpty()) }
            .map { it to (it.addedAt + random.nextLong(JITTER_MS)) }
            .sortedBy { it.second }
            .map { it.first }
            .take(remaining)
            .toList()
    }

    /**
     * Whether any of [words] is short of a full pool: a non-blank context and
     * not [isStocked] — the words [next] would queue, ignoring the cap. Review uses it to decide whether to start a
     * background run (#725); it never generates.
     */
    fun anyNeedsItems(words: List<Word>, items: List<PracticeItem>): Boolean {
        val pools = items.groupBy { it.wordId }
        return words.any { it.context.isNotBlank() && !isStocked(it, pools[it.id].orEmpty()) }
    }

    /**
     * A word's [pool] is stocked when it has [PracticeContract.ITEMS_PER_WORD]
     * items, or when it already holds the word's own cloze fallback card (same
     * [PracticeCardKey]): a run that could not fill the pool has padded it with
     * that fallback. Before the store skipped identical cards the padding was
     * three copies, which kept the word out of the queue; this keeps it out
     * without the copies. A changed context gives a different fallback card,
     * so such a word is queued again.
     */
    internal fun isStocked(word: Word, pool: List<PracticeItem>): Boolean {
        if (pool.size >= PracticeContract.ITEMS_PER_WORD) return true
        if (pool.isEmpty()) return false
        val fallback = PracticeCardKey.of(PracticeContract.fallbacksFor(word, 1, PracticeItem.Source.WORD).first())
        return pool.any { PracticeCardKey.of(it) == fallback }
    }

    /** Distinct words that got items (generated or fallback) on [today]. */
    private fun wordsGeneratedToday(items: List<PracticeItem>, today: LocalDate): Int {
        val dayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return items.asSequence()
            .filter { it.createdAt >= dayStart }
            .map { it.wordId }
            .distinct()
            .count()
    }
}
