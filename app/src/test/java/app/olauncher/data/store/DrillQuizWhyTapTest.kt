package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Scoring of the "Why?" tap (#651 design §2): a tap before an answer is recorded as a
 * weaker right answer. The answer still counts as a right first attempt (the raw
 * counts and the summary's first-attempt colour keep their meaning), but the round
 * result becomes ALMOST — sand, not sage — and only half of it feeds the decayed
 * score evidence.
 */
class DrillQuizWhyTapTest {

    private fun item(key: String): DrillItem = DrillItem(
        id = DrillItemId(DrillType.PASSIVE, key),
        prompt = "Das Auto ___ gerade repariert.",
        answer = "wird",
        choices = listOf("wird", "werde", "wurde", "wurden"),
        ruleId = "passiv-aux-form",
    )

    private fun quizWith(vararg keys: String): DrillQuiz =
        DrillQuiz(keys.map { key -> DrillQuizQuestion(item(key), DrillFormat.TAP, item(key).choices) })

    @Test
    fun whyTapBeforeARightAnswerMakesItWeaker() {
        val quiz = quizWith("1", "2")
        quiz.recordWhyTap()
        quiz.answer("wird")
        assertEquals(DrillResult.ALMOST, quiz.itemResults()[item("1").id])
        // Still a right first attempt: the raw counts keep their meaning.
        assertEquals(true, quiz.firstAttempts()[item("1").id])
        assertNull(quiz.learnerAnswers()[item("1").id])
    }

    @Test
    fun whyTapAfterTheAnswerChangesNothing() {
        val quiz = quizWith("1")
        quiz.answer("wird")
        quiz.recordWhyTap()
        assertEquals(DrillResult.RIGHT, quiz.itemResults()[item("1").id])
        assertFalse(quiz.whyTappedIds().contains(item("1").id))
    }

    @Test
    fun whyTapOnATeachingStepIsIgnored() {
        val flashcard = DrillQuizQuestion(item("1"), DrillFormat.FLASHCARD)
        val quiz = DrillQuiz(listOf(flashcard, DrillQuizQuestion(item("2"), DrillFormat.TAP, item("2").choices)))
        quiz.recordWhyTap() // flashcard: never quizzed, never counted
        assertTrue(quiz.whyTappedIds().isEmpty())
        quiz.advance()
        quiz.recordWhyTap()
        assertEquals(setOf(item("2").id), quiz.whyTappedIds())
    }

    @Test
    fun wrongAnswerAfterWhyTapIsSimplyWrong() {
        val quiz = quizWith("1")
        quiz.recordWhyTap()
        quiz.answer("wurde")
        assertEquals(DrillResult.WRONG, quiz.itemResults()[item("1").id])
    }

    @Test
    fun whyRightTypedAnswerIsWeaker() {
        val question = DrillQuizQuestion(item("1"), DrillFormat.TYPED)
        val quiz = DrillQuiz(listOf(question))
        quiz.recordWhyTap()
        quiz.answerTyped("wird")
        assertEquals(DrillResult.ALMOST, quiz.itemResults()[item("1").id])
        assertEquals(DrillResult.ALMOST, quiz.typedResults()[item("1").id])
        assertEquals(true, quiz.firstAttempts()[item("1").id])
    }

    @Test
    fun whyRightHalvesTheChoiceEvidence() {
        val quiz = quizWith("1", "2")
        quiz.recordWhyTap()
        quiz.answer("wird") // right, after Why
        quiz.advance()
        quiz.answer("wurde") // wrong
        val evidence = DrillScores.roundEvidenceByType(
            quiz.firstAttempts(),
            quiz.typedResults(),
            abandoned = false,
            whyTapped = quiz.whyTappedIds(),
        )[DrillType.PASSIVE]!!
        assertEquals(2, evidence.asked)
        assertEquals(1, evidence.firstTryRight)
        assertEquals(2.0, evidence.evidence.askedDelta, 1e-9)
        // One full right would be 1.0; the why-right contributes half.
        assertEquals(0.5, evidence.evidence.rightDelta, 1e-9)
        // The raw right count still credits it.
        assertEquals(1, evidence.rightAnswers)
    }

    @Test
    fun whyRightTypedAnswerHalvesTheTypedEvidence() {
        val question = DrillQuizQuestion(item("1"), DrillFormat.TYPED)
        val quiz = DrillQuiz(listOf(question))
        quiz.recordWhyTap()
        quiz.answerTyped("wird")
        val evidence = DrillScores.roundEvidenceByType(
            quiz.firstAttempts(),
            quiz.typedResults(),
            abandoned = false,
            whyTapped = quiz.whyTappedIds(),
        )[DrillType.PASSIVE]!!
        // Typed answers carry the typed weight (2/3) for a type with a typed round.
        assertEquals(2.0 / 3.0, evidence.evidence.askedDelta, 1e-9)
        assertEquals(DrillScores.WHY_WEIGHT * 2.0 / 3.0, evidence.evidence.rightDelta, 1e-9)
    }

    @Test
    fun withoutWhyTapTheEvidenceIsUnchanged() {
        val quiz = quizWith("1")
        quiz.answer("wird")
        val evidence = DrillScores.roundEvidenceByType(
            quiz.firstAttempts(),
            quiz.typedResults(),
            abandoned = false,
            whyTapped = quiz.whyTappedIds(),
        )[DrillType.PASSIVE]!!
        assertEquals(1.0, evidence.evidence.rightDelta, 1e-9)
        assertEquals(DrillResult.RIGHT, quiz.itemResults()[item("1").id])
    }
}
