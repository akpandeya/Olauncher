package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DrillReviewFeedTest {

    private val today = 19500L

    private val word1 = Word(id = 10, text = "durch", box = 2, dueDay = today, addedAt = 100L)
    private val word2 = Word(id = 20, text = "für", box = 1, dueDay = today, addedAt = 100L)

    @Test
    fun `two items on one word - the first counts`() {
        // First item on word 10 was answered correctly, second was wrong.
        val attemptsRightFirst = linkedMapOf(
            DrillItemId(DrillType.PREPOSITIONS, "10:1") to true,
            DrillItemId(DrillType.PREPOSITIONS, "10:2") to false,
        )
        val gradesRightFirst = DrillReviewFeed.grades(attemptsRightFirst, listOf(word1), today)
        assertEquals(1, gradesRightFirst.size)
        assertEquals(10L, gradesRightFirst[0].word.id)
        assertEquals(ReviewEvent.Result.KNEW, gradesRightFirst[0].result)
        assertEquals("drill:prepositions", gradesRightFirst[0].source)

        // First item on word 10 was answered wrong, second was right.
        val attemptsWrongFirst = linkedMapOf(
            DrillItemId(DrillType.PREPOSITIONS, "10:1") to false,
            DrillItemId(DrillType.PREPOSITIONS, "10:2") to true,
        )
        val gradesWrongFirst = DrillReviewFeed.grades(attemptsWrongFirst, listOf(word1), today)
        assertEquals(1, gradesWrongFirst.size)
        assertEquals(10L, gradesWrongFirst[0].word.id)
        assertEquals(ReviewEvent.Result.AGAIN, gradesWrongFirst[0].result)
    }

    @Test
    fun `wrong then right on retry - still AGAIN`() {
        // Simulate a DrillQuiz session where the user misses an item first, then retries and answers right.
        val item = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "10"),
            prompt = "Ich gehe durch ___ Park.",
            answer = "den",
            choices = listOf("den", "dem", "das", "des"),
        )
        val quiz = DrillQuiz(listOf(item))

        // First attempt: wrong
        quiz.answer("dem")
        quiz.advance()

        // Quiz switches / retries: second attempt right
        quiz.answer("den")
        quiz.advance()

        val attempts = quiz.firstAttempts()
        assertEquals(mapOf(item.id to false), attempts)

        val grades = DrillReviewFeed.grades(attempts, listOf(word1), today)
        assertEquals(1, grades.size)
        assertEquals(10L, grades[0].word.id)
        assertEquals(ReviewEvent.Result.AGAIN, grades[0].result)
        assertEquals("drill:prepositions", grades[0].source)
    }

    @Test
    fun `table-only items ignored`() {
        val attempts = linkedMapOf(
            DrillItemId(DrillType.PREPOSITIONS, "prep:durch:0") to true,
            DrillItemId(DrillType.PRONOUNS, "pron:ich:1") to false,
        )
        val grades = DrillReviewFeed.grades(attempts, listOf(word1, word2), today)
        assertTrue(grades.isEmpty())
    }

    @Test
    fun `a deleted word ignored`() {
        val attempts = linkedMapOf(
            DrillItemId(DrillType.PREPOSITIONS, "999") to true,
        )
        val grades = DrillReviewFeed.grades(attempts, listOf(word1, word2), today)
        assertTrue(grades.isEmpty())
    }

    @Test
    fun `multiple saved words each get at most one grade`() {
        val attempts = linkedMapOf(
            DrillItemId(DrillType.PREPOSITIONS, "10") to true,
            DrillItemId(DrillType.PREPOSITIONS, "20") to false,
            DrillItemId(DrillType.PREPOSITIONS, "10:retry") to false,
        )
        val grades = DrillReviewFeed.grades(attempts, listOf(word1, word2), today)
        assertEquals(2, grades.size)
        assertEquals(10L, grades[0].word.id)
        assertEquals(ReviewEvent.Result.KNEW, grades[0].result)
        assertEquals(20L, grades[1].word.id)
        assertEquals(ReviewEvent.Result.AGAIN, grades[1].result)
    }

    @Test
    fun `grades go through ReviewSessionLogic gradeAndLog updating Leitner box and events`() {
        val tempDir = Files.createTempDirectory("drill_feed_test").toFile()
        try {
            val eventsStore = ReviewEventsStore(tempDir)
            var savedWord: Word? = null

            val attempts = mapOf(
                DrillItemId(DrillType.PREPOSITIONS, "10") to true
            )
            val grades = DrillReviewFeed.grades(attempts, listOf(word1), today)
            assertEquals(1, grades.size)

            val events = DrillReviewFeed.applyGrades(
                grades = grades,
                saveWord = { savedWord = it },
                events = eventsStore,
                reviewedAt = 12345L,
            )

            // Word 1 was box 2; KNEW advances it to box 3 with interval 4 days
            assertEquals(3, savedWord?.box)
            assertEquals(today + Leitner.INTERVALS[2], savedWord?.dueDay)

            // review_events.json updated with source
            assertEquals(1, events.size)
            assertEquals(10L, events[0].wordId)
            assertEquals(ReviewEvent.Result.KNEW, events[0].result)
            assertEquals(2, events[0].boxBefore)
            assertEquals(3, events[0].boxAfter)
            assertEquals("drill:prepositions", events[0].source)
            assertEquals(12345L, events[0].reviewedAt)

            val stored = eventsStore.all()
            assertEquals(1, stored.size)
            assertEquals(events[0], stored[0])
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
