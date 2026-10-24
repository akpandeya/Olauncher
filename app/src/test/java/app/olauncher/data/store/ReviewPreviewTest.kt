package app.olauncher.data.store

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewPreviewTest {

    private fun word(box: Int, id: Long = 1, learnedAt: Long? = null, dueDay: Long = 100, listIds: List<String> = emptyList()) =
        Word(id = id, text = "w$id", box = box, dueDay = dueDay, addedAt = 0, learnedAt = learnedAt, listIds = listIds)

    @Test
    fun againAlwaysSaysTomorrow() {
        assertEquals(
            ReviewPreview.Interval.AgainTomorrow,
            ReviewPreview.interval(word(5), ReviewEvent.Result.AGAIN, 100),
        )
    }

    @Test
    fun almostKeepsTheBox() {
        assertEquals(
            ReviewPreview.Interval.SameBox,
            ReviewPreview.interval(word(3), ReviewEvent.Result.ALMOST, 100),
        )
    }

    @Test
    fun knewItShowsNextBoxAndItsInterval() {
        // Box 2 -> box 3, whose interval is INTERVALS[2] = 4 days.
        assertEquals(
            ReviewPreview.Interval.Promoted(box = 3, days = 4),
            ReviewPreview.interval(word(2), ReviewEvent.Result.KNEW, 100),
        )
    }

    @Test
    fun knewItOnTheLastBoxIsLearned() {
        assertEquals(
            ReviewPreview.Interval.Learned,
            ReviewPreview.interval(word(Leitner.learnedBox), ReviewEvent.Result.KNEW, 100),
        )
    }

    @Test
    fun progressCountsOnlyThisWordsReviews() {
        val events = listOf(
            ReviewEvent(1, 7, 0, ReviewEvent.Result.KNEW, 1, 2),
            ReviewEvent(2, 7, 0, ReviewEvent.Result.AGAIN, 2, 1),
            ReviewEvent(3, 8, 0, ReviewEvent.Result.KNEW, 1, 2),
        )
        val progress = WordProgress.of(word(2, id = 7, dueDay = 55), events)
        assertEquals(WordProgress.Progress(box = 2, learned = false, nextReviewDay = 55, reviewCount = 2), progress)
    }

    @Test
    fun progressMarksLearnedWords() {
        assertEquals(true, WordProgress.of(word(7, learnedAt = 3), emptyList()).learned)
    }

    @Test
    fun formatsTheDayLikeTheDesign() {
        val day = LocalDate.of(2026, 10, 26).toEpochDay()
        assertEquals("Mon 26 Oct", WordProgress.formatDay(day, Locale.ENGLISH))
    }

    @Test
    fun listSummaryCountsDueAndMembers() {
        val words = listOf(
            word(1, id = 1, dueDay = 10, listIds = listOf("a")),
            word(1, id = 2, dueDay = 20, listIds = listOf("a")),
            word(1, id = 3, dueDay = 5, learnedAt = 4, listIds = listOf("a")),
            word(1, id = 4, dueDay = 5),
        )
        assertEquals(3, WordListsSummary.membersOf(words, "a").size)
        assertEquals(1, WordListsSummary.dueCount(WordListsSummary.membersOf(words, "a"), 10))
        assertEquals(2, WordListsSummary.dueCount(words, 10))
    }
}
