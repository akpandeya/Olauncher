package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSessionLogicTest {

    private val today = 19500L

    @Test
    fun `grade maps the three results onto Leitner`() {
        val word = Word(id = 1, text = "a", box = 3, dueDay = today, addedAt = 0L)

        val knew = ReviewSessionLogic.grade(word, ReviewEvent.Result.KNEW, today)
        assertEquals(4, knew.box)
        assertEquals(today + 8, knew.dueDay)

        val almost = ReviewSessionLogic.grade(word, ReviewEvent.Result.ALMOST, today)
        assertEquals(3, almost.box)
        assertEquals(today + 2, almost.dueDay)

        val again = ReviewSessionLogic.grade(word, ReviewEvent.Result.AGAIN, today)
        assertEquals(1, again.box)
        assertEquals(today + 1, again.dueDay)
    }

    @Test
    fun `an unknown result leaves the word untouched`() {
        val word = Word(id = 1, text = "a", box = 3, dueDay = today, addedAt = 0L)
        assertEquals(word, ReviewSessionLogic.grade(word, "bogus", today))
    }

    @Test
    fun `queue is due words oldest due first, learned excluded`() {
        val overdue = Word(id = 1, text = "a", box = 1, dueDay = today - 2, addedAt = 9L)
        val dueToday = Word(id = 2, text = "b", box = 1, dueDay = today, addedAt = 1L)
        val future = Word(id = 3, text = "c", box = 1, dueDay = today + 3, addedAt = 0L)
        val learned = Word(id = 4, text = "d", box = Leitner.learnedBox, dueDay = today, addedAt = 0L, learnedAt = today)

        assertEquals(listOf(overdue, dueToday), ReviewSessionLogic.queue(listOf(learned, dueToday, future, overdue), today))
        assertEquals(listOf(overdue), ReviewSessionLogic.queue(listOf(overdue, dueToday), today, limit = 1))
    }

    @Test
    fun `summarize counts each result`() {
        fun event(result: String, id: Long) =
            ReviewEvent(id = id, wordId = 1, reviewedAt = 0, result = result, boxBefore = 1, boxAfter = 1)

        val summary = ReviewSessionLogic.summarize(
            listOf(event("knew", 1), event("knew", 2), event("almost", 3), event("again", 4))
        )
        assertEquals(2, summary.knew)
        assertEquals(1, summary.almost)
        assertEquals(1, summary.again)
        assertEquals(4, summary.total)
    }
}
