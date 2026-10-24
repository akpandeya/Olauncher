package app.olauncher.ui.today

import app.olauncher.ui.today.cards.HeldCard
import app.olauncher.ui.today.cards.NextUpCard
import app.olauncher.ui.today.cards.NotesCard
import app.olauncher.ui.today.cards.SavedCard
import app.olauncher.ui.today.cards.WeatherCard
import app.olauncher.ui.today.cards.WellbeingCard
import app.olauncher.ui.today.cards.WordsCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayBentoOrderTest {

    @Test
    fun `built-in cards follow the design order`() {
        assertEquals(
            listOf(
                WeatherCard.KEY, WordsCard.KEY, NotesCard.KEY, WellbeingCard.KEY,
                SavedCard.KEY, NextUpCard.KEY, HeldCard.KEY,
            ),
            TodayCards.all.map { it.key },
        )
    }

    @Test
    fun `only Words and Notes are half width`() {
        val half = TodayCards.all.filter { it.halfWidth }.map { it.key }
        assertEquals(listOf(WordsCard.KEY, NotesCard.KEY), half)
    }

    @Test
    fun `cards with counts or controls in their heading draw it themselves`() {
        val own = TodayCards.all.filter { it.drawsOwnHeader }.map { it.key }.toSet()
        assertEquals(setOf(NotesCard.KEY, WellbeingCard.KEY, SavedCard.KEY, HeldCard.KEY), own)
    }

    @Test
    fun `only Held is tinted`() {
        assertEquals(listOf(HeldCard.KEY), TodayCards.all.filter { it.tinted }.map { it.key })
        assertTrue(HeldCard.tinted)
        assertFalse(WordsCard.tinted)
    }
}
