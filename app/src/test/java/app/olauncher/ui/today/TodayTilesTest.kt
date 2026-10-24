package app.olauncher.ui.today

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayTilesTest {

    @Test
    fun `span is the tile span only when the tile is on`() {
        assertEquals(2, TodayTiles.span(tileSpan = 2, tileOn = true))
        assertNull(TodayTiles.span(tileSpan = 2, tileOn = false))
        assertNull(TodayTiles.span(tileSpan = null, tileOn = true))
    }

    @Test
    fun `span is clamped to the grid columns`() {
        assertEquals(TodayWidgetResize.COLUMNS, TodayTiles.span(7, true))
        assertEquals(1, TodayTiles.span(0, true))
    }

    @Test
    fun `only the words card has a tile`() {
        assertEquals(listOf("words"), TodayTiles.tileCards(TodayCards.all).map { it.key })
    }

    @Test
    fun `other built-in cards have no tile span and no grid span`() {
        for (card in TodayCards.all.filter { it.key != "words" }) {
            assertNull(card.key, card.tileSpan)
            assertNull(card.key, card.gridSpan)
        }
    }
}
