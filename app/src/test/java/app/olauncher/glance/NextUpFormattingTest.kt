package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val TEST_LABELS = NextUpLabels(
    today = "Today",
    tomorrow = "Tomorrow",
    nowTemplate = "Now · %1\$s (until %2\$s)",
    tomorrowEventTemplate = "Tomorrow %1\$s %2\$s",
)

class NextUpFormattingTest {

    private val zoneBerlin: ZoneId = ZoneId.of("Europe/Berlin")

    @Test
    fun `event later today formats as time and title`() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(12, 0),
            zoneBerlin
        ).toInstant()

        val event = CalendarEvent(
            id = 1L,
            title = "Dentist",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(14, 30), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(15, 30), zoneBerlin).toInstant(),
        )

        // 24-hour setting
        val line24 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("14:30 Dentist", line24)

        // 12-hour setting
        val line12 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = false, labels = TEST_LABELS)
        assertEquals("2:30 PM Dentist", line12)
    }

    @Test
    fun `event tomorrow formats as Tomorrow time and title`() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(12, 0),
            zoneBerlin
        ).toInstant()

        val event = CalendarEvent(
            id = 2L,
            title = "Standup",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(9, 0), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(9, 30), zoneBerlin).toInstant(),
        )

        // 24-hour setting
        val line24 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("Tomorrow 09:00 Standup", line24)

        // 12-hour setting
        val line12 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = false, labels = TEST_LABELS)
        assertEquals("Tomorrow 9:00 AM Standup", line12)
    }

    @Test
    fun `event further out formats as day time and title`() {
        // Saturday Oct 17, 2026 -> Mon is Oct 19, 2026
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 10, 17),
            LocalTime.of(12, 0),
            zoneBerlin
        ).toInstant()

        val event = CalendarEvent(
            id = 3L,
            title = "Review",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(10, 0), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(11, 0), zoneBerlin).toInstant(),
        )

        // 24-hour setting
        val line24 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("Mon 10:00 Review", line24)

        // 12-hour setting
        val line12 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = false, labels = TEST_LABELS)
        assertEquals("Mon 10:00 AM Review", line12)
    }

    @Test
    fun `all-day event today formats as Today dot title`() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(14, 0),
            zoneBerlin
        ).toInstant()

        // Android CalendarContract UTC midnight convention
        val eventUtc = CalendarEvent(
            id = 4L,
            title = "Mum's birthday",
            start = LocalDate.of(2026, 10, 18).atStartOfDay(ZoneOffset.UTC).toInstant(),
            end = LocalDate.of(2026, 10, 19).atStartOfDay(ZoneOffset.UTC).toInstant(),
            allDay = true,
        )
        val lineUtc = NextUpFormatting.line(eventUtc, now, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("Today · Mum's birthday", lineUtc)

        // Local zone start convention
        val eventLocal = CalendarEvent(
            id = 5L,
            title = "Mum's birthday",
            start = LocalDate.of(2026, 10, 18).atStartOfDay(zoneBerlin).toInstant(),
            end = LocalDate.of(2026, 10, 19).atStartOfDay(zoneBerlin).toInstant(),
            allDay = true,
        )
        val lineLocal = NextUpFormatting.line(eventLocal, now, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("Today · Mum's birthday", lineLocal)
    }

    @Test
    fun `multi-day all-day event spanning today formats as Today`() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(10, 0),
            zoneBerlin
        ).toInstant()

        val event = CalendarEvent(
            id = 6L,
            title = "Hackathon",
            start = LocalDate.of(2026, 10, 17).atStartOfDay(ZoneOffset.UTC).toInstant(),
            end = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).toInstant(),
            allDay = true,
        )
        val line = NextUpFormatting.line(event, now, zoneBerlin, labels = TEST_LABELS)
        assertEquals("Today · Hackathon", line)
    }

    @Test
    fun `all-day event tomorrow formats as Tomorrow dot title`() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(10, 0),
            zoneBerlin
        ).toInstant()

        val event = CalendarEvent(
            id = 7L,
            title = "Conference",
            start = LocalDate.of(2026, 10, 19).atStartOfDay(ZoneOffset.UTC).toInstant(),
            end = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).toInstant(),
            allDay = true,
        )
        val line = NextUpFormatting.line(event, now, zoneBerlin, labels = TEST_LABELS)
        assertEquals("Tomorrow · Conference", line)
    }

    @Test
    fun `event in progress formats as Now dot title (until endTime)`() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(10, 5),
            zoneBerlin
        ).toInstant()

        val event = CalendarEvent(
            id = 8L,
            title = "Standup",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(10, 0), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(10, 15), zoneBerlin).toInstant(),
        )

        // 24-hour setting
        val line24 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("Now · Standup (until 10:15)", line24)

        // 12-hour setting
        val line12 = NextUpFormatting.line(event, now, zoneBerlin, is24Hour = false, labels = TEST_LABELS)
        assertEquals("Now · Standup (until 10:15 AM)", line12)
    }

    @Test
    fun `event exactly at start time is considered in progress`() {
        val startZdt = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(10, 0), zoneBerlin)
        val event = CalendarEvent(
            id = 9L,
            title = "Standup",
            start = startZdt.toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(10, 15), zoneBerlin).toInstant(),
        )
        val line = NextUpFormatting.line(event, startZdt.toInstant(), zoneBerlin, labels = TEST_LABELS)
        assertEquals("Now · Standup (until 10:15)", line)
    }

    @Test
    fun `midnight edge cases`() {
        // 1. Event starting at midnight tomorrow (00:00)
        val nowNight = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(23, 0),
            zoneBerlin
        ).toInstant()

        val midnightEvent = CalendarEvent(
            id = 10L,
            title = "Midnight Deploy",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(0, 0), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(1, 0), zoneBerlin).toInstant(),
        )
        assertEquals("Tomorrow 00:00 Midnight Deploy", NextUpFormatting.line(midnightEvent, nowNight, zoneBerlin, is24Hour = true, labels = TEST_LABELS))
        assertEquals("Tomorrow 12:00 AM Midnight Deploy", NextUpFormatting.line(midnightEvent, nowNight, zoneBerlin, is24Hour = false, labels = TEST_LABELS))

        // 2. Event ending at midnight in progress
        val nowLate = ZonedDateTime.of(
            LocalDate.of(2026, 10, 18),
            LocalTime.of(23, 45),
            zoneBerlin
        ).toInstant()

        val lateEvent = CalendarEvent(
            id = 11L,
            title = "Late Party",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(23, 0), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(0, 0), zoneBerlin).toInstant(),
        )
        assertEquals("Now · Late Party (until 00:00)", NextUpFormatting.line(lateEvent, nowLate, zoneBerlin, is24Hour = true, labels = TEST_LABELS))
        assertEquals("Now · Late Party (until 12:00 AM)", NextUpFormatting.line(lateEvent, nowLate, zoneBerlin, is24Hour = false, labels = TEST_LABELS))

        // 3. Now is exactly at midnight (00:00:00)
        val nowMidnight = ZonedDateTime.of(
            LocalDate.of(2026, 10, 19),
            LocalTime.of(0, 0),
            zoneBerlin
        ).toInstant()

        val earlyEvent = CalendarEvent(
            id = 12L,
            title = "Early Flight",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(0, 30), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(2, 0), zoneBerlin).toInstant(),
        )
        assertEquals("00:30 Early Flight", NextUpFormatting.line(earlyEvent, nowMidnight, zoneBerlin, is24Hour = true, labels = TEST_LABELS))
        assertEquals("12:30 AM Early Flight", NextUpFormatting.line(earlyEvent, nowMidnight, zoneBerlin, is24Hour = false, labels = TEST_LABELS))
    }

    @Test
    fun `DST transition day in Europe Berlin`() {
        // Fall transition: Sunday October 25, 2026 (clocks fall back from 03:00 to 02:00 UTC+2 -> UTC+1)
        val saturday = ZonedDateTime.of(
            LocalDate.of(2026, 10, 24),
            LocalTime.of(18, 0),
            zoneBerlin
        ).toInstant()

        val sundayEvent = CalendarEvent(
            id = 13L,
            title = "Sunday Brunch",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 25), LocalTime.of(9, 0), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 25), LocalTime.of(11, 0), zoneBerlin).toInstant(),
        )

        // From Saturday, Sunday event correctly formats as Tomorrow
        val brunchLine = NextUpFormatting.line(sundayEvent, saturday, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("Tomorrow 09:00 Sunday Brunch", brunchLine)

        // Event on the DST day itself
        val sundayNow = ZonedDateTime.of(
            LocalDate.of(2026, 10, 25),
            LocalTime.of(7, 0),
            zoneBerlin
        ).toInstant()

        val sundayLine = NextUpFormatting.line(sundayEvent, sundayNow, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("09:00 Sunday Brunch", sundayLine)

        // Spring transition: Sunday March 29, 2026 (clocks advance 02:00 -> 03:00 UTC+1 -> UTC+2)
        val springSaturday = ZonedDateTime.of(
            LocalDate.of(2026, 3, 28),
            LocalTime.of(20, 0),
            zoneBerlin
        ).toInstant()

        val springEvent = CalendarEvent(
            id = 14L,
            title = "Morning Run",
            start = ZonedDateTime.of(LocalDate.of(2026, 3, 29), LocalTime.of(9, 0), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 3, 29), LocalTime.of(10, 0), zoneBerlin).toInstant(),
        )
        val springLine = NextUpFormatting.line(springEvent, springSaturday, zoneBerlin, is24Hour = true, labels = TEST_LABELS)
        assertEquals("Tomorrow 09:00 Morning Run", springLine)
    }

    @Test
    fun `line overloads with ZonedDateTime and Long millis work`() {
        val nowZdt = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(12, 0), zoneBerlin)
        val event = CalendarEvent(
            id = 15L,
            title = "Dentist",
            start = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(14, 30), zoneBerlin).toInstant(),
            end = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(15, 30), zoneBerlin).toInstant(),
        )

        assertEquals("14:30 Dentist", NextUpFormatting.line(event, nowZdt, labels = TEST_LABELS))
        assertEquals("14:30 Dentist", NextUpFormatting.line(event, nowZdt.toInstant().toEpochMilli(), zoneBerlin, labels = TEST_LABELS))
    }

    @Test
    fun `all-day event ends at local midnight, not UTC midnight`() {
        val zoneNewYork = ZoneId.of("America/New_York")
        val sunday = CalendarEvent(
            id = 20L,
            title = "Mum's birthday",
            start = LocalDate.of(2026, 10, 18).atStartOfDay(ZoneOffset.UTC).toInstant(),
            end = LocalDate.of(2026, 10, 19).atStartOfDay(ZoneOffset.UTC).toInstant(),
            allDay = true,
        )

        // Berlin, 00:30 Monday: UTC end (02:00 local) not reached yet, but the day is over.
        val berlinMonday = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(0, 30), zoneBerlin).toInstant()
        assertTrue(NextUpFormatting.hasEnded(sunday, berlinMonday, zoneBerlin))
        val berlinSundayLate = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(23, 30), zoneBerlin).toInstant()
        assertFalse(NextUpFormatting.hasEnded(sunday, berlinSundayLate, zoneBerlin))

        // New York, 21:00 Sunday: UTC end (20:00 local) already passed, but it is still Sunday.
        val newYorkSundayEvening = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(21, 0), zoneNewYork).toInstant()
        assertFalse(NextUpFormatting.hasEnded(sunday, newYorkSundayEvening, zoneNewYork))
        val newYorkMonday = ZonedDateTime.of(LocalDate.of(2026, 10, 19), LocalTime.of(0, 0), zoneNewYork).toInstant()
        assertTrue(NextUpFormatting.hasEnded(sunday, newYorkMonday, zoneNewYork))
    }

    @Test
    fun `timed event has ended exactly at its end time`() {
        val start = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(9, 0), zoneBerlin).toInstant()
        val end = ZonedDateTime.of(LocalDate.of(2026, 10, 18), LocalTime.of(10, 15), zoneBerlin).toInstant()
        val event = CalendarEvent(id = 21L, title = "Standup", start = start, end = end)
        assertFalse(NextUpFormatting.hasEnded(event, end.minusSeconds(1), zoneBerlin))
        assertTrue(NextUpFormatting.hasEnded(event, end, zoneBerlin))
    }
}
