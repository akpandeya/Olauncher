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

    @Test
    fun placementRespectsBottomSystemInset() {
        val word = ScreenBox(100, 100, 200, 140)
        val bottomInset = 64
        val cardHeight = 200
        val card = MeaningCardPlacement.place(word, screenHeight, cardHeight, bottomInset = bottomInset)
        val expectedBottom = screenHeight - bottomInset - MeaningCardPlacement.MARGIN_PX
        val expectedTop = expectedBottom - cardHeight
        assertEquals(expectedTop, card.top)
        assertEquals(expectedBottom, card.bottom)
    }

    @Test
    fun grownHeightRepositionsCardToStayAboveBottomInset() {
        // Pins the grown-height case with distinguishing values: small initial height (110)
        // vs large final height (310) with a non-zero bottom inset (48).
        val word = ScreenBox(100, 150, 200, 190)
        val bottomInset = 48
        val smallInitialHeight = 110
        val largeFinalHeight = 310

        val initialCard = MeaningCardPlacement.place(
            word, screenHeight, cardHeight = smallInitialHeight, bottomInset = bottomInset
        )
        val grownCard = MeaningCardPlacement.place(
            word, screenHeight, cardHeight = largeFinalHeight, bottomInset = bottomInset
        )

        val usableBottom = screenHeight - bottomInset - MeaningCardPlacement.MARGIN_PX
        assertEquals(usableBottom, initialCard.bottom)
        assertEquals(usableBottom, grownCard.bottom)

        // Distinguishing tops:
        assertEquals(usableBottom - smallInitialHeight, initialCard.top)
        assertEquals(usableBottom - largeFinalHeight, grownCard.top)
        assertTrue(grownCard.top < initialCard.top)

        // Verifies the defect: without repositioning on growth, the card bottom would
        // extend past usableBottom and past screenHeight into gesture navigation:
        val unadjustedBottom = initialCard.top + largeFinalHeight
        assertTrue(unadjustedBottom > usableBottom)
        assertTrue(unadjustedBottom > screenHeight)
    }

    @Test
    fun grownHeightFlipsToTopWhenBottomWouldOverlapWord() {
        // Word center is at 480 (top half of 1000px screen), so preferred side is bottom.
        val word = ScreenBox(100, 460, 200, 500)
        val bottomInset = 60
        val usableBottom = screenHeight - bottomInset - MeaningCardPlacement.MARGIN_PX // 932

        val smallInitialHeight = 120
        // Initial: bottom placement is 932 - 120 = 812 to 932. No overlap with word (460..500).
        val initialCard = MeaningCardPlacement.place(
            word, screenHeight, cardHeight = smallInitialHeight, bottomInset = bottomInset
        )
        assertEquals(usableBottom - smallInitialHeight, initialCard.top)
        assertEquals(usableBottom, initialCard.bottom)

        // Grown: large height = 460.
        // Bottom overlap: [472, 500] = 28px. Top overlap: [460, 468] = 8px.
        // Top placement wins because it has smaller overlap.
        val grownCard = MeaningCardPlacement.place(
            word, screenHeight, cardHeight = 460, bottomInset = bottomInset
        )
        assertEquals(MeaningCardPlacement.MARGIN_PX, grownCard.top)
        assertEquals(MeaningCardPlacement.MARGIN_PX + 460, grownCard.bottom)
    }
}
