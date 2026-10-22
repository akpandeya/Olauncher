package app.olauncher.glance

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val calendarId: Long = 0L,
) {
    val start: Instant get() = Instant.ofEpochMilli(startMillis)
    val end: Instant get() = Instant.ofEpochMilli(endMillis)

    constructor(
        id: Long,
        title: String,
        start: Instant,
        end: Instant,
        allDay: Boolean = false,
    ) : this(id, title, start.toEpochMilli(), end.toEpochMilli(), allDay)
}

open class CalendarRepository(private val context: Context) : CalendarEventSource {

    open suspend fun next(
        now: Long = System.currentTimeMillis(),
        hours: Int = 48,
        limit: Int = 4,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return@withContext emptyList()
        }

        val windowEndMillis = now + hours * 3600 * 1000L
        val queryStartMillis = now - 24 * 3600 * 1000L

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let { builder ->
            ContentUris.appendId(builder, queryStartMillis)
            ContentUris.appendId(builder, windowEndMillis)
            builder.build()
        }

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.VISIBLE,
        )

        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC, ${CalendarContract.Instances.END} ASC"

        val events = mutableListOf<CalendarEvent>()
        val nowInstant = Instant.ofEpochMilli(now)
        val zone = ZoneId.systemDefault()

        try {
            val cursor = try {
                context.contentResolver.query(uri, projection, selection, null, sortOrder)
            } catch (_: Exception) {
                context.contentResolver.query(uri, projection, null, null, sortOrder)
            }

            cursor?.use { c ->
                val idIdx = c.getColumnIndex(CalendarContract.Instances.EVENT_ID)
                val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = c.getColumnIndex(CalendarContract.Instances.END)
                val allDayIdx = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val statusIdx = c.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
                val visibleIdx = c.getColumnIndex(CalendarContract.Instances.VISIBLE)

                while (c.moveToNext() && events.size < limit) {
                    val eventId = if (idIdx != -1) c.getLong(idIdx) else 0L
                    val title = if (titleIdx != -1) (c.getString(titleIdx) ?: "") else ""
                    val begin = if (beginIdx != -1) c.getLong(beginIdx) else 0L
                    val end = if (endIdx != -1) c.getLong(endIdx) else 0L
                    val allDay = if (allDayIdx != -1) c.getInt(allDayIdx) == 1 else false
                    val status = if (statusIdx != -1) c.getInt(statusIdx) else CalendarContract.Attendees.ATTENDEE_STATUS_NONE
                    val visible = if (visibleIdx != -1) c.getInt(visibleIdx) else 1

                    if (visible == 0) continue
                    if (status == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                    if (begin >= windowEndMillis) continue

                    val event = CalendarEvent(
                        id = eventId,
                        title = title,
                        startMillis = begin,
                        endMillis = end,
                        allDay = allDay,
                    )
                    if (NextUpFormatting.hasEnded(event, nowInstant, zone)) continue
                    events.add(event)
                }
            }
        } catch (_: Exception) {
            // Return whatever events collected or empty list
        }

        events
    }

    /** Blocking read of visible, non-declined events overlapping the window. Call off the main thread. */
    override fun events(fromMillis: Long, toMillis: Long): List<CalendarEvent> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val out = mutableListOf<CalendarEvent>()
        try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().let { b ->
                ContentUris.appendId(b, fromMillis)
                ContentUris.appendId(b, toMillis)
                b.build()
            }
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.SELF_ATTENDEE_STATUS,
                CalendarContract.Instances.CALENDAR_ID,
            )
            val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
            context.contentResolver.query(
                uri, projection, selection, null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1) ?: continue
                    out.add(CalendarEvent(c.getLong(0), title, c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getLong(6)))
                }
            }
        } catch (_: Exception) {
            // Permission revoked or provider unavailable: no rows.
        }
        return out
    }

    /** Calendars on the phone as id to display name. Empty without permission. Blocking. */
    fun calendars(): List<Pair<Long, String>> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val out = mutableListOf<Pair<Long, String>>()
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME),
                null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )?.use { c ->
                while (c.moveToNext()) out.add(c.getLong(0) to (c.getString(1) ?: ""))
            }
        } catch (_: Exception) {
            // Provider unavailable: no calendars.
        }
        return out
    }

    /** Upcoming events in [fromMillis, toMillis] whose title matches [query]. Blocking; needs READ_CALENDAR already granted. */
    fun search(
        query: String,
        fromMillis: Long,
        toMillis: Long,
        max: Int = CalendarSearch.MAX_ROWS,
    ): List<CalendarEvent> = CalendarSearch.search(this, query, fromMillis, toMillis, max)

    open suspend fun next(
        now: Instant,
        hours: Int = 48,
        limit: Int = 4,
    ): List<CalendarEvent> = next(now.toEpochMilli(), hours, limit)

    companion object {
        suspend fun next(
            context: Context,
            now: Long = System.currentTimeMillis(),
            hours: Int = 48,
            limit: Int = 4,
        ): List<CalendarEvent> = CalendarRepository(context).next(now, hours, limit)

        suspend fun next(
            context: Context,
            now: Instant,
            hours: Int = 48,
            limit: Int = 4,
        ): List<CalendarEvent> = CalendarRepository(context).next(now.toEpochMilli(), hours, limit)
    }
}
