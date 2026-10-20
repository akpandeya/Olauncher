package app.olauncher.glance

import java.time.Instant
import java.time.ZoneId

/** The single line home shows for the next timed event today. */
data class HomeNextEventLine(val text: String, val eventId: Long)

object HomeNextEvent {

    /** Earliest timed, titled event today that is running now or starts later today; null if none. */
    fun pick(events: List<CalendarEvent>, now: Instant, zone: ZoneId): CalendarEvent? {
        val today = now.atZone(zone).toLocalDate()
        return events
            .filter { !it.allDay && it.title.isNotBlank() }
            .filter { !NextUpFormatting.hasEnded(it, now, zone) }
            .filter { it.start.atZone(zone).toLocalDate() <= today }
            .minWithOrNull(compareBy<CalendarEvent>({ it.startMillis }, { it.endMillis }))
    }

    /** Home shows the line only when the pref is on, calendar permission is granted, and there is an event. */
    fun shouldShow(prefOn: Boolean, permissionGranted: Boolean, line: HomeNextEventLine?): Boolean =
        prefOn && permissionGranted && line != null

    fun line(
        events: List<CalendarEvent>,
        now: Instant,
        zone: ZoneId,
        is24Hour: Boolean,
        nowTemplate: String,
    ): HomeNextEventLine? {
        val event = pick(events, now, zone) ?: return null
        val text = if (!event.start.isAfter(now)) {
            String.format(nowTemplate, event.title, NextUpFormatting.formatTime(event.end.atZone(zone), is24Hour))
        } else {
            "${NextUpFormatting.formatTime(event.start.atZone(zone), is24Hour)} · ${event.title}"
        }
        return HomeNextEventLine(text, event.id)
    }
}
