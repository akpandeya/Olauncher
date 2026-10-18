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

data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
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

open class CalendarRepository(private val context: Context) {

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
                    if (end <= now) continue
                    if (begin >= windowEndMillis) continue

                    events.add(
                        CalendarEvent(
                            id = eventId,
                            title = title,
                            startMillis = begin,
                            endMillis = end,
                            allDay = allDay,
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Return whatever events collected or empty list
        }

        events
    }

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
