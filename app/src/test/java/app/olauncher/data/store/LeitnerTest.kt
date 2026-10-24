package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitnerTest {

    @Test
    fun `intervals match specification`() {
        assertEquals(listOf(1, 2, 4, 8, 16, 32, 64), Leitner.INTERVALS)
    }

    @Test
    fun `new word is due today`() {
        val today = 19500L
        assertEquals(today, Leitner.newWordDueDay(today))
    }

    @Test
    fun `each box step advances box and sets correct dueDay`() {
        val today = 19500L

        var word = Word(id = 1L, text = "Wort", box = 1, dueDay = today, addedAt = 1000L)
        for (box in 1 until Leitner.learnedBox) {
            word = Leitner.knowIt(word, today)
            assertEquals(box + 1, word.box)
            assertEquals(today + Leitner.INTERVALS[box], word.dueDay)
            assertNull(word.learnedAt)
        }
    }

    @Test
    fun `a word at the last box is marked learned by knowIt instead of advancing`() {
        val today = 19500L
        val word = Word(id = 5L, text = "Meister", box = Leitner.learnedBox, dueDay = today, addedAt = 1000L)

        val learned = Leitner.knowIt(word, today)
        assertEquals(Leitner.learnedBox, learned.box)
        assertEquals(today, learned.learnedAt)
        assertEquals(today, learned.dueDay)
    }

    @Test
    fun `almost keeps the box and halves the interval`() {
        val today = 19500L
        // Box 1: interval 1, halved to 0, floored at 1 day.
        val word1 = Word(id = 1L, text = "a", box = 1, dueDay = today, addedAt = 1000L)
        val almost1 = Leitner.almost(word1, today)
        assertEquals(1, almost1.box)
        assertEquals(today + 1, almost1.dueDay)
        assertNull(almost1.learnedAt)

        // Box 4: interval 8, halved to 4.
        val word4 = Word(id = 2L, text = "b", box = 4, dueDay = today, addedAt = 1000L)
        val almost4 = Leitner.almost(word4, today)
        assertEquals(4, almost4.box)
        assertEquals(today + 4, almost4.dueDay)

        // Box 6: interval 32, halved to 16.
        val word6 = Word(id = 3L, text = "c", box = 6, dueDay = today, addedAt = 1000L)
        val almost6 = Leitner.almost(word6, today)
        assertEquals(6, almost6.box)
        assertEquals(today + 16, almost6.dueDay)
    }

    @Test
    fun `almost on an odd interval rounds down but never below one day`() {
        val today = 19500L
        // Box 2: interval 2, halved to 1.
        val almost2 = Leitner.almost(Word(id = 1L, text = "a", box = 2, dueDay = today, addedAt = 0L), today)
        assertEquals(today + 1, almost2.dueDay)
        // Box 7: interval 64, halved to 32.
        val almost7 = Leitner.almost(Word(id = 2L, text = "b", box = 7, dueDay = today, addedAt = 0L), today)
        assertEquals(today + 32, almost7.dueDay)
    }

    @Test
    fun `again resets unlearned word to box 1 and sets dueDay to tomorrow`() {
        val today = 19500L
        val word = Word(id = 10L, text = "Vergessen", box = 4, dueDay = today, addedAt = 1000L)

        val reset = Leitner.again(word, today)
        assertEquals(1, reset.box)
        assertEquals(today + 1, reset.dueDay)
        assertNull(reset.learnedAt)
    }

    @Test
    fun `again resets learned word to box 1 tomorrow and clears learnedAt`() {
        val today = 19500L
        val learnedWord = Word(
            id = 11L, text = "Wiederholt", box = Leitner.learnedBox, dueDay = today,
            addedAt = 1000L, learnedAt = today - 5
        )

        val reset = Leitner.again(learnedWord, today)
        assertEquals(1, reset.box)
        assertEquals(today + 1, reset.dueDay)
        assertNull(reset.learnedAt)
    }

    @Test
    fun `due ordering and limit`() {
        val today = 19500L

        val overdueOld = Word(id = 1L, text = "a", dueDay = today - 2, addedAt = 200L)
        val overdueRecent = Word(id = 2L, text = "b", dueDay = today - 2, addedAt = 300L)
        val dueTodayFirst = Word(id = 3L, text = "c", dueDay = today, addedAt = 100L)
        val dueTodaySecond = Word(id = 4L, text = "d", dueDay = today, addedAt = 500L)
        val future = Word(id = 5L, text = "e", dueDay = today + 1, addedAt = 50L)

        val words = listOf(dueTodaySecond, future, overdueRecent, dueTodayFirst, overdueOld)

        val dueAll = Leitner.due(words, today)
        assertEquals(4, dueAll.size)
        // Ordered by dueDay then addedAt:
        // dueDay today - 2: overdueOld (addedAt 200), overdueRecent (addedAt 300)
        // dueDay today: dueTodayFirst (addedAt 100), dueTodaySecond (addedAt 500)
        assertEquals(listOf(overdueOld, overdueRecent, dueTodayFirst, dueTodaySecond), dueAll)

        val dueLimited = Leitner.due(words, today, limit = 2)
        assertEquals(listOf(overdueOld, overdueRecent), dueLimited)

        val dueLimitZero = Leitner.due(words, today, limit = 0)
        assertTrue(dueLimitZero.isEmpty())

        val dueLimitNegative = Leitner.due(words, today, limit = -1)
        assertTrue(dueLimitNegative.isEmpty())
    }

    @Test
    fun `learned words are never due even if dueDay is in the past`() {
        val today = 19500L
        val learnedWord = Word(
            id = 1L,
            text = "Gelernt",
            box = Leitner.learnedBox,
            dueDay = today - 10,
            addedAt = 100L,
            learnedAt = today - 5
        )
        val activeWord = Word(
            id = 2L,
            text = "Aktiv",
            box = 1,
            dueDay = today,
            addedAt = 200L,
            learnedAt = null
        )

        val dueWords = Leitner.due(listOf(learnedWord, activeWord), today)
        assertEquals(1, dueWords.size)
        assertEquals("Aktiv", dueWords[0].text)
    }

    @Test
    fun `box counts and segment widths with 0 words`() {
        val emptyWords = emptyList<Word>()
        val counts = Leitner.boxCounts(emptyWords)
        assertEquals(Leitner.learnedBox, counts.size)
        assertEquals(List(Leitner.learnedBox) { 0 }, counts)

        val widths = Leitner.segmentWidths(counts)
        assertEquals(Leitner.learnedBox, widths.size)
        assertEquals(List(Leitner.learnedBox) { 0f }, widths)

        val widthsFromWords = Leitner.segmentWidths(emptyWords, totalWidth = 100f)
        assertEquals(List(Leitner.learnedBox) { 0f }, widthsFromWords)
    }

    @Test
    fun `box counts and segment widths when all words are in box 1`() {
        val words = (1..5).map {
            Word(id = it.toLong(), text = "word$it", box = 1, dueDay = 1000L, addedAt = 1000L)
        }
        val counts = Leitner.boxCounts(words)
        assertEquals(Leitner.learnedBox, counts.size)
        assertEquals(5, counts[0])
        for (i in 1 until Leitner.learnedBox) {
            assertEquals("Box ${i + 1} should be empty", 0, counts[i])
        }

        val widths = Leitner.segmentWidths(counts)
        assertEquals(1.0f, widths[0], 0.0001f)
        for (i in 1 until Leitner.learnedBox) {
            assertEquals("Box ${i + 1} width should be 0", 0f, widths[i], 0.0001f)
        }

        val scaledWidths = Leitner.segmentWidths(counts, totalWidth = 350f)
        assertEquals(350f, scaledWidths[0], 0.0001f)
        for (i in 1 until Leitner.learnedBox) {
            assertEquals(0f, scaledWidths[i], 0.0001f)
        }
    }

    @Test
    fun `box counts and segment widths with one empty box`() {
        // Boxes 1, 2, 3, 5, 6, 7 have 1 word each; box 4 is empty
        val boxesWithWords = listOf(1, 2, 3, 5, 6, 7)
        val words = boxesWithWords.mapIndexed { idx, box ->
            Word(id = (idx + 1).toLong(), text = "w$idx", box = box, dueDay = 1000L, addedAt = 1000L)
        }
        val counts = Leitner.boxCounts(words)
        assertEquals(Leitner.learnedBox, counts.size)
        assertEquals(listOf(1, 1, 1, 0, 1, 1, 1), counts)

        val widths = Leitner.segmentWidths(counts)
        assertEquals(Leitner.learnedBox, widths.size)
        assertEquals("Empty box 4 must have 0 width", 0f, widths[3], 0.0001f)

        val nonZeroWidths = widths.filterIndexed { index, _ -> index != 3 }
        assertEquals(6, nonZeroWidths.size)
        for (w in nonZeroWidths) {
            assertEquals(1f / 6f, w, 0.0001f)
        }
        assertEquals(1f, widths.sum(), 0.0001f)
    }

    @Test
    fun `segmentColors smoothly blends from sand to sage across learnedBox segments`() {
        val sand = 0x00D4C4A8
        val sage = 0x009CAF88
        val colors = Leitner.segmentColors(sand, sage, Leitner.learnedBox)
        assertEquals(Leitner.learnedBox, colors.size)
        assertEquals("First box must be sand", sand, colors.first())
        assertEquals("Last box must be sage", sage, colors.last())
    }
}
