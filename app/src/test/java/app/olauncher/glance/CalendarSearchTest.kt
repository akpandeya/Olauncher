package app.olauncher.glance

import app.olauncher.data.AppModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class CalendarSearchTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val now = ZonedDateTime.of(2026, 10, 19, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
    private val hour = 3600_000L
    private val day = 24 * hour
    private val to = now + CalendarSearch.WINDOW_DAYS * day

    private class FakeSource(private val list: List<CalendarEvent>) : CalendarEventSource {
        override fun events(fromMillis: Long, toMillis: Long) = list
    }

    private fun ev(id: Long, title: String, startOffset: Long, len: Long = hour) =
        CalendarEvent(id, title, now + startOffset, now + startOffset + len)

    private val labels = NextUpLabels("Today", "Tomorrow", "Now · %1\$s (until %2\$s)", "Tomorrow %1\$s %2\$s")

    @Test
    fun matchingTitleIsFound() {
        val src = FakeSource(listOf(ev(1, "Dentist", 2 * day), ev(2, "Standup", day)))
        assertEquals(listOf(1L), CalendarSearch.search(src, "dent", now, to).map { it.id })
    }

    @Test
    fun pastEventsExcluded() {
        val src = FakeSource(listOf(ev(1, "Dentist", -3 * hour), ev(2, "Dentist visit", day)))
        assertEquals(listOf(2L), CalendarSearch.search(src, "dent", now, to).map { it.id })
    }

    @Test
    fun cappedAtMax() {
        val src = FakeSource((1L..5L).map { ev(it, "Dentist $it", it * day) })
        val found = CalendarSearch.search(src, "dentist", now, to)
        assertEquals(2, found.size)
        assertEquals(listOf(1L, 2L), found.map { it.id })
    }

    @Test
    fun shortQueryGivesNothing() {
        val src = FakeSource(listOf(ev(1, "Dentist", day)))
        assertTrue(CalendarSearch.search(src, "de", now, to).isEmpty())
        assertTrue(CalendarSearch.search(src, " de ", now, to).isEmpty())
    }

    @Test
    fun lowScoreAndBeyondWindowDropped() {
        val src = FakeSource(listOf(ev(1, "Zebra", day), ev(2, "Dentist", 40 * day)))
        assertTrue(CalendarSearch.search(src, "dent", now, to).isEmpty())
    }

    @Test
    fun resultRowFormatsTitleAndTime() {
        // 2026-10-22 is a Thursday
        val start = ZonedDateTime.of(2026, 10, 22, 14, 0, 0, 0, zone).toInstant().toEpochMilli()
        val e = CalendarEvent(42, "Dentist", start, start + hour)
        val row = CalendarSearch.toResult(e, now, zone, true, labels)
        assertEquals("📅 Dentist · Thu 14:00", row.text)
        assertEquals(AppModel.Result.Action.Event(42), row.action)
    }
}
