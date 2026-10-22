package app.olauncher.helper

/** New gestures go at the end: the stored hint index is a position in this order. */
enum class Gesture { SWIPE_RIGHT, SWIPE_LEFT, DOUBLE_TAP, LONG_PRESS, SWIPE_UP, SWIPE_DOWN, TAP_MUSIC, TAP_EVENT }

data class Hint(val gesture: Gesture, val position: Int)

/** Teaches one gesture per day, in a fixed order, skipping gestures that are off. */
object GestureHints {

    /** An index past every hint: the sequence is over. */
    const val DONE = Int.MAX_VALUE

    val order: List<Gesture> = Gesture.values().toList()

    /**
     * The hint to show now, or null. [index] is the first position not yet taught,
     * [lastShownDay] the epoch day of the last hint. After showing [Hint.position],
     * store `position + 1` as the new index.
     */
    fun next(index: Int, lastShownDay: Long, today: Long, enabled: Set<Gesture>): Hint? {
        if (lastShownDay == today) return null
        for (position in index.coerceAtLeast(0) until order.size) {
            if (order[position] in enabled) return Hint(order[position], position)
        }
        return null
    }
}
