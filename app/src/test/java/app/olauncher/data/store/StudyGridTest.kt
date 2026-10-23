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
    fun `window count is inclusive of today and truncates at the window edge`() {
        val today = LocalDate.of(2026, 10, 21)
        val inside = listOf(today, today.minusDays(83), today.minusDays(84), today.minusDays(200))
        val events = inside.mapIndexed { index, date -> event(millis(date), id = index.toLong() + 1) }
        // 84-day trailing window: today and -83 in, -84 and -200 out.
        assertEquals(2, StudyGrid.studiedInWindow(events, today, zone))
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
        val studied = LocalDate.of(2026, 10, 24)
        val events = listOf(event(studied.atTime(12, 0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()))
        val cells = StudyGrid.build(events, today, zone)
        val cell = cells.first { it.date == studied }
        assertTrue(cell.studied)
        assertFalse(cells.first { it.date == today.minusDays(1) }.studied)
    }
}
