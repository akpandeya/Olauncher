package app.olauncher.data.store

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Geometry and presence data for the study dot grid: the trailing weeks laid
 * out like a contribution graph — one column per week (Monday first), one row
 * per weekday. Pure so it can be unit tested; the view only draws [cells].
 */
object StudyGrid {

    const val WEEKS = 12

    data class Cell(val column: Int, val row: Int, val date: LocalDate, val studied: Boolean)

    /**
     * Cells for the [weeks] weeks ending with the current one, oldest column
     * first. Days after [today] are not part of the grid — the current week's
     * column simply grows as the week progresses.
     */
    fun build(
        events: List<ReviewEvent>,
        today: LocalDate,
        zone: ZoneId,
        weeks: Int = WEEKS,
    ): List<Cell> {
        val studied = ReviewComebacks.studyDays(events, zone).toSet()
        val startMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks((weeks - 1).toLong())
        val cells = mutableListOf<Cell>()
        for (column in 0 until weeks) {
            for (row in 0 until 7) {
                val date = startMonday.plusDays((column * 7 + row).toLong())
                if (date > today) break
                cells.add(Cell(column, row, date, date in studied))
            }
        }
        return cells
    }

    /** Distinct study days within the [days] days ending today, inclusive. */
    fun studiedInWindow(events: List<ReviewEvent>, today: LocalDate, zone: ZoneId, days: Int = WEEKS * 7): Int {
        val earliest = today.minusDays((days - 1).toLong())
        return ReviewComebacks.studyDays(events, zone).count { it >= earliest && it <= today }
    }
}
