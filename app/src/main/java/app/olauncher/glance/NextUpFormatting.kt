package app.olauncher.glance

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NextUpLabels(
    val today: String,
    val tomorrow: String,
    /** Template with %1$s = title, %2$s = end time. */
    val nowTemplate: String,
    /** Template with %1$s = start time, %2$s = title. */
    val tomorrowEventTemplate: String,
) {
    fun now(title: String, endTime: String): String = String.format(nowTemplate, title, endTime)
    fun tomorrowEvent(startTime: String, title: String): String = String.format(tomorrowEventTemplate, startTime, title)
}

/** An event line split for a two-column row: [lead] is the time or day, [title] the rest. */
data class NextUpRow(val lead: String, val title: String)

object NextUpFormatting {

    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.US)

    fun formatTime(time: ZonedDateTime, is24Hour: Boolean): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return time.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
    }

    /**
     * True when [event] is over at [now] in [zone]. All-day events are stored with UTC-midnight
     * bounds, so their end is moved to local midnight of the same date; otherwise a finished
     * all-day event lingers (east of UTC) or a current one drops out early (west of UTC).
     */
    fun hasEnded(event: CalendarEvent, now: Instant, zone: ZoneId): Boolean {
        val end = if (event.allDay) {
            val utcEnd = Instant.ofEpochMilli(event.endMillis).atZone(ZoneOffset.UTC)
            if (utcEnd.hour == 0 && utcEnd.minute == 0) {
                utcEnd.toLocalDate().atStartOfDay(zone).toInstant()
            } else {
                event.end
            }
        } else {
            event.end
        }
        return !now.isBefore(end)
    }

    fun line(
        event: CalendarEvent,
        now: Instant,
        zone: ZoneId,
        is24Hour: Boolean = true,
        labels: NextUpLabels,
    ): String {
        val nowZdt = now.atZone(zone)
        val today = nowZdt.toLocalDate()
        val tomorrow = today.plusDays(1)

        if (event.allDay) {
            val utcStart = Instant.ofEpochMilli(event.startMillis).atZone(ZoneOffset.UTC)
            val eventStartDate = if (utcStart.hour == 0 && utcStart.minute == 0) {
                utcStart.toLocalDate()
            } else {
                Instant.ofEpochMilli(event.startMillis).atZone(zone).toLocalDate()
            }

            val utcEnd = Instant.ofEpochMilli(event.endMillis).atZone(ZoneOffset.UTC)
            val eventEndDate = if (utcEnd.hour == 0 && utcEnd.minute == 0) {
                utcEnd.toLocalDate()
            } else {
                Instant.ofEpochMilli(event.endMillis).atZone(zone).toLocalDate()
            }

            val prefix = when {
                eventStartDate <= today && today < eventEndDate -> labels.today
                eventStartDate == tomorrow || (eventStartDate <= tomorrow && tomorrow < eventEndDate) -> labels.tomorrow
                else -> dayFormatter.format(eventStartDate)
            }
            return "$prefix · ${event.title}"
        }

        val startZdt = Instant.ofEpochMilli(event.startMillis).atZone(zone)
        val endZdt = Instant.ofEpochMilli(event.endMillis).atZone(zone)

        val inProgress = !now.isBefore(event.start) && now.isBefore(event.end)
        if (inProgress) {
            val endTimeStr = formatTime(endZdt, is24Hour)
            return labels.now(event.title, endTimeStr)
        }

        val startDate = startZdt.toLocalDate()
        val startTimeStr = formatTime(startZdt, is24Hour)

        return when (startDate) {
            today -> "$startTimeStr ${event.title}"
            tomorrow -> labels.tomorrowEvent(startTimeStr, event.title)
            else -> "${dayFormatter.format(startDate)} $startTimeStr ${event.title}"
        }
    }

    /**
     * Same text as [line], split into a lead (time or day, may be empty) and a title.
     * In-progress events and templates that do not end with the title stay whole in [NextUpRow.title].
     */
    fun row(
        event: CalendarEvent,
        now: Instant,
        zone: ZoneId,
        is24Hour: Boolean = true,
        labels: NextUpLabels,
    ): NextUpRow {
        val full = line(event, now, zone, is24Hour, labels)
        val inProgress = !event.allDay && !now.isBefore(event.start) && now.isBefore(event.end)
        if (inProgress) return NextUpRow("", full)
        val lead = if (event.allDay) full.removeSuffix(" · ${event.title}") else full.removeSuffix(event.title).trim()
        if (lead == full || lead.isEmpty()) return NextUpRow("", full)
        return NextUpRow(lead, event.title)
    }

    fun line(
        event: CalendarEvent,
        now: ZonedDateTime,
        is24Hour: Boolean = true,
        labels: NextUpLabels,
    ): String = line(event, now.toInstant(), now.zone, is24Hour, labels)

    fun line(
        event: CalendarEvent,
        nowMillis: Long,
        zone: ZoneId,
        is24Hour: Boolean = true,
        labels: NextUpLabels,
    ): String = line(event, Instant.ofEpochMilli(nowMillis), zone, is24Hour, labels)
}
