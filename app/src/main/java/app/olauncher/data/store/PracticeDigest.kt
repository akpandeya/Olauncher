package app.olauncher.data.store

/**
 * The digest (#604, 9.11; docs/design/ai-practice.md "Digest cadence"): weekly
 * OR threshold-triggered, whichever first (owner decision, 2026-10-27 — was
 * fortnightly). One batched call turns the weak spots found in the review event
 * log and the drill scores into new practice items. All of this is context-free
 * and derived — [due] reads the event log and the `AI_LAST_DIGEST_AT` timestamp,
 * nothing else is stored here.
 */
object PracticeDigest {

    /** Weekly cadence: a digest is due when this much time passed since the last one. */
    const val CADENCE_MS: Long = 7L * 24 * 60 * 60 * 1000

    /** The weak-spot look-back window for review events. */
    const val WINDOW_MS: Long = 14L * 24 * 60 * 60 * 1000

    /** A word with at least this many AGAIN in the window is a weak spot. */
    const val MIN_AGAIN_FOR_WEAK_WORD = 2

    /** A drill type below this first-try percentage is a weak spot. */
    const val WEAK_DRILL_PERCENT = 70

    /**
     * Whether a digest should run now: the weekly cadence elapsed (`AI_LAST_DIGEST_AT`
     * of 0 means never digested), or the weak-spot threshold in the review events
     * fires earlier — a word with at least [MIN_AGAIN_FOR_WEAK_WORD] AGAIN within
     * [WINDOW_MS] of [now]. Whichever comes first.
     */
    fun due(lastDigestAt: Long, now: Long, reviewEvents: List<ReviewEvent>): Boolean {
        if (lastDigestAt <= 0L || now - lastDigestAt >= CADENCE_MS) return true
        return weakWordIds(reviewEvents, now).isNotEmpty()
    }

    /**
     * The words with at least [MIN_AGAIN_FOR_WEAK_WORD] AGAIN in the
     * [WINDOW_MS] window before [now], ascending by word id.
     */
    fun weakWordIds(reviewEvents: List<ReviewEvent>, now: Long): List<Long> {
        return reviewEvents.asSequence()
            .filter { it.result == ReviewEvent.Result.AGAIN && it.reviewedAt <= now && now - it.reviewedAt <= WINDOW_MS }
            .groupBy { it.wordId }
            .filterValues { it.size >= MIN_AGAIN_FOR_WEAK_WORD }
            .keys
            .sorted()
            .toList()
    }

    /**
     * The weak spots of one digest: the weak words from the review events plus
     * the drill types below [WEAK_DRILL_PERCENT] first try (from the
     * `DRILL_SCORES` map, #577), ascending by [DrillType] declaration order.
     */
    fun summary(
        reviewEvents: List<ReviewEvent>,
        drillScores: Map<String, DrillScores.Score>,
        now: Long,
    ): WeakSpots {
        return WeakSpots(
            wordIds = weakWordIds(reviewEvents, now),
            drillTypes = DrillType.values().filter { type ->
                val score = drillScores[type.id]
                score != null && score.asked > 0 && score.percentage < WEAK_DRILL_PERCENT
            },
        )
    }

    /** The weak spots one digest turns into practice items. */
    data class WeakSpots(
        val wordIds: List<Long>,
        val drillTypes: List<DrillType>,
    ) {
        val isEmpty: Boolean get() = wordIds.isEmpty() && drillTypes.isEmpty()
    }
}
