package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class NextUpPlanTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val now = Instant.parse("2026-10-20T09:00:00Z") // 11:00 local
    private val today = LocalDate.of(2026, 10, 20)

    private fun at(hour: Int, dayOffset: Long = 0): Long =
        today.plusDays(dayOffset).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun timed(id: Long, title: String, hour: Int, dayOffset: Long = 0) =
        CalendarEvent(id, title, at(hour, dayOffset), at(hour, dayOffset) + 3_600_000L)

    private fun allDay(id: Long, title: String, dayOffset: Long = 0): CalendarEvent {
        val s = today.plusDays(dayOffset).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        return CalendarEvent(id, title, s, s + 86_400_000L, allDay = true)
    }

    private fun titles(p: NextUpPlan) = p.rows.map { it.title }

    @Test
    fun timedOneHourOutRanksAboveAllDay() {
        val p = NextUpPlanner.plan(listOf(allDay(1, "Holiday"), timed(2, "Call", 12)), now, zone)
        assertEquals(listOf("Call", "Holiday"), titles(p))
    }

    @Test
    fun timedFiveHoursOutRanksBelowAllDay() {
        val p = NextUpPlanner.plan(listOf(timed(2, "Dinner", 16), allDay(1, "Holiday")), now, zone)
        assertEquals(listOf("Holiday", "Dinner"), titles(p))
    }

    @Test
    fun threeAllDayAndTwoTimedGivesFiveRows() {
        val p = NextUpPlanner.plan(
            listOf(
                allDay(1, "A1"), allDay(2, "A2"), allDay(3, "A3"),
                timed(4, "T1", 12), timed(5, "T2", 18),
                timed(6, "Tomorrow", 9, 1),
            ),
            now, zone,
        )
        assertEquals(listOf("T1", "A1", "A2", "A3", "T2"), titles(p))
        assertEquals(0, p.hiddenCount)
    }

    @Test
    fun fillsFromTomorrowUpToFourRows() {
        val p = NextUpPlanner.plan(
            listOf(
                allDay(1, "A1"), allDay(2, "A2"), timed(3, "T1", 18),
                timed(4, "Tom1", 9, 1), timed(5, "Tom2", 10, 1), timed(6, "Tom3", 11, 1),
            ),
            now, zone,
        )
        assertEquals(listOf("A1", "A2", "T1", "Tom1"), titles(p))
        assertEquals(0, p.hiddenCount)
    }

    @Test
    fun eightEventsTodayCapAtSixAndExpand() {
        val events = (1..8).map { timed(it.toLong(), "E$it", 11 + it) }
        val collapsed = NextUpPlanner.plan(events, now, zone)
        assertEquals((1..6).map { "E$it" }, titles(collapsed))
        assertEquals(2, collapsed.hiddenCount)
        val open = NextUpPlanner.plan(events, now, zone, expanded = true)
        assertEquals((1..8).map { "E$it" }, titles(open))
        assertEquals(2, open.hiddenCount)
    }

    @Test
    fun duplicatesCollapse() {
        val p = NextUpPlanner.plan(listOf(timed(1, "Same", 12), timed(2, "Same", 12), timed(3, "Other", 13)), now, zone)
        assertEquals(listOf("Same", "Other"), titles(p))
        assertEquals(1, p.rows.first().id.toInt())
    }

    @Test
    fun endedEventsAreExcluded() {
        val p = NextUpPlanner.plan(
            listOf(timed(1, "Earlier", 8), allDay(2, "Yesterday", -1), timed(3, "Upcoming", 12)),
            now, zone,
        )
        assertEquals(listOf("Upcoming"), titles(p))
    }

    @Test
    fun screenshotCase() {
        val p = NextUpPlanner.plan(
            listOf(
                allDay(1, "Out of office"), allDay(2, "BER - P89 (Office)"),
                allDay(3, "Maha Ashtami"), allDay(4, "Maha Ashtami"),
                timed(5, "Standup", 14),
            ),
            now, zone,
        )
        assertEquals(4, p.rows.size)
        assertEquals("Standup", p.rows.first().title)
        assertEquals(1, titles(p).count { it == "Maha Ashtami" })
    }

    @Test
    fun eventJustAfterMidnightWithinThreeHoursIsInSoonGroup() {
        val lateNow = today.atTime(23, 30).atZone(zone).toInstant()
        fun startingAt(id: Long, title: String, h: Int, m: Int): CalendarEvent {
            val start = today.plusDays(1).atTime(h, m).atZone(zone).toInstant().toEpochMilli()
            return CalendarEvent(id, title, start, start + 3_600_000L)
        }
        val inWindow = startingAt(2, "Early flight", 0, 45)
        val atEdge = startingAt(3, "Edge", 2, 30)
        val outside = startingAt(4, "Breakfast", 2, 31)
        val p = NextUpPlanner.plan(listOf(outside, allDay(1, "Holiday"), atEdge, inWindow), lateNow, zone)
        assertEquals(listOf("Early flight", "Edge", "Holiday", "Breakfast"), titles(p))
    }
}
