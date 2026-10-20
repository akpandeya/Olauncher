package app.olauncher.helper

import android.graphics.Bitmap
import app.olauncher.glance.CalendarEvent
import java.time.Instant

/**
 * What the active media session is doing. [positionMs] was reported at [positionAtMs]
 * (elapsed realtime, 0 if never reported); [durationMs] is 0 when unknown.
 */
data class MediaInfo(
    val title: String,
    val artist: String?,
    val packageName: String,
    val playing: Boolean,
    val artwork: Bitmap? = null,
    val positionMs: Long = 0L,
    val positionAtMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
)

/** Templates for event text; [inMinutes] takes %1$s = title, %2$d = minutes, [now] takes %1$s = title. */
data class ContextLabels(
    val inMinutes: String = "%1\$s in %2\$d min",
    val now: String = "%1\$s now",
)

sealed class ContextItem {
    abstract val text: String

    data class Media(
        override val text: String,
        val packageName: String,
        val title: String = "",
        val playing: Boolean = true,
    ) : ContextItem()
    data class Event(override val text: String, val eventId: Long) : ContextItem()
}

object ContextLine {

    const val LOOKAHEAD_MINUTES = 30L
    private const val MINUTE_MS = 60_000L

    /** Media (playing or recently paused) wins; else an event starting within 30 min or running now; else null (hide the line). */
    fun pick(
        media: MediaInfo?,
        event: CalendarEvent?,
        now: Instant,
        labels: ContextLabels = ContextLabels(),
    ): ContextItem? {
        if (media != null && media.title.isNotBlank()) {
            val artist = media.artist?.trim().orEmpty()
            val icon = if (media.playing) "▶" else "❚❚"
            val title = media.title.trim()
            val text = if (artist.isEmpty()) "$icon $title" else "$icon $title — $artist"
            return ContextItem.Media(text, media.packageName, title, media.playing)
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
