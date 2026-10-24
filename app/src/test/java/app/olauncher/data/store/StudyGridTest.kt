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
}
