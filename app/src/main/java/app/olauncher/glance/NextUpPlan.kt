package app.olauncher.glance

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** What the Next up card shows: [rows] in display order and how many more of today's events are [hiddenCount]. */
data class NextUpPlan(val rows: List<CalendarEvent>, val hiddenCount: Int)

object NextUpPlanner {
    const val DEFAULT_ROWS = 4
    const val MAX_ROWS = 6
    const val SOON_HOURS = 3
    const val FETCH_LIMIT = 50

    fun plan(
        events: List<CalendarEvent>,
        now: Instant,
        zone: ZoneId,
        expanded: Boolean = false,
    ): NextUpPlan {
        val today = now.atZone(zone).toLocalDate()
        val soonEnd = now.plusSeconds(SOON_HOURS * 3600L)

        val live = events
            .filter { !NextUpFormatting.hasEnded(it, now, zone) }
            .distinctBy { Triple(it.title, it.startMillis, it.allDay) }

        val soon = mutableListOf<CalendarEvent>()
        val allDayToday = mutableListOf<CalendarEvent>()
        val laterToday = mutableListOf<CalendarEvent>()
        val after = mutableListOf<CalendarEvent>()

        for (e in live) {
            if (e.allDay) {
                if (localStartDate(e.startMillis, zone) <= today && today < localEndDate(e.endMillis, zone)) {
                    allDayToday += e
                } else {
                    after += e
                }
            } else if (!e.start.isAfter(soonEnd)) {
                soon += e
            } else if (e.start.atZone(zone).toLocalDate() == today) {
                laterToday += e
            } else {
                after += e
            }
        }

        val order = compareBy<CalendarEvent>({ it.startMillis }, { it.endMillis })
        val head = soon.sortedWith(order) + allDayToday.sortedWith(order) + laterToday.sortedWith(order)
        val tail = after.sortedWith(order)
        val total = head.size

        return when {
            total <= DEFAULT_ROWS -> NextUpPlan(head + tail.take(DEFAULT_ROWS - total), 0)
            total <= MAX_ROWS -> NextUpPlan(head, 0)
            expanded -> NextUpPlan(head, total - MAX_ROWS)
            else -> NextUpPlan(head.take(MAX_ROWS), total - MAX_ROWS)
        }
    }

    // All-day events use UTC-midnight bounds; same rule as NextUpFormatting.line.
    private fun localStartDate(millis: Long, zone: ZoneId): LocalDate = allDayDate(millis, zone)

    private fun localEndDate(millis: Long, zone: ZoneId): LocalDate = allDayDate(millis, zone)

    private fun allDayDate(millis: Long, zone: ZoneId): LocalDate {
        val utc = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)
        return if (utc.hour == 0 && utc.minute == 0) utc.toLocalDate()
        else Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    }
}
