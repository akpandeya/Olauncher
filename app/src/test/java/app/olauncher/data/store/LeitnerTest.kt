package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeitnerTest {

    @Test
    fun `intervals match specification`() {
        assertEquals(listOf(1, 2, 4, 8, 16), Leitner.INTERVALS)
    }

    @Test
    fun `new word is due today`() {
        val today = 19500L
        assertEquals(today, Leitner.newWordDueDay(today))
    }

    @Test
    fun `each box step advances box and sets correct dueDay`() {
        val today = 19500L

        // Box 1 -> Box 2 (interval = 2)
        val word1 = Word(id = 1L, text = "Wort", box = 1, dueDay = today, addedAt = 1000L)
        val stepped1 = Leitner.knowIt(word1, today)
        assertEquals(2, stepped1.box)
        assertEquals(today + 2, stepped1.dueDay)
        assertNull(stepped1.learnedAt)

        // Box 2 -> Box 3 (interval = 4)
        val word2 = stepped1
        val stepped2 = Leitner.knowIt(word2, today)
        assertEquals(3, stepped2.box)
        assertEquals(today + 4, stepped2.dueDay)
        assertNull(stepped2.learnedAt)

        // Box 3 -> Box 4 (interval = 8)
        val word3 = stepped2
        val stepped3 = Leitner.knowIt(word3, today)
        assertEquals(4, stepped3.box)
        assertEquals(today + 8, stepped3.dueDay)
        assertNull(stepped3.learnedAt)

        // Box 4 -> Box 5 (interval = 16)
        val word4 = stepped3
        val stepped4 = Leitner.knowIt(word4, today)
        assertEquals(5, stepped4.box)
        assertEquals(today + 16, stepped4.dueDay)
        assertNull(stepped4.learnedAt)
    }

    @Test
    fun `box 5 knowIt marks word as learned instead of advancing box`() {
        val today = 19500L
        val word5 = Word(id = 5L, text = "Meister", box = 5, dueDay = today, addedAt = 1000L)

        val learned = Leitner.knowIt(word5, today)
        assertEquals(5, learned.box)
        assertEquals(today, learned.learnedAt)
        assertEquals(today, learned.dueDay)
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
        val learnedWord = Word(id = 11L, text = "Wiederholt", box = 5, dueDay = today, addedAt = 1000L, learnedAt = today - 5)

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
            box = 5,
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
}
