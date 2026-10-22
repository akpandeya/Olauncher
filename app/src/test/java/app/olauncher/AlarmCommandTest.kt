package app.olauncher

import app.olauncher.helper.AlarmCommand
import java.time.LocalTime
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmCommandTest {
    private val noon = LocalTime.of(12, 0)
    private fun p(args: String, now: LocalTime = noon) = AlarmCommand.parse(args, now)
    private fun hm(args: String) = p(args)?.let { it.hour to it.minute }

    @Test fun timeFormats() {
        assertEquals(7 to 30, hm("7:30"))
        assertEquals(7 to 30, hm("07.30"))
        assertEquals(7 to 30, hm("730"))
        assertEquals(19 to 30, hm("1930"))
        assertEquals(7 to 0, hm("7"))
        assertEquals(19 to 0, hm("19"))
        assertEquals(0 to 0, hm("0:00"))
    }

    @Test fun rejectsInvalidTimes() {
        assertNull(p("25:00"))
        assertNull(p("7:61"))
        assertNull(p("24"))
        assertNull(p(""))
        assertNull(p("gym"))
        assertNull(p("+0"))
        assertNull(p("+1441"))
    }

    @Test fun laterTodayIsToday() {
        val a = p("12:01")!!
        assertFalse(a.tomorrow)
        assertEquals("⏰ 12:01", AlarmCommand.previewText(a))
    }

    @Test fun sameMinuteOrEarlierIsTomorrow() {
        assertTrue(p("12:00")!!.tomorrow)
        assertTrue(p("7:30")!!.tomorrow)
        assertEquals("⏰ 07:30 tomorrow · gym", AlarmCommand.previewText(p("7:30 gym")!!))
    }

    @Test fun midnightEdges() {
        assertFalse(p("23:59", LocalTime.of(23, 58))!!.tomorrow)
        assertTrue(p("0:00", LocalTime.of(0, 0))!!.tomorrow)
    }

    @Test fun relativeMinutes() {
        val a = p("+20", LocalTime.of(14, 32, 40))!!
        assertEquals(14, a.hour)
        assertEquals(52, a.minute)
        assertEquals(20, a.relativeMinutes)
        assertEquals("⏰ in 20 min (14:52)", AlarmCommand.previewText(a))
        val wrap = p("+30", LocalTime.of(23, 50))!!
        assertEquals(0 to 20, wrap.hour to wrap.minute)
    }

    @Test fun weekdayRange() {
        val a = p("7 mo-fr")!!
        assertEquals(
            listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
            a.days,
        )
        assertFalse(a.tomorrow)
        assertEquals("⏰ 07:00 Mon–Fri", AlarmCommand.previewText(a))
    }

    @Test fun dayListAndWeekend() {
        assertEquals(listOf(Calendar.SATURDAY, Calendar.SUNDAY), p("9 sa,su")!!.days)
        assertEquals(listOf(Calendar.SATURDAY, Calendar.SUNDAY), p("9 we")!!.days)
        assertEquals(listOf(Calendar.MONDAY, Calendar.WEDNESDAY), p("9 mon,wed")!!.days)
        assertEquals("⏰ 09:00 Sat, Sun", AlarmCommand.previewText(p("9 we")!!))
    }

    @Test fun germanDaysAndWrappingRange() {
        assertEquals(listOf(Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY), p("8 di-do")!!.days)
        assertEquals(listOf(Calendar.MONDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY), p("8 fr-mo")!!.days)
        assertEquals(listOf(Calendar.SUNDAY), p("8 so")!!.days)
    }

    @Test fun labelWords() {
        val a = p("6:45 gym time")!!
        assertEquals("gym time", a.label)
        assertTrue(a.days.isEmpty())
        val b = p("6:45 mo-fr gym time")!!
        assertEquals("gym time", b.label)
        assertEquals(5, b.days.size)
    }

    @Test fun rejectsUnknownDayWords() {
        assertNull(p("7 mo-xx"))
        assertNull(p("7 foo,bar"))
        assertNull(p("7 mo-fr-su"))
    }
}
