package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeNextEventTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val template = "Now · %1\$s (until %2\$s)"
    private val now: Instant = ZonedDateTime.of(2026, 3, 10, 9, 0, 0, 0, zone).toInstant()

    private fun at(h: Int, m: Int = 0, dayOffset: Long = 0): Instant =
        ZonedDateTime.of(2026, 3, 10, h, m, 0, 0, zone).plusDays(dayOffset).toInstant()

    private fun timed(id: Long, title: String, start: Instant, end: Instant) =
        CalendarEvent(id, title, start, end)

    @Test
    fun timedEventPickedOverEarlierAllDay() {
        val allDay = CalendarEvent(
            1, "Holiday",
            ZonedDateTime.of(2026, 3, 10, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant(),
            ZonedDateTime.of(2026, 3, 11, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant(),
            allDay = true,
        )
        val standup = timed(7, "Standup", at(14, 30), at(15))
        val result = HomeNextEvent.line(listOf(allDay, standup), now, zone, true, template)
        assertEquals(HomeNextEventLine("14:30 · Standup", 7), result)
    }

    @Test
    fun runningEventShowsNowText() {
        val e = timed(9, "Retro", at(8, 30), at(15))
        val result = HomeNextEvent.line(listOf(e), now, zone, true, template)
        assertEquals(HomeNextEventLine("Now · Retro (until 15:00)", 9), result)
    }

    @Test
    fun endedEventSkipped() {
        val ended = timed(3, "Early", at(7), at(8))
        val next = timed(4, "Lunch", at(12), at(13))
        assertEquals(4L, HomeNextEvent.pick(listOf(ended, next), now, zone)?.id)
    }

    @Test
    fun onlyTomorrowGivesNull() {
        val e = timed(5, "Tomorrow", at(10, 0, 1), at(11, 0, 1))
        assertNull(HomeNextEvent.pick(listOf(e), now, zone))
        assertNull(HomeNextEvent.line(listOf(e), now, zone, true, template))
    }

    @Test
    fun onlyAllDayGivesNull() {
        val allDay = CalendarEvent(
            6, "Trip",
            ZonedDateTime.of(2026, 3, 10, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant(),
            ZonedDateTime.of(2026, 3, 11, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toInstant(),
            allDay = true,
        )
        assertNull(HomeNextEvent.pick(listOf(allDay), now, zone))
    }

    @Test
    fun twelveHourFormatRespected() {
        val e = timed(8, "Review", at(14, 30), at(15))
        assertEquals("2:30 PM · Review", HomeNextEvent.line(listOf(e), now, zone, false, template)?.text)
        val running = timed(10, "Sync", at(8), at(15))
        assertEquals("Now · Sync (until 3:00 PM)", HomeNextEvent.line(listOf(running), now, zone, false, template)?.text)
    }
}
