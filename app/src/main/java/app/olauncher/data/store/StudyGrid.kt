package app.olauncher.data.store

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Geometry and presence data for the study dot grid: the trailing weeks laid
 * out like a contribution graph — one column per week (Monday first), one row
 * per weekday. Pure so it can be unit tested; the view only draws [cells].
 */
object StudyGrid {

    const val WEEKS = 12

    data class Cell(
        val column: Int,
        val row: Int,
        val date: LocalDate,
        val count: Int = 0,
        val shadeLevel: Int = 0,
        val isToday: Boolean = false,
        val studied: Boolean = count > 0,
    ) {
        constructor(column: Int, row: Int, date: LocalDate, studied: Boolean) : this(
            column = column,
            row = row,
            date = date,
            count = if (studied) 1 else 0,
            shadeLevel = if (studied) 1 else 0,
            isToday = false,
            studied = studied,
        )
    }

    data class MonthLabel(
        val column: Int,
        val name: String,
    )

    data class DayStripCell(
        val dayOfWeek: DayOfWeek,
        val date: LocalDate,
        val label: String,
        val reviewed: Boolean,
        val isToday: Boolean,
        val isFuture: Boolean,
    )

    /**
     * Buckets review events by local date in [zone] and counts the number of reviews on each day.
     */
    fun reviewCountsByDate(events: List<ReviewEvent>, zone: ZoneId): Map<LocalDate, Int> {
        return events.groupingBy { Instant.ofEpochMilli(it.reviewedAt).atZone(zone).toLocalDate() }
            .eachCount()
    }

    /**
     * Returns the shade level (0 for neutral, 1..4 for intensity) based on review count.
     * 0: neutral (zero reviews)
     * 1: 1-2 reviews
     * 2: 3-5 reviews
     * 3: 6-9 reviews
     * 4: 10+ reviews
     */
    fun shadeLevel(count: Int): Int = when {
        count <= 0 -> 0
        count in 1..2 -> 1
        count in 3..5 -> 2
        count in 6..9 -> 3
        else -> 4
    }

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
        val counts = reviewCountsByDate(events, zone)
        val startMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks((weeks - 1).toLong())
        val cells = mutableListOf<Cell>()
        for (column in 0 until weeks) {
            for (row in 0 until 7) {
                val date = startMonday.plusDays((column * 7 + row).toLong())
                if (date > today) break
                val count = counts[date] ?: 0
                val isToday = (date == today)
                cells.add(
                    Cell(
                        column = column,
                        row = row,
                        date = date,
                        count = count,
                        shadeLevel = shadeLevel(count),
                        isToday = isToday,
                        studied = count > 0,
                    )
                )
            }
        }
        return cells
    }

    /**
     * Month labels for the grid columns.
     * Adds a label at the column where the 1st of the month falls.
     * Also labels column 0 if the next month begins at least 2 weeks away.
     */
    fun monthLabels(cells: List<Cell>, locale: Locale = Locale.getDefault()): List<MonthLabel> {
        if (cells.isEmpty()) return emptyList()
        val labels = mutableListOf<MonthLabel>()
        val byColumn = cells.groupBy { it.column }
        val maxCol = byColumn.keys.maxOrNull() ?: 0

        var firstNewMonthCol: Int? = null
        for (col in 0..maxCol) {
            val colCells = byColumn[col] ?: continue
            val firstOfMonth = colCells.firstOrNull { it.date.dayOfMonth == 1 }
            if (firstOfMonth != null) {
                if (firstNewMonthCol == null && col > 0) {
                    firstNewMonthCol = col
                }
                val monthName = firstOfMonth.date.month.getDisplayName(TextStyle.SHORT, locale)
                labels.add(MonthLabel(col, monthName))
            }
        }

        // Check if column 0 needs a month label:
        // If col 0 doesn't already have a label, and the first month transition is at least 2 weeks away (or no transition):
        if (labels.none { it.column == 0 }) {
            val firstColCells = byColumn[0]
            if (firstColCells != null && firstColCells.isNotEmpty()) {
                val nextMonthCol = firstNewMonthCol ?: (maxCol + 1)
                if (nextMonthCol >= 2) {
                    val monthName = firstColCells.first().date.month.getDisplayName(TextStyle.SHORT, locale)
                    labels.add(0, MonthLabel(0, monthName))
                }
            }
        }

        return labels
    }

    /**
     * 7-day strip cells for the current week (Monday through Sunday) containing [today].
     * Filled on review days; today is outlined.
     */
    fun buildDayStrip(
        events: List<ReviewEvent>,
        today: LocalDate,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): List<DayStripCell> {
        val reviewDays = ReviewComebacks.studyDays(events, zone).toSet()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return (0 until 7).map { i ->
            val date = monday.plusDays(i.toLong())
            val dayOfWeek = DayOfWeek.of(i + 1)
            val narrow = dayOfWeek.getDisplayName(TextStyle.NARROW, locale)
            val label = if (narrow.isNotBlank()) narrow.take(1).uppercase(locale) else dayOfWeek.name.take(1)
            DayStripCell(
                dayOfWeek = dayOfWeek,
                date = date,
                label = label,
                reviewed = (date in reviewDays) && (date <= today),
                isToday = (date == today),
                isFuture = (date > today),
            )
        }
    }
}
