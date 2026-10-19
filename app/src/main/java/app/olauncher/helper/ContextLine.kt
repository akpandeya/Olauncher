package app.olauncher.helper

import app.olauncher.glance.CalendarEvent
import java.time.Instant

/** What the active media session is doing. */
data class MediaInfo(
    val title: String,
    val artist: String?,
    val packageName: String,
    val playing: Boolean,
)

/** Templates for event text; [inMinutes] takes %1$s = title, %2$d = minutes, [now] takes %1$s = title. */
data class ContextLabels(
    val inMinutes: String = "%1\$s in %2\$d min",
    val now: String = "%1\$s now",
)

sealed class ContextItem {
    abstract val text: String

    data class Media(override val text: String, val packageName: String) : ContextItem()
    data class Event(override val text: String, val eventId: Long) : ContextItem()
}

object ContextLine {

    const val LOOKAHEAD_MINUTES = 30L
    private const val MINUTE_MS = 60_000L

    /** Playing media wins; else an event starting within 30 min or running now; else null (hide the line). */
    fun pick(
        media: MediaInfo?,
        event: CalendarEvent?,
        now: Instant,
        labels: ContextLabels = ContextLabels(),
    ): ContextItem? {
        if (media != null && media.playing && media.title.isNotBlank()) {
            val artist = media.artist?.trim().orEmpty()
            val text = if (artist.isEmpty()) "▶ ${media.title.trim()}" else "▶ ${media.title.trim()} — $artist"
            return ContextItem.Media(text, media.packageName)
        }
        if (event == null || event.allDay || event.title.isBlank()) return null
        val nowMs = now.toEpochMilli()
        val title = event.title.trim()
        if (event.startMillis <= nowMs) {
            if (event.endMillis <= nowMs) return null
            return ContextItem.Event(String.format(labels.now, title), event.id)
        }
        val untilMs = event.startMillis - nowMs
        if (untilMs > LOOKAHEAD_MINUTES * MINUTE_MS) return null
        val minutes = ((untilMs + MINUTE_MS - 1) / MINUTE_MS).toInt()
        return ContextItem.Event(String.format(labels.inMinutes, title, minutes), event.id)
    }

    /** First event from a start-ordered list that is running now or starts within the lookahead. */
    fun relevantEvent(events: List<CalendarEvent>, now: Instant): CalendarEvent? =
        events.firstOrNull { pick(null, it, now) != null }
}
