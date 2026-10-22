package app.olauncher

import app.olauncher.helper.EventCommand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventCommandTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    // Wednesday 22 Oct 2025, 12:00 local (CEST, UTC+2).
    private val noon = ZonedDateTime.of(2025, 10, 22, 12, 0, 0, 0, berlin)

    private fun millis(utc: String) = Instant.parse(utc).toEpochMilli()

    private fun ok(args: String, now: ZonedDateTime = noon): EventCommand.Event {
        val parsed = EventCommand.parse(args, now)
        assertTrue("expected Ok for '$args' but got $parsed", parsed is EventCommand.Parsed.Ok)
        return (parsed as EventCommand.Parsed.Ok).event
    }

    private fun errorToken(args: String, now: ZonedDateTime = noon): String? {
        val parsed = EventCommand.parse(args, now)
        assertTrue("expected Error for '$args' but got $parsed", parsed is EventCommand.Parsed.Error)
        return (parsed as EventCommand.Parsed.Error).token
    }

    @Test fun dentistFriday() {
        val e = ok("Dentist fri 15:00")
        assertEquals("Dentist", e.title)
        assertEquals("", e.place)
        assertEquals(millis("2025-10-24T13:00:00Z"), e.beginMillis)
        assertEquals(millis("2025-10-24T14:00:00Z"), e.endMillis)
        assertEquals(false, e.allDay)
        assertEquals("📅 Dentist · Fri 24 Oct · 15:00–16:00", EventCommand.previewText(e))
    }

    @Test fun lunchTomorrowWithLengthAndPlace() {
        val e = ok("Lunch with Sam tmrw 13 1h30 @Café Rio")
        assertEquals("Lunch with Sam", e.title)
        assertEquals("Café Rio", e.place)
        assertEquals(millis("2025-10-23T11:00:00Z"), e.beginMillis)
        assertEquals(millis("2025-10-23T12:30:00Z"), e.endMillis)
        assertEquals(false, e.allDay)
        assertEquals("📅 Lunch with Sam · Thu 23 Oct · 13:00–14:30 · Café Rio", EventCommand.previewText(e))
    }

    @Test fun rentIsAllDay() {
        val e = ok("Rent 1.11")
        assertEquals("Rent", e.title)
        assertEquals("", e.place)
        assertEquals(millis("2025-11-01T00:00:00Z"), e.beginMillis)
        assertEquals(millis("2025-11-02T00:00:00Z"), e.endMillis)
        assertEquals(true, e.allDay)
        assertEquals("📅 Rent · Sat 1 Nov", EventCommand.previewText(e))
    }

    @Test fun callMamaTodayRange() {
        val e = ok("Call Mama today 19-19:30")
        assertEquals("Call Mama", e.title)
        assertEquals("", e.place)
        assertEquals(millis("2025-10-22T17:00:00Z"), e.beginMillis)
        assertEquals(millis("2025-10-22T17:30:00Z"), e.endMillis)
        assertEquals(false, e.allDay)
    }

    @Test fun noDateUsesTodayWhenTimeIsAheadElseTomorrow() {
        assertEquals(millis("2025-10-22T12:01:00Z"), ok("Call 14:01").beginMillis)
        assertEquals(millis("2025-10-23T08:00:00Z"), ok("Call 10:00").beginMillis)
        assertEquals(millis("2025-10-23T10:00:00Z"), ok("Call 12:00").beginMillis)
    }

    @Test fun noTimeNoDateIsAllDayToday() {
        val e = ok("Birthday")
        assertEquals(true, e.allDay)
        assertEquals(LocalDate.of(2025, 10, 22), e.date)
        assertEquals(millis("2025-10-22T00:00:00Z"), e.beginMillis)
    }

    @Test fun weekdayIsTodayOnlyIfTimeIsAhead() {
        assertEquals(millis("2025-10-22T13:00:00Z"), ok("Gym wed 15:00").beginMillis)
        // DST ends on 26 Oct, so later dates are UTC+1.
        assertEquals(millis("2025-10-29T10:00:00Z"), ok("Gym wed 11:00").beginMillis)
        assertEquals(millis("2025-10-27T10:00:00Z"), ok("Gym montag 11:00").beginMillis)
    }

    @Test fun germanDayWords() {
        assertEquals(LocalDate.of(2025, 10, 23), ok("Zahnarzt morgen").date)
        assertEquals(LocalDate.of(2025, 10, 24), ok("Zahnarzt übermorgen").date)
        assertEquals(LocalDate.of(2025, 10, 22), ok("Zahnarzt heute").date)
        assertEquals(LocalDate.of(2025, 10, 24), ok("Zahnarzt fr").date)
    }

    @Test fun explicitDatesRollToNextYearOnlyWithoutYear() {
        assertEquals(LocalDate.of(2025, 10, 24), ok("X 24.10").date)
        assertEquals(LocalDate.of(2026, 10, 1), ok("X 1.10").date)
        assertEquals(LocalDate.of(2024, 3, 5), ok("X 5.3.2024").date)
    }

    @Test fun dotTimeIsATimeWhenNotADate() {
        // 9:30 has passed today, so it is tomorrow 09:30 CEST.
        assertEquals(millis("2025-10-23T07:30:00Z"), ok("X 9.30").beginMillis)
    }

    @Test fun rangePastMidnightEndsNextDay() {
        val e = ok("Party tmrw 23-1")
        assertEquals(millis("2025-10-23T21:00:00Z"), e.beginMillis)
        assertEquals(millis("2025-10-23T23:00:00Z"), e.endMillis)
    }

    @Test fun lengthFormats() {
        assertEquals(millis("2025-10-22T13:00:00Z"), ok("X 14:00 1h").endMillis)
        assertEquals(millis("2025-10-22T12:45:00Z"), ok("X 14:00 45m").endMillis)
        assertEquals(millis("2025-10-22T13:30:00Z"), ok("X 14:00 1h30").endMillis)
    }

    @Test fun firstWordIsAlwaysTitle() {
        val e = ok("Fri fri 15:00")
        assertEquals("Fri", e.title)
        assertEquals(LocalDate.of(2025, 10, 24), e.date)
    }

    @Test fun rejectsInvalidDatesAndTimes() {
        assertEquals("32.10", errorToken("X 32.10"))
        assertEquals("25:00", errorToken("X 25:00"))
        assertEquals("13:60", errorToken("X 13:60"))
        assertEquals("30.2.2025", errorToken("X 30.2.2025"))
        assertEquals("19-25", errorToken("X 19-25"))
        assertEquals("0m", errorToken("X 15:00 0m"))
    }

    @Test fun rejectsTwoTimesOrTwoDates() {
        assertEquals("16:00", errorToken("X 15:00 16:00"))
        assertEquals("25.10", errorToken("X fri 25.10"))
    }

    @Test fun rejectsEmptyTitle() {
        assertNull(errorToken(""))
        assertNull(errorToken("   "))
        assertNull(errorToken("@Home"))
        assertNull(errorToken("@Home 15:00"))
    }

    @Test fun errorTextNamesTheToken() {
        assertEquals("Can't read '32.10'", EventCommand.errorText(EventCommand.Parsed.Error("32.10")))
    }

    @Test fun usesTheInjectedZone() {
        val utc = ZonedDateTime.of(2025, 10, 22, 12, 0, 0, 0, ZoneId.of("UTC"))
        assertEquals(millis("2025-10-22T15:00:00Z"), ok("X 15:00", utc).beginMillis)
    }
}
