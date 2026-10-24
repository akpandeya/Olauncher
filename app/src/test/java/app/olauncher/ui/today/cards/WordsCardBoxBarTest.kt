package app.olauncher.ui.today.cards

import app.olauncher.data.store.Leitner
import app.olauncher.data.store.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsCardBoxBarTest {

    private fun word(id: Long, box: Int) = Word(
        id = id,
        text = "word$id",
        box = box,
        dueDay = 1000L,
        addedAt = 1000L,
    )

    @Test
    fun `box counts and segment widths with 0 words`() {
        val words = emptyList<Word>()

        val counts = WordsCardLogic.boxCounts(words)
        assertEquals(Leitner.learnedBox, counts.size)
        assertEquals(List(Leitner.learnedBox) { 0 }, counts)

        val widths = WordsCardLogic.segmentWidths(counts)
        assertEquals(Leitner.learnedBox, widths.size)
        assertEquals(List(Leitner.learnedBox) { 0f }, widths)

        val widthsFromWords = WordsCardLogic.segmentWidths(words, totalWidth = 250f)
        assertEquals(List(Leitner.learnedBox) { 0f }, widthsFromWords)

        val lines = WordsCardLogic.lines(total = 0, due = 0, next = null)
        assertFalse("Box bar must be hidden when there are no words", lines.showBoxBar)
    }

    @Test
    fun `box counts and segment widths with all words in box 1`() {
        val totalWords = 8
        val words = (1..totalWords).map { word(it.toLong(), box = 1) }

        val counts = WordsCardLogic.boxCounts(words)
        assertEquals(Leitner.learnedBox, counts.size)
        assertEquals(totalWords, counts[0])
        for (i in 1 until Leitner.learnedBox) {
            assertEquals("Box ${i + 1} count should be 0", 0, counts[i])
        }

        val widths = WordsCardLogic.segmentWidths(counts)
        assertEquals(1.0f, widths[0], 0.0001f)
        for (i in 1 until Leitner.learnedBox) {
            assertEquals("Empty box ${i + 1} takes no width", 0f, widths[i], 0.0001f)
        }

        val pixelWidths = WordsCardLogic.segmentWidths(words, totalWidth = 320f)
        assertEquals(320f, pixelWidths[0], 0.0001f)
        for (i in 1 until Leitner.learnedBox) {
            assertEquals(0f, pixelWidths[i], 0.0001f)
        }

        val lines = WordsCardLogic.lines(total = totalWords, due = 3, next = words.first())
        assertTrue("Box bar must be visible when there are words", lines.showBoxBar)
    }

    @Test
    fun `box counts and segment widths with one empty box`() {
        // Distribute words so that box 3 is empty and all other boxes have non-zero words
        val words = listOf(
            word(1, box = 1),
            word(2, box = 2),
            // Box 3 has 0 words (empty box)
            word(3, box = 4),
            word(4, box = 5),
            word(5, box = 6),
            word(6, box = 7),
        )

        val counts = WordsCardLogic.boxCounts(words)
        assertEquals(Leitner.learnedBox, counts.size)
        assertEquals(0, counts[2]) // box 3 (0-indexed 2) is empty

        val widths = WordsCardLogic.segmentWidths(counts)
        assertEquals(Leitner.learnedBox, widths.size)
        assertEquals("Empty box 3 must take no width", 0f, widths[2], 0.0001f)

        // Non-empty boxes have 1 word each out of 6 total
        for (i in listOf(0, 1, 3, 4, 5, 6)) {
            assertEquals(1f / 6f, widths[i], 0.0001f)
        }
        assertEquals(1f, widths.sum(), 0.0001f)
    }

    @Test
    fun `box counts clamp out-of-range boxes into 1 to learnedBox`() {
        val words = listOf(
            word(1, box = -3),
            word(2, box = 0),
            word(3, box = 99),
        )
        val counts = WordsCardLogic.boxCounts(words)
        assertEquals(2, counts[0]) // -3 and 0 clamped to box 1
        assertEquals(1, counts[Leitner.learnedBox - 1]) // 99 clamped to learnedBox
    }

    @Test
    fun `number of boxes always equals Leitner learnedBox`() {
        assertEquals(Leitner.INTERVALS.size, Leitner.learnedBox)
        val counts = WordsCardLogic.boxCounts(emptyList())
        assertEquals(Leitner.learnedBox, counts.size)
        val widths = WordsCardLogic.segmentWidths(counts)
        assertEquals(Leitner.learnedBox, widths.size)
    }
}
