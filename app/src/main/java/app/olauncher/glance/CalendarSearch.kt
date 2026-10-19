package app.olauncher.glance

import app.olauncher.data.AppModel
import app.olauncher.helper.AppMatcher
import java.time.Instant
import java.time.ZoneId

/** Source of calendar events in a time window. The Android implementation reads CalendarContract; tests use a fake. */
interface CalendarEventSource {
    fun events(fromMillis: Long, toMillis: Long): List<CalendarEvent>
}

object CalendarSearch {
    const val MIN_QUERY_LENGTH = 3
    const val MAX_ROWS = 2
    const val MIN_SCORE = AppMatcher.SCORE_SUBSTRING
    const val WINDOW_DAYS = 30L

    /** Events not yet over in [fromMillis, toMillis] whose title scores >= [MIN_SCORE]; best first, then soonest; at most [max]. */
    fun search(
        source: CalendarEventSource,
        query: String,
        fromMillis: Long,
        toMillis: Long,
        max: Int = MAX_ROWS,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEvent> {
        val q = query.trim()
        if (q.length < MIN_QUERY_LENGTH || max <= 0) return emptyList()
        val now = Instant.ofEpochMilli(fromMillis)
        return source.events(fromMillis, toMillis)
            .asSequence()
            .filter { it.title.isNotBlank() }
            .filter { it.startMillis < toMillis }
            .filter { !NextUpFormatting.hasEnded(it, now, zone) }
            .mapNotNull { e ->
                val score = AppMatcher.score(e.title, q) ?: return@mapNotNull null
                if (score < MIN_SCORE) null else e to score
            }
            .sortedWith(
                compareByDescending<Pair<CalendarEvent, Int>> { it.second }
                    .thenBy { it.first.startMillis }
            )
            .take(max)
            .map { it.first }
            .toList()
    }

    /** "Dentist · Thu 14:00" with a calendar emoji in front. */
    fun toResult(
        event: CalendarEvent,
        nowMillis: Long,
        zone: ZoneId,
        is24Hour: Boolean,
        labels: NextUpLabels,
    ): AppModel.Result {
        val row = NextUpFormatting.row(event, Instant.ofEpochMilli(nowMillis), zone, is24Hour, labels)
        val body = if (row.lead.isEmpty()) row.title else "${row.title} · ${row.lead}"
        return AppModel.Result(
            text = "📅 $body",
            action = AppModel.Result.Action.Event(event.id),
        )
    }
}
