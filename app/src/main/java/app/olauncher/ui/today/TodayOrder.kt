package app.olauncher.ui.today

import java.time.DayOfWeek

/** A card key with the priority it has right now. */
data class Ranked(val key: String, val priority: Int)

/** Context-free ranking rules for the Today cards. */
object TodayOrder {
    const val NEXT_UP_PRIORITY = 100
    const val HELD_PRIORITY = 95
    const val WEEK_PRIORITY = 90
    const val WELLBEING_PRIORITY = 70
    const val BASE_PRIORITY = 50

    const val NEXT_UP_WINDOW_MILLIS = 2 * 60 * 60 * 1000L
    const val WELLBEING_FROM_HOUR = 18

    /** Highest priority first; cards with equal priority keep their incoming order. */
    fun order(cards: List<Ranked>): List<Ranked> = cards.sortedByDescending { it.priority }

    fun defaultPriority(listIndex: Int): Int = BASE_PRIORITY - listIndex

    fun nextUpPriority(nextStartMillis: Long?, nowMillis: Long, fallback: Int): Int =
        if (nextStartMillis != null && nextStartMillis >= nowMillis && nextStartMillis - nowMillis <= NEXT_UP_WINDOW_MILLIS) {
            NEXT_UP_PRIORITY
        } else {
            fallback
        }

    fun heldPriority(anyHeld: Boolean, fallback: Int): Int = if (anyHeld) HELD_PRIORITY else fallback

    fun weekPriority(day: DayOfWeek, fallback: Int): Int =
        if (day == DayOfWeek.SUNDAY || day == DayOfWeek.MONDAY) WEEK_PRIORITY else fallback

    fun wellbeingPriority(hourOfDay: Int, fallback: Int): Int =
        if (hourOfDay >= WELLBEING_FROM_HOUR) WELLBEING_PRIORITY else fallback
}
