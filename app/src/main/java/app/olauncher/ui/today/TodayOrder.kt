package app.olauncher.ui.today

import app.olauncher.glance.CalendarEvent
import app.olauncher.glance.HomeNextEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** A card key with the priority it has right now. */
data class Ranked(val key: String, val priority: Int)

/**
 * Where the Today panels go: [pinned] sit at the top, right under the date; [stacked] fill the
 * bottom-up stack, the first (highest priority) one nearest the thumb.
 */
data class TodayPanelSplit(val pinned: List<String>, val stacked: List<String>)

data class TodayPanelMoves(val up: List<String>?, val down: List<String>?)

/** Context-free ranking rules for the Today cards. */
object TodayOrder {
    private const val WIDGET_PREFIX = "widget:"

    fun widgetKey(id: Int): String {
        require(id > 0)
        return "$WIDGET_PREFIX$id"
    }

    fun widgetId(key: String): Int? =
        key.removePrefix(WIDGET_PREFIX).toIntOrNull()
            ?.takeIf { key.startsWith(WIDGET_PREFIX) && it > 0 && widgetKey(it) == key }

    /** Saved keys may include hidden or uninstalled cards; new cards retain their default order. */
    fun arrange(cards: List<Ranked>, savedOrder: List<String>, pinnedKeys: Set<String>, smart: Boolean): TodayPanelSplit {
        val positions = savedOrder.withIndex().associate { it.value to it.index }
        val arranged = cards.sortedBy { positions[it.key] ?: Int.MAX_VALUE }
        val (pinned, rest) = arranged.partition { it.key in pinnedKeys }
        return TodayPanelSplit(pinned.map { it.key }, (if (smart) order(rest) else rest).map { it.key })
    }

    /** Move within the displayed group without disturbing hidden cards' saved positions. */
    fun move(keys: List<String>, key: String, offset: Int, groupKeys: List<String> = keys): List<String> {
        val group = keys.filter { it in groupKeys }
        val index = group.indexOf(key)
        val target = index + offset
        if (index < 0 || target !in group.indices) return keys
        val moved = group.toMutableList().apply { add(target, removeAt(index)) }.iterator()
        return keys.map { if (it in groupKeys) moved.next() else it }
    }

    /** Pinned panels render forward; the unpinned stack renders in reverse. Null hides a boundary action. */
    fun panelMoves(keys: List<String>, key: String, pinned: Boolean, groupKeys: List<String>): TodayPanelMoves {
        val upOffset = if (pinned) -1 else 1
        return TodayPanelMoves(
            up = move(keys, key, upOffset, groupKeys).takeUnless { it == keys },
            down = move(keys, key, -upOffset, groupKeys).takeUnless { it == keys },
        )
    }

    const val NEXT_UP_PRIORITY = 100
    const val HELD_PRIORITY = 95
    const val WEEK_PRIORITY = 90
    const val WELLBEING_PRIORITY = 70
    const val BASE_PRIORITY = 50

    const val LEGACY_WEEK_KEY = "week"
    private const val WELLBEING_KEY = "wellbeing"

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

    /**
     * Next up is pinned to the top of the page while a timed event is running now or starts later
     * today: a meeting is the thing to see first, not something to dig for at the bottom.
     * All-day and tomorrow-only events leave it in the normal stack.
     */
    fun nextUpPinned(events: List<CalendarEvent>, now: Instant, zone: ZoneId): Boolean =
        HomeNextEvent.pick(events, now, zone) != null

    /** The old "week" card now lives inside the Wellbeing card, so its keys map to the merged card. */
    fun canonicalKey(key: String): String = if (key == LEGACY_WEEK_KEY) WELLBEING_KEY else key

    /** Merged card on/off: each old card counts as its saved choice or its default; off only when both were off. */
    fun mergedEnabled(wellbeingSaved: Boolean?, weekSaved: Boolean?, wellbeingDefault: Boolean, weekDefault: Boolean): Boolean =
        (wellbeingSaved ?: wellbeingDefault) || (weekSaved ?: weekDefault)

    /** The Sunday/Monday boost of the old week card only applies while the 7 days side is shown. */
    fun mergedPriority(hourOfDay: Int, day: DayOfWeek, weekSideShown: Boolean, fallback: Int): Int =
        if (weekSideShown) weekPriority(day, fallback) else wellbeingPriority(hourOfDay, fallback)

    /** Pinned keys keep their incoming order; the rest are ranked by [order]. */
    fun split(cards: List<Ranked>, pinnedKeys: Set<String>): TodayPanelSplit {
        val (pinned, rest) = cards.partition { it.key in pinnedKeys }
        return TodayPanelSplit(pinned.map { it.key }, order(rest).map { it.key })
    }
}

/** Which side of the merged Wellbeing card shows. */
enum class WellbeingRange { TODAY, WEEK }

/** Context-free rules for the Today | 7 days switch of the Wellbeing card. */
object WellbeingRanges {
    /**
     * The range to show: the saved choice when its side has content, else the other side.
     * Null when neither has content.
     */
    fun resolve(savedWeek: Boolean, todayHasContent: Boolean, weekHasContent: Boolean): WellbeingRange? {
        val wanted = if (savedWeek) WellbeingRange.WEEK else WellbeingRange.TODAY
        val wantedHasContent = if (savedWeek) weekHasContent else todayHasContent
        return when {
            wantedHasContent -> wanted
            todayHasContent -> WellbeingRange.TODAY
            weekHasContent -> WellbeingRange.WEEK
            else -> null
        }
    }

    /** The switch only shows when both sides have something to show. */
    fun showSwitch(todayHasContent: Boolean, weekHasContent: Boolean): Boolean =
        todayHasContent && weekHasContent
}
