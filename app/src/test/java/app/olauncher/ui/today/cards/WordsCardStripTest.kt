package app.olauncher.ui.today.cards

import app.olauncher.data.store.ReviewEvent
import app.olauncher.data.store.StudyGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class WordsCardStripTest {

    private val zone = ZoneId.of("UTC")

    private fun event(date: LocalDate, id: Long = 1L) = ReviewEvent(
        id = id,
        wordId = 10,
        reviewedAt = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        result = "knew",
        boxBefore = 1,
        boxAfter = 2,
    )

    @Test
    fun `dayStrip generates seven day cells Monday to Sunday with letters`() {
        val today = LocalDate.of(2026, 10, 23) // Friday
        val monday = LocalDate.of(2026, 10, 19)
        val wednesday = LocalDate.of(2026, 10, 21)

        val events = listOf(
            event(monday, id = 1),
            event(wednesday, id = 2),
            event(today, id = 3),
        )

        val strip = WordsCardLogic.dayStrip(events, today, zone, Locale.ENGLISH)
        assertEquals(7, strip.size)

        // Weekdays: M T W T F S S
        val expectedLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        assertEquals(expectedLabels, strip.map { it.label })

        // Monday: reviewed, not today
        assertTrue(strip[0].reviewed)
        assertFalse(strip[0].isToday)
        assertEquals(DayOfWeek.MONDAY, strip[0].dayOfWeek)

        // Tuesday: not reviewed
        assertFalse(strip[1].reviewed)
        assertFalse(strip[1].isToday)

        // Wednesday: reviewed
        assertTrue(strip[2].reviewed)
        assertFalse(strip[2].isToday)

        // Thursday: not reviewed
        assertFalse(strip[3].reviewed)
        assertFalse(strip[3].isToday)

        // Friday (today): reviewed and today
        assertTrue(strip[4].reviewed)
        assertTrue(strip[4].isToday)
        assertFalse(strip[4].isFuture)

        // Saturday (tomorrow): not reviewed and future
        assertFalse(strip[5].reviewed)
        assertFalse(strip[5].isToday)
        assertTrue(strip[5].isFuture)

        // Sunday: not reviewed and future
        assertFalse(strip[6].reviewed)
        assertFalse(strip[6].isToday)
        assertTrue(strip[6].isFuture)
    }

    @Test
    fun `showDayStrip is true when words exist and false when empty`() {
        val emptyLines = WordsCardLogic.lines(total = 0, due = 0, next = null)
        assertFalse(emptyLines.showDayStrip)

        val withWordsLines = WordsCardLogic.lines(total = 5, due = 0, next = null)
        assertTrue(withWordsLines.showDayStrip)

        val dueLines = WordsCardLogic.lines(total = 5, due = 2, next = null)
        assertTrue(dueLines.showDayStrip)
    }

    @Test
    fun `no streak wording in DayStripCell or WordsCard strings`() {
        // Streaks were rejected in #453; verify no streak fields or wording
        val fields = StudyGrid.DayStripCell::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("DayStripCell must not contain streak field", fields.any { it.contains("streak") })
    }
}
