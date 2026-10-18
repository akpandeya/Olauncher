package app.olauncher.wellbeing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure scheduling maths for nudge intervals within an active hours window.
 */
object ScheduleMath {

    /**
     * Calculates the next fire time in epoch milliseconds.
     *
     * Returns the next slot `windowStart + k * interval` within `[windowStartMin, windowEndMin]`
     * (inclusive) that is strictly after [nowMillis]. If no remaining slots exist today,
     * rolls to tomorrow's `windowStartMin`.
     *
     * @param nowMillis The current timestamp in epoch milliseconds.
     * @param zone The time zone to compute wall-clock times in.
     * @param windowStartMin Active window start minute of the day (e.g. 540 for 09:00).
     * @param windowEndMin Active window end minute of the day (e.g. 1260 for 21:00).
     * @param intervalMin Interval between nudge slots in minutes.
     * @throws IllegalArgumentException if `windowStartMin >= windowEndMin`, `intervalMin <= 0`,
     *         or minute bounds are invalid.
     */
    fun nextFire(
        nowMillis: Long,
        zone: ZoneId,
        windowStartMin: Int,
        windowEndMin: Int,
        intervalMin: Int,
    ): Long {
        require(windowStartMin < windowEndMin) {
            "windowStartMin ($windowStartMin) must be < windowEndMin ($windowEndMin)"
        }
        require(intervalMin > 0) {
            "intervalMin ($intervalMin) must be > 0"
        }
        require(windowStartMin >= 0) {
            "windowStartMin ($windowStartMin) must be >= 0"
        }
        require(windowEndMin <= 24 * 60) {
            "windowEndMin ($windowEndMin) must be <= 1440"
        }

        val currentZonedDateTime = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = currentZonedDateTime.toLocalDate()

        // Check slots today: windowStart + k * interval <= windowEnd
        var slotMinute = windowStartMin
        while (slotMinute <= windowEndMin) {
            val slotMillis = slotToEpochMillis(today, slotMinute, zone)
            if (slotMillis > nowMillis) {
                return slotMillis
            }
            slotMinute += intervalMin
        }

        // No more slots today; roll to tomorrow's windowStart
        val tomorrow = today.plusDays(1)
        return slotToEpochMillis(tomorrow, windowStartMin, zone)
    }

    internal fun slotToEpochMillis(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long {
        val hours = minuteOfDay / 60
        val minutes = minuteOfDay % 60
        val localDateTime = if (hours >= 24) {
            date.plusDays(1).atStartOfDay()
        } else {
            LocalDateTime.of(date, LocalTime.of(hours, minutes))
        }
        return localDateTime.atZone(zone).toInstant().toEpochMilli()
    }
}
