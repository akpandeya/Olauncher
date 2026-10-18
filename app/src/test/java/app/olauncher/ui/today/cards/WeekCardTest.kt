package app.olauncher.ui.today.cards

import app.olauncher.R
import app.olauncher.ui.today.TodayCards
import app.olauncher.wellbeing.DaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekCardTest {

    @Test
    fun `week card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == WeekCard.KEY }
        assertNotNull(card)
        assertEquals("week", card?.key)
        assertEquals(R.string.last_7_days, card?.titleRes)
        assertEquals(true, card?.defaultEnabled)
    }

    @Test
    fun `isAvailable returns false when fewer than 2 snapshots in last 7 days`() {
        val today = 100L

        // Empty
        assertFalse(WeekCard.isAvailable(emptyList(), today))

        // Only 1 snapshot in last 7 days (93..99)
        val oneSnapshot = listOf(DaySnapshot(epochDay = 95L))
        assertFalse(WeekCard.isAvailable(oneSnapshot, today))

        // Snapshots outside last 7 days (e.g. 90L and 91L, or today 100L)
        val outsideSnapshots = listOf(
            DaySnapshot(epochDay = 90L),
            DaySnapshot(epochDay = 91L),
            DaySnapshot(epochDay = 100L),
        )
        assertFalse(WeekCard.isAvailable(outsideSnapshots, today))

        // 1 inside (95L) and 1 outside (90L) = 1 in last 7 days
        val mixedOne = listOf(
            DaySnapshot(epochDay = 90L),
            DaySnapshot(epochDay = 95L),
        )
        assertFalse(WeekCard.isAvailable(mixedOne, today))
    }

    @Test
    fun `isAvailable returns true when at least 2 snapshots in last 7 days`() {
        val today = 100L

        // Exactly 2 snapshots in last 7 days (93..99)
        val twoSnapshots = listOf(
            DaySnapshot(epochDay = 93L),
            DaySnapshot(epochDay = 99L),
        )
        assertTrue(WeekCard.isAvailable(twoSnapshots, today))

        // 3 snapshots in last 7 days + 1 outside
        val multipleSnapshots = listOf(
            DaySnapshot(epochDay = 80L),
            DaySnapshot(epochDay = 94L),
            DaySnapshot(epochDay = 95L),
            DaySnapshot(epochDay = 96L),
        )
        assertTrue(WeekCard.isAvailable(multipleSnapshots, today))
    }

    @Test
    fun `isAvailable LocalDate overload delegates correctly`() {
        val date = LocalDate.of(2026, 10, 18)
        val epochDay = date.toEpochDay()
        val snapshots = listOf(
            DaySnapshot(epochDay = epochDay - 1),
            DaySnapshot(epochDay = epochDay - 2),
        )
        assertTrue(WeekCard.isAvailable(snapshots, date))
    }
}
