package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StudyGridTest {

    private val zone = ZoneId.of("UTC")

    private fun event(reviewedAt: Long, id: Long = reviewedAt) =
        ReviewEvent(id = id, wordId = 1, reviewedAt = reviewedAt, result = "knew", boxBefore = 1, boxAfter = 2)

    private fun millis(date: LocalDate): Long =
        date.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()

    @Test
    fun `grid covers exactly 12 columns ending with the current week`() {
        val today = LocalDate.of(2026, 10, 21) // a Wednesday
        val cells = StudyGrid.build(emptyList(), today, zone)

        val columns = cells.map { it.column }.distinct().sorted()
        assertEquals((0 until StudyGrid.WEEKS).toList(), columns)

        // The last cell is today itself: Wednesday = row 2 (Monday-first).
        val last = cells.last()
        assertEquals(StudyGrid.WEEKS - 1, last.column)
        assertEquals(2, last.row)
        assertEquals(today, last.date)
    }

    @Test
    fun `cells map to Monday-first weekday rows`() {
        val today = LocalDate.of(2026, 10, 21)
        val cells = StudyGrid.build(emptyList(), today, zone)
        for (cell in cells) {
            assertEquals(cell.date.dayOfWeek, DayOfWeek.of(cell.row + 1))
        }
    }

    @Test
    fun `today sits in the last column and future days are absent`() {
        val today = LocalDate.of(2026, 10, 21)
        val cells = StudyGrid.build(emptyList(), today, zone)
        assertTrue(cells.none { it.date > today })
        assertTrue(cells.any { it.date == today })
        // The current week's column stops at today: no Thursday-Sunday cells.
        assertEquals(3, cells.count { it.column == StudyGrid.WEEKS - 1 })
    }

    @Test
    fun `studied days are flagged by local date across duplicate events`() {
        val today = LocalDate.of(2026, 10, 21)
        val studiedDay = today.minusDays(9)
        val events = listOf(
            event(millis(studiedDay), id = 1),
            event(millis(studiedDay).plus(1), id = 2), // twice the same day
            event(millis(today), id = 3),
        )
        val cells = StudyGrid.build(events, today, zone)
        assertEquals(2, cells.count { it.studied })
        assertEquals(setOf(studiedDay, today), cells.filter { it.studied }.map { it.date }.toSet())
    }

    @Test
    fun `empty events yield only empty cells`() {
        val today = LocalDate.of(2026, 10, 21)
        val cells = StudyGrid.build(emptyList(), today, zone)
        assertTrue(cells.isNotEmpty())
        assertTrue(cells.none { it.studied })
    }

    @Test
    fun `bucketing survives a DST boundary`() {
        // Europe/Berlin: DST ends Oct 25 2026. Events at fixed UTC instants on
        // both sides must land on their local dates in the grid.
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.of(2026, 10, 26)
        // 00:30 local on the 25th (DST day) is 22:30 UTC on the 24th; 23:30 local on
        // the 25th (after the switch, CET) is 22:30 UTC on the 25th. UTC bucketing would
        // mark the 24th and 25th; local bucketing marks only the 25th.
        val studied = LocalDate.of(2026, 10, 25)
        val events = listOf(
            event(studied.atTime(0, 30).atZone(zone).toInstant().toEpochMilli(), id = 1),
            event(studied.atTime(23, 30).atZone(zone).toInstant().toEpochMilli(), id = 2),
        )
        val cells = StudyGrid.build(events, today, zone)
        assertEquals(listOf(studied), cells.filter { it.studied }.map { it.date })
        assertFalse(cells.first { it.date == studied.minusDays(1) }.studied)
    }

    @Test
    fun `shadeLevel maps counts to levels 0 through 4`() {
        assertEquals(0, StudyGrid.shadeLevel(-5))
        assertEquals(0, StudyGrid.shadeLevel(0))
        assertEquals(1, StudyGrid.shadeLevel(1))
        assertEquals(1, StudyGrid.shadeLevel(2))
        assertEquals(2, StudyGrid.shadeLevel(3))
        assertEquals(2, StudyGrid.shadeLevel(5))
        assertEquals(3, StudyGrid.shadeLevel(6))
        assertEquals(3, StudyGrid.shadeLevel(9))
        assertEquals(4, StudyGrid.shadeLevel(10))
        assertEquals(4, StudyGrid.shadeLevel(25))
    }

    @Test
    fun `reviewCountsByDate counts events by local date`() {
        val today = LocalDate.of(2026, 10, 21)
        val d1 = today.minusDays(3)
        val d2 = today.minusDays(1)
        val events = listOf(
            event(millis(d1), id = 1),
            event(millis(d1) + 100, id = 2),
            event(millis(d1) + 200, id = 3),
            event(millis(d2), id = 4),
            event(millis(today), id = 5),
            event(millis(today) + 1000, id = 6),
        )
        val counts = StudyGrid.reviewCountsByDate(events, zone)
        assertEquals(3, counts[d1])
        assertEquals(1, counts[d2])
        assertEquals(2, counts[today])
        assertEquals(null, counts[today.minusDays(2)])
    }

    @Test
    fun `cells contain per-day review counts and shade levels`() {
        val today = LocalDate.of(2026, 10, 21)
        val dayLevel1 = today.minusDays(1) // 1 review -> level 1
        val dayLevel2 = today.minusDays(2) // 4 reviews -> level 2
        val dayLevel3 = today.minusDays(3) // 7 reviews -> level 3
        val dayLevel4 = today.minusDays(4) // 12 reviews -> level 4

        val events = mutableListOf<ReviewEvent>()
        events.add(event(millis(dayLevel1), id = 100))
        repeat(4) { i -> events.add(event(millis(dayLevel2) + i, id = (200 + i).toLong())) }
        repeat(7) { i -> events.add(event(millis(dayLevel3) + i, id = (300 + i).toLong())) }
        repeat(12) { i -> events.add(event(millis(dayLevel4) + i, id = (400 + i).toLong())) }

        val cells = StudyGrid.build(events, today, zone)

        val c1 = cells.first { it.date == dayLevel1 }
        assertEquals(1, c1.count)
        assertEquals(1, c1.shadeLevel)
        assertTrue(c1.studied)

        val c2 = cells.first { it.date == dayLevel2 }
        assertEquals(4, c2.count)
        assertEquals(2, c2.shadeLevel)
        assertTrue(c2.studied)

        val c3 = cells.first { it.date == dayLevel3 }
        assertEquals(7, c3.count)
        assertEquals(3, c3.shadeLevel)
        assertTrue(c3.studied)

        val c4 = cells.first { it.date == dayLevel4 }
        assertEquals(12, c4.count)
        assertEquals(4, c4.shadeLevel)
        assertTrue(c4.studied)

        val zeroCell = cells.first { it.date == today.minusDays(5) }
        assertEquals(0, zeroCell.count)
        assertEquals(0, zeroCell.shadeLevel)
        assertFalse(zeroCell.studied)
    }

    @Test
    fun `today is flagged with isToday on cell`() {
        val today = LocalDate.of(2026, 10, 21)
        val cells = StudyGrid.build(emptyList(), today, zone)
        val todayCell = cells.first { it.date == today }
        assertTrue(todayCell.isToday)
        assertEquals(1, cells.count { it.isToday })
        assertTrue(cells.filter { it.date != today }.none { it.isToday })
    }

    @Test
    fun `monthLabels returns labels above grid without collisions`() {
        val today = LocalDate.of(2026, 10, 25)
        val cells = StudyGrid.build(emptyList(), today, zone)
        val labels = StudyGrid.monthLabels(cells, java.util.Locale.ENGLISH)

        assertTrue(labels.isNotEmpty())
        assertEquals(labels.map { it.column }.distinct(), labels.map { it.column })
        assertTrue(labels.any { it.name.startsWith("Aug") })
        assertTrue(labels.any { it.name.startsWith("Sep") })
        assertTrue(labels.any { it.name.startsWith("Oct") })

        // Check empty cells returns empty labels
        assertTrue(StudyGrid.monthLabels(emptyList()).isEmpty())
    }

    @Test
    fun `monthLabels skips column 0 when next month starts in column 1`() {
        // Ending on Nov 15 2026: 12 weeks earlier starts in late August, Sep 1 is in column 1.
        val today = LocalDate.of(2026, 11, 15)
        val cells = StudyGrid.build(emptyList(), today, zone)
        val labels = StudyGrid.monthLabels(cells, java.util.Locale.ENGLISH)
        // Column 0 should not collide with Sep at column 1
        assertFalse(labels.any { it.column == 0 })
        assertTrue(labels.any { it.column == 1 && it.name.startsWith("Sep") })
    }

    @Test
    fun `buildDayStrip builds 7 day cells Monday to Sunday with reviewed days filled and today outlined`() {
        val today = LocalDate.of(2026, 10, 21) // Wednesday
        val monday = today.minusDays(2)
        val tuesday = today.minusDays(1)
        val thursday = today.plusDays(1)

        val events = listOf(
            event(millis(monday), id = 1),
            event(millis(today), id = 2),
        )
        val strip = StudyGrid.buildDayStrip(events, today, zone, java.util.Locale.ENGLISH)

        assertEquals(7, strip.size)
        assertEquals(listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        ), strip.map { it.dayOfWeek })

        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), strip.map { it.label })

        // Monday: reviewed, not today, not future
        val monCell = strip[0]
        assertEquals(monday, monCell.date)
        assertTrue(monCell.reviewed)
        assertFalse(monCell.isToday)
        assertFalse(monCell.isFuture)

        // Tuesday: not reviewed, not today, not future
        val tueCell = strip[1]
        assertEquals(tuesday, tueCell.date)
        assertFalse(tueCell.reviewed)
        assertFalse(tueCell.isToday)
        assertFalse(tueCell.isFuture)

        // Wednesday (today): reviewed, today, not future
        val wedCell = strip[2]
        assertEquals(today, wedCell.date)
        assertTrue(wedCell.reviewed)
        assertTrue(wedCell.isToday)
        assertFalse(wedCell.isFuture)

        // Thursday (future): not reviewed, not today, future
        val thuCell = strip[3]
        assertEquals(thursday, thuCell.date)
        assertFalse(thuCell.reviewed)
        assertFalse(thuCell.isToday)
        assertTrue(thuCell.isFuture)

        // Sunday (future)
        val sunCell = strip[6]
        assertFalse(sunCell.reviewed)
        assertFalse(sunCell.isToday)
        assertTrue(sunCell.isFuture)
    }

    @Test
    fun `buildDayStrip survives DST boundary`() {
        val zone = ZoneId.of("Europe/Berlin")
        val today = LocalDate.of(2026, 10, 26) // Monday after DST switch on Sunday Oct 25
        val sunday = LocalDate.of(2026, 10, 25)
        val events = listOf(
            event(sunday.atTime(0, 30).atZone(zone).toInstant().toEpochMilli(), id = 1),
        )
        // Strip for week containing Sunday Oct 25
        val strip = StudyGrid.buildDayStrip(events, sunday, zone, java.util.Locale.ENGLISH)
        val sunCell = strip.first { it.date == sunday }
        assertTrue(sunCell.reviewed)
        assertTrue(sunCell.isToday)
        assertFalse(strip.first { it.date == sunday.minusDays(1) }.reviewed)
    }
}
