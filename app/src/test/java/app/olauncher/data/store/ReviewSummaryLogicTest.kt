package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSummaryLogicTest {

    private fun word(id: Long, text: String, meaning: String = "", context: String = "") = Word(
        id = id,
        text = text,
        meaning = meaning,
        context = context,
        dueDay = 0L,
        addedAt = 0L,
    )

    private fun item(index: Int, result: String, text: String = "Word $index") = ReviewSummaryItem(
        word = word(index.toLong(), text),
        result = result,
        sessionIndex = index,
    )

    @Test
    fun `sort places again first, then almost, then knew`() {
        val items = listOf(
            item(0, ReviewEvent.Result.KNEW),
            item(1, ReviewEvent.Result.ALMOST),
            item(2, ReviewEvent.Result.AGAIN),
            item(3, ReviewEvent.Result.KNEW),
            item(4, ReviewEvent.Result.AGAIN),
            item(5, ReviewEvent.Result.ALMOST),
        )

        val sorted = ReviewSummaryLogic.sort(items)

        val expectedResults = listOf(
            ReviewEvent.Result.AGAIN,
            ReviewEvent.Result.AGAIN,
            ReviewEvent.Result.ALMOST,
            ReviewEvent.Result.ALMOST,
            ReviewEvent.Result.KNEW,
            ReviewEvent.Result.KNEW,
        )
        assertEquals(expectedResults, sorted.map { it.result })
    }

    @Test
    fun `sort preserves session order for ties`() {
        val items = listOf(
            item(0, ReviewEvent.Result.KNEW),
            item(1, ReviewEvent.Result.AGAIN),
            item(2, ReviewEvent.Result.ALMOST),
            item(3, ReviewEvent.Result.AGAIN),
            item(4, ReviewEvent.Result.KNEW),
            item(5, ReviewEvent.Result.ALMOST),
            item(6, ReviewEvent.Result.AGAIN),
        )

        val sorted = ReviewSummaryLogic.sort(items)

        assertEquals(listOf(1, 3, 6), sorted.filter { it.result == ReviewEvent.Result.AGAIN }.map { it.sessionIndex })
        assertEquals(listOf(2, 5), sorted.filter { it.result == ReviewEvent.Result.ALMOST }.map { it.sessionIndex })
        assertEquals(listOf(0, 4), sorted.filter { it.result == ReviewEvent.Result.KNEW }.map { it.sessionIndex })
    }

    @Test
    fun `formatCorrection formats meaning and context`() {
        assertEquals(
            "yearning for distant places · Ich habe großes Fernweh",
            ReviewSummaryLogic.formatCorrection("yearning for distant places", "Ich habe großes Fernweh"),
        )
        assertEquals(
            "yearning for distant places",
            ReviewSummaryLogic.formatCorrection("yearning for distant places", ""),
        )
        assertEquals(
            "Ich habe großes Fernweh",
            ReviewSummaryLogic.formatCorrection("", "Ich habe großes Fernweh"),
        )
    }
}
