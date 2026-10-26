package app.olauncher.data.store

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillQuizTest {

    private val item1 = DrillItem(
        id = DrillItemId(DrillType.PREPOSITIONS, "mit"),
        prompt = "Ich fahre ___ dem Zug.",
        answer = "mit",
        choices = listOf("mit", "bei", "zu", "nach"),
        hint = "Dativ",
    )

    private val item2 = DrillItem(
        id = DrillItemId(DrillType.ARTICLES, "buch"),
        prompt = "___ Buch ist alt.",
        answer = "Das",
        choices = listOf("Der", "Die", "Das"),
    )

    private val item3 = DrillItem(
        id = DrillItemId(DrillType.PRONOUNS, "wir"),
        prompt = "___ gehen nach Hause.",
        answer = "Wir",
        choices = listOf("Ich", "Wir", "Ihr"),
    )

    private fun tap(item: DrillItem): DrillQuizQuestion =
        DrillQuizQuestion(item, DrillFormat.TAP, item.choices)

    private fun typed(item: DrillItem): DrillQuizQuestion =
        DrillQuizQuestion(item, DrillFormat.TYPED)

    private fun flashcard(item: DrillItem): DrillQuizQuestion =
        DrillQuizQuestion(item, DrillFormat.FLASHCARD)

    private fun trueFalse(item: DrillItem, isTrue: Boolean): DrillQuizQuestion =
        DrillQuizQuestion(
            item = item,
            format = DrillFormat.TRUE_FALSE,
            choices = listOf(DrillQuizQuestion.TRUE_CHOICE, DrillQuizQuestion.FALSE_CHOICE),
            statement = item.prompt.replaceFirst(DrillItem.GAP_MARKER, item.answer),
            isTrue = isTrue,
        )

    private fun wrongChoice(item: DrillItem): String =
        item.choices.first { it != item.answer }

    @Test
    fun tapQuestionInitializesWithChoicesAndItem() {
        val quiz = DrillQuiz(listOf(tap(item1), tap(item2)), Random(42))
        assertEquals(2, quiz.total)
        assertFalse(quiz.isFinished)

        val q = quiz.current
        assertNotNull(q)
        assertEquals(item1.id, q!!.id)
        assertEquals(item1.prompt, q.prompt)
        assertEquals(item1.answer, q.answer)
        assertEquals(item1.hint, q.hint)
        assertEquals(DrillFormat.TAP, q.format)
        assertEquals(item1.choices.toSet(), q.choices.toSet())
    }

    @Test
    fun rightAnswerFirstTryRecordsTrueInFirstAttempts() {
        val quiz = DrillQuiz(listOf(tap(item1), tap(item2)))
        val ans = quiz.answer("mit")
        assertNotNull(ans)
        assertTrue(ans!!.correct)
        assertEquals(mapOf(item1.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)
    }

    @Test
    fun nothingIsRequeuedAfterAWrongAnswer() {
        val quiz = DrillQuiz(listOf(tap(item1), tap(item2), tap(item3)))

        quiz.answer(wrongChoice(item1))
        assertEquals(mapOf(item1.id to false), quiz.firstAttempts())
        quiz.advance()

        // The wrong item is not queued again: the next planned step follows.
        assertEquals(item2.id, quiz.current!!.id)
        quiz.answer(item2.answer)
        quiz.advance()
        assertEquals(item3.id, quiz.current!!.id)
        quiz.answer(item3.answer)
        quiz.advance()

        assertTrue(quiz.isFinished)
        assertEquals(
            mapOf(item1.id to DrillResult.WRONG, item2.id to DrillResult.RIGHT, item3.id to DrillResult.RIGHT),
            quiz.itemResults(),
        )
    }

    @Test
    fun answerAlternateFormIsRight() {
        val multi = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "multi"),
            prompt = "___",
            answer = "mich / mir",
            choices = listOf("mich", "mir", "dir"),
        )
        val quiz = DrillQuiz(listOf(tap(multi)))
        assertTrue(quiz.answer("mir")!!.correct)
    }

    @Test
    fun flashcardIsAnsweredWithoutGradingAndNotScored() {
        val quiz = DrillQuiz(listOf(flashcard(item1), tap(item2)))

        assertTrue(quiz.isAnswered)
        assertNull(quiz.lastAnswer)
        assertEquals(emptyMap<DrillItemId, Boolean>(), quiz.firstAttempts())

        quiz.advance()
        assertEquals(item2.id, quiz.current!!.id)
        assertEquals(listOf(item2), quiz.items)
    }

    @Test
    fun trueFalseGradesTheTruthValueNotTheItemAnswer() {
        val quiz = DrillQuiz(listOf(trueFalse(item1, isTrue = false), trueFalse(item2, isTrue = true)))

        val wrong = quiz.answer(DrillQuizQuestion.TRUE_CHOICE)
        assertNotNull(wrong)
        assertFalse(wrong!!.correct)
        assertEquals(
            DrillQuizQuestion.TRUE_CHOICE,
            quiz.learnerAnswers()[item1.id],
        )
        quiz.advance()

        val right = quiz.answer(DrillQuizQuestion.TRUE_CHOICE)
        assertNotNull(right)
        assertTrue(right!!.correct)
        assertEquals(mapOf(item1.id to DrillResult.WRONG, item2.id to DrillResult.RIGHT), quiz.itemResults())
    }

    @Test
    fun typedNearMissGetsOneRetryWithoutMarkingTheItemWrong() {
        val quiz = DrillQuiz(listOf(typed(item1)))

        val nearMiss = quiz.answerTyped("mitx")
        assertNotNull(nearMiss)
        assertFalse(nearMiss!!.correct)
        assertTrue(nearMiss.isNearMiss)
        assertTrue(quiz.isAwaitingRetry)
        // Not marked wrong, no reveal: no result recorded yet.
        assertEquals(emptyMap<DrillItemId, DrillResult>(), quiz.itemResults())

        val retry = quiz.answerTyped("mit")
        assertNotNull(retry)
        assertTrue(retry!!.correct)
        assertTrue(retry.isAlmost)
        assertEquals(DrillResult.ALMOST, quiz.itemResults()[item1.id])
        assertEquals(DrillResult.ALMOST, quiz.typedResults()[item1.id])
        // A near miss that lands exact is not a first-try right.
        assertEquals(mapOf(item1.id to false), quiz.firstAttempts())
    }

    @Test
    fun typedSecondMissIsWrong() {
        val quiz = DrillQuiz(listOf(typed(item1)))

        assertNotNull(quiz.answerTyped("mitx"))
        val second = quiz.answerTyped("mitxx")
        assertNotNull(second)
        assertFalse(second!!.correct)
        assertFalse(second.isNearMiss)
        assertEquals(DrillResult.WRONG, quiz.itemResults()[item1.id])
        assertEquals("mitxx", quiz.learnerAnswers()[item1.id])
    }

    @Test
    fun typedExactFirstTryIsRight() {
        val quiz = DrillQuiz(listOf(typed(item1)))
        val ans = quiz.answerTyped("mit")
        assertTrue(ans!!.correct)
        assertFalse(ans.isAlmost)
        assertEquals(DrillResult.RIGHT, quiz.itemResults()[item1.id])
        assertEquals(1, quiz.firstTryScore)
    }

    @Test
    fun choicesAreShuffledButKeepTheirSet() {
        val quiz = DrillQuiz(listOf(tap(item1)), Random(7))
        assertEquals(item1.choices.toSet(), quiz.current!!.choices.toSet())
    }

    @Test
    fun currentPositionTracksTheStep() {
        val quiz = DrillQuiz(listOf(tap(item1), flashcard(item2), tap(item3)))
        assertEquals(0, quiz.currentPosition)
        quiz.answer(item1.answer)
        quiz.advance()
        assertEquals(1, quiz.currentPosition)
        quiz.advance()
        assertEquals(2, quiz.currentPosition)
    }

    @Test
    fun emptyQuizIsFinished() {
        val quiz = DrillQuiz(emptyList())
        assertTrue(quiz.isFinished)
        assertEquals(0, quiz.total)
        assertNull(quiz.current)
    }

    @Test
    fun answeringTwiceIsIgnored() {
        val quiz = DrillQuiz(listOf(tap(item1)))
        assertNotNull(quiz.answer(wrongChoice(item1)))
        assertNull(quiz.answer(item1.answer))
    }

    @Test
    fun answeringAFlashcardIsIgnored() {
        val quiz = DrillQuiz(listOf(flashcard(item1)))
        assertNull(quiz.answer(item1.answer))
        assertNull(quiz.answerTyped("anything"))
    }

    @Test
    fun advanceWithoutAnswerDoesNothing() {
        val quiz = DrillQuiz(listOf(tap(item1), tap(item2)))
        quiz.advance()
        assertEquals(item1.id, quiz.current!!.id)
    }
}
