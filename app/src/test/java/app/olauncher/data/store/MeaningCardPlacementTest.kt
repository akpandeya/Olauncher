package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningCardPlacementTest {

    private val screenHeight = 1000

    @Test
    fun wordInTopHalfPutsCardAtTheBottom() {
        val word = ScreenBox(100, 100, 200, 140)
        val card = MeaningCardPlacement.place(word, screenHeight, cardHeight = 200)
        // Bottom-aligned: margin 8 above the screen edge.
        assertEquals(1000 - MeaningCardPlacement.MARGIN_PX - 200, card.top)
        assertEquals(1000 - MeaningCardPlacement.MARGIN_PX, card.bottom)
        assertTrue(card.top >= word.bottom)
    }

    @Test
    fun wordInBottomHalfPutsCardAtTheTop() {
        val word = ScreenBox(100, 800, 200, 840)
        val card = MeaningCardPlacement.place(word, screenHeight, cardHeight = 200)
        assertEquals(MeaningCardPlacement.MARGIN_PX, card.top)
        assertEquals(MeaningCardPlacement.MARGIN_PX + 200, card.bottom)
        assertTrue(card.bottom <= word.top)
    }

    @Test
    fun wordOnTheMidlineCountsAsBottomHalf() {
        // Center exactly on the midline: the card moves out of the way to the top.
        val word = ScreenBox(0, 400, 100, 600)
        val card = MeaningCardPlacement.place(word, screenHeight, cardHeight = 200)
        assertEquals(MeaningCardPlacement.MARGIN_PX, card.top)
    }

    @Test
    fun preferredSideStaysWhenTheOtherWouldCoverMore() {
        // The word's box reaches 3 px into the bottom placement's band, but the top
        // placement would cover 200 px of it — the smaller overlap keeps the card below.
        val word = ScreenBox(0, 0, 100, 795)
        val card = MeaningCardPlacement.place(word, screenHeight, cardHeight = 200)
        assertEquals(1000 - MeaningCardPlacement.MARGIN_PX - 200, card.top)
    }

    @Test
    fun bothSidesFullyCoveredKeepsThePreferredSide() {
        // A word box spanning both bands covers each placement by exactly the card height —
        // a tie. The center sits in the top half, so the bottom placement (the preferred
        // side) stays.
        val word = ScreenBox(0, 0, 100, 999)
        val card = MeaningCardPlacement.place(word, screenHeight, cardHeight = 300)
        assertEquals(1000 - MeaningCardPlacement.MARGIN_PX - 300, card.top)
    }

    @Test
    fun cardTallerThanTheScreenStillReturnsAPosition() {
        val word = ScreenBox(0, 400, 100, 600)
        val card = MeaningCardPlacement.place(word, screenHeight, cardHeight = 1200)
        // Top placement (the preferred side on a tie): the smaller overlap, deterministically.
        assertEquals(MeaningCardPlacement.MARGIN_PX, card.top)
    }
}
