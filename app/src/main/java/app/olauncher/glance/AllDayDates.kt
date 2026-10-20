package app.olauncher.glance

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** All-day events are stored with UTC-midnight bounds; this is the one place that reads them. */
object AllDayDates {

    /** True when [millis] is exactly a UTC midnight (hour and minute zero). */
    fun isUtcMidnight(millis: Long): Boolean {
        val utc = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC)
        return utc.hour == 0 && utc.minute == 0
    }

    /** The calendar date of an all-day bound: its UTC date at UTC midnight, else its date in [zone]. */
    fun dateOf(millis: Long, zone: ZoneId): LocalDate =
        if (isUtcMidnight(millis)) Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
