package app.olauncher.data.store

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConjugationQuizTest {

    private val machen = listOf(
        DrillPrompt("ich", "mache"),
        DrillPrompt("du", "machst"),
        DrillPrompt("er/sie/es", "macht"),
        DrillPrompt("wir", "machen"),
        DrillPrompt("ihr", "macht"),
        DrillPrompt("sie/Sie", "machen"),
    )

    private fun quiz(prompts: List<DrillPrompt> = machen) = ConjugationQuiz(prompts, Random(7))

    private fun wrongChoice(q: ConjugationQuiz): String = q.current!!.choices.first { it != q.current!!.answer }

    @Test
    fun choicesAreFourDistinctFormsIncludingTheAnswer() {
        val q = quiz().current!!
        assertEquals("ich", q.pronoun)
        assertEquals("mache", q.answer)
        assertEquals(4, q.choices.size)
        assertEquals(4, q.choices.toSet().size)
        assertTrue("mache" in q.choices)
        assertTrue(q.choices.all { it in setOf("mache", "machst", "macht", "machen") })
    }

    @Test
    fun duplicateFormsNeverRepeatAsChoices() {
        val prompts = listOf(DrillPrompt("wir", "gehen"), DrillPrompt("sie/Sie", "gehen"), DrillPrompt("ich", "gehe"))
        val q = quiz(prompts).current!!
        assertEquals(listOf("gehe", "gehen"), q.choices.sorted())
    }

    @Test
    fun rightAnswersFirstTryScoreFullMarks() {
        val q = quiz()
        repeat(6) {
            assertTrue(q.answer(q.current!!.answer)!!.correct)
            q.advance()
        }
        assertTrue(q.isRoundFinished)
        assertEquals(6, q.firstTryScore)
        assertTrue(q.missedForms.isEmpty())
        assertFalse(q.startRetry())
    }

    @Test
    fun missedFormComesBackAtTheEndUntilRight() {
        val q = quiz()
        // Miss "ich" (form 0) twice, answer everything else right.
        val first = q.answer(wrongChoice(q))!!
        assertFalse(first.correct)
        assertEquals("mache", first.answer)
        q.advance()
        val asked = mutableListOf<String>()
        while (q.current!!.pronoun != "ich") {
            asked += q.current!!.pronoun
            q.answer(q.current!!.answer)
            q.advance()
        }
        assertEquals(listOf("du", "er/sie/es", "wir", "ihr", "sie/Sie"), asked)
        q.answer(wrongChoice(q))
        q.advance()
        assertEquals("ich", q.current!!.pronoun)
        q.answer("mache")
        q.advance()
        assertTrue(q.isRoundFinished)
        assertEquals(5, q.firstTryScore)
        assertEquals(2, q.missCount(0))
        assertEquals(listOf(0), q.missedForms)
        assertEquals(FormStatus.MISSED, q.status(0))
    }

    @Test
    fun secondPickOnSameQuestionIsIgnored() {
        val q = quiz()
        val wrong = wrongChoice(q)
        q.answer(wrong)
        assertNull(q.answer("mache"))
        assertEquals(1, q.missCount(0))
        assertTrue(q.isAnswered)
    }

    @Test
    fun advanceBeforeAnsweringDoesNothing() {
        val q = quiz()
        q.advance()
        assertEquals("ich", q.current!!.pronoun)
        assertFalse(q.isAnswered)
    }

    @Test
    fun retryAsksOnlyMissedFormsAndKeepsFirstTryScore() {
        val q = quiz()
        // Miss du (1) and ihr (4).
        repeat(6) {
            val pronoun = q.current!!.pronoun
            if (pronoun == "du" || pronoun == "ihr") q.answer(wrongChoice(q)) else q.answer(q.current!!.answer)
            q.advance()
        }
        // Retry queue of du/ihr is now in front; answer them right.
        assertEquals("du", q.current!!.pronoun)
        q.answer("machst")
        q.advance()
        assertEquals("ihr", q.current!!.pronoun)
        q.answer("macht")
        q.advance()
        assertTrue(q.isRoundFinished)
        assertEquals(4, q.firstTryScore)
        assertEquals(listOf(1, 4), q.missedForms)

        assertTrue(q.startRetry())
        assertEquals("du", q.current!!.pronoun)
        q.answer(wrongChoice(q))
        q.advance()
        assertEquals("ihr", q.current!!.pronoun)
        q.answer("macht")
        q.advance()
        assertEquals("du", q.current!!.pronoun)
        q.answer("machst")
        q.advance()
        assertTrue(q.isRoundFinished)
        assertEquals(4, q.firstTryScore)
        assertEquals(2, q.missCount(1))
        assertEquals(1, q.missCount(4))
    }

    @Test
    fun statusTracksPendingRightAndMissed() {
        val q = quiz()
        assertEquals(FormStatus.PENDING, q.status(0))
        q.answer("mache")
        q.advance()
        assertEquals(FormStatus.RIGHT_FIRST_TRY, q.status(0))
        assertEquals(FormStatus.PENDING, q.status(1))
        q.answer(wrongChoice(q))
        assertEquals(FormStatus.MISSED, q.status(1))
        assertEquals(FormStatus.PENDING, q.status(2))
        assertNotNull(q.current)
    }

    @Test
    fun emptyParadigmIsImmediatelyFinished() {
        val q = quiz(emptyList())
        assertTrue(q.isRoundFinished)
        assertEquals(0, q.total)
        assertNull(q.answer("x"))
    }

    @Test
    fun onlyRightAnswerAutoAdvances() {
        val q = quiz()
        val right = q.answer(q.current!!.answer)
        assertTrue(shouldAutoAdvance(right))

        val q2 = quiz()
        val wrong = q2.answer(wrongChoice(q2))
        assertFalse(shouldAutoAdvance(wrong))
        assertFalse(shouldAutoAdvance(null))
        assertFalse(shouldAutoAdvance(q2.answer(q2.current!!.answer)))
    }

    @Test
    fun checkTypedAnswerTrimsWhitespace() {
        assertTrue(checkTypedAnswer("  mache  ", "mache"))
        assertTrue(checkTypedAnswer("\tmache\n", "mache"))
        assertTrue(checkTypedAnswer("mache", "  mache  "))
    }

    @Test
    fun checkTypedAnswerNfcNormalises() {
        val decomposed = "w" + "a\u0308" + "re"
        assertTrue(checkTypedAnswer(decomposed, "wäre"))
        assertTrue(checkTypedAnswer("wäre", decomposed))
    }

    @Test
    fun checkTypedAnswerIsCaseSensitiveExceptFirstLetter() {
        assertTrue(checkTypedAnswer("Mache", "mache"))
        assertTrue(checkTypedAnswer("mache", "Mache"))
        assertTrue(checkTypedAnswer("Äußern", "äußern"))
        assertTrue(checkTypedAnswer("äußern", "Äußern"))

        assertFalse(checkTypedAnswer("mAche", "mache"))
        assertFalse(checkTypedAnswer("MACHEN", "machen"))
        assertFalse(checkTypedAnswer("machsT", "machst"))
        assertFalse(checkTypedAnswer("mAchE", "mache"))
    }

    @Test
    fun checkTypedAnswerRequiresUmlautsWithoutFolding() {
        assertFalse(checkTypedAnswer("waere", "wäre"))
        assertFalse(checkTypedAnswer("koennen", "können"))
        assertFalse(checkTypedAnswer("ueber", "über"))
    }

    @Test
    fun checkTypedAnswerHandlesSeparableVerbsAndEmptyStrings() {
        assertTrue(checkTypedAnswer("fange an", "fange an"))
        assertTrue(checkTypedAnswer("Fange an", "fange an"))
        assertFalse(checkTypedAnswer("fange An", "fange an"))

        assertFalse(checkTypedAnswer("", "mache"))
        assertFalse(checkTypedAnswer("   ", "mache"))
        assertFalse(checkTypedAnswer("mache", ""))
        assertTrue(checkTypedAnswer("", ""))
        assertTrue(checkTypedAnswer("   ", ""))
    }

    @Test
    fun typedQuizChecksAnswersWithNormalizationAndFirstLetterCasing() {
        val q = ConjugationQuiz(machen, Random(7), isTyped = true)
        assertTrue(q.isTyped)
        assertTrue(q.current!!.choices.isEmpty())

        val result = q.answer("  Mache  ")
        assertNotNull(result)
        assertTrue(result!!.correct)
        assertEquals("mache", result.answer)
        assertEquals(result, q.lastAnswer)
        assertTrue(q.isAnswered)
    }

    @Test
    fun typedQuizRetryQueueRepeatsMissedFormsAtTheEndUntilRight() {
        val q = ConjugationQuiz(machen, Random(7), isTyped = true)
        // Miss form 0 ("ich")
        val first = q.answer("machst")!!
        assertFalse(first.correct)
        assertEquals("mache", first.answer)
        q.advance()

        val asked = mutableListOf<String>()
        while (q.current!!.pronoun != "ich") {
            asked += q.current!!.pronoun
            q.answer(q.current!!.answer)
            q.advance()
        }
        assertEquals(listOf("du", "er/sie/es", "wir", "ihr", "sie/Sie"), asked)

        // Miss "ich" again with wrong casing on second letter
        val second = q.answer("mAche")!!
        assertFalse(second.correct)
        q.advance()

        assertEquals("ich", q.current!!.pronoun)
        val third = q.answer("Mache")!!
        assertTrue(third.correct)
        q.advance()

        assertTrue(q.isRoundFinished)
        assertEquals(5, q.firstTryScore)
        assertEquals(2, q.missCount(0))
        assertEquals(listOf(0), q.missedForms)
        assertEquals(FormStatus.MISSED, q.status(0))
    }

    @Test
    fun typedQuizStartRetryRequeuesOnlyMissedForms() {
        val q = ConjugationQuiz(machen, Random(7), isTyped = true)
        // Miss du (1) and ihr (4).
        repeat(6) {
            val pronoun = q.current!!.pronoun
            if (pronoun == "du" || pronoun == "ihr") q.answer("wrong") else q.answer(q.current!!.answer)
            q.advance()
        }
        assertEquals("du", q.current!!.pronoun)
        q.answer("machst")
        q.advance()
        assertEquals("ihr", q.current!!.pronoun)
        q.answer("macht")
        q.advance()
        assertTrue(q.isRoundFinished)
        assertEquals(4, q.firstTryScore)
        assertEquals(listOf(1, 4), q.missedForms)

        assertTrue(q.startRetry())
        assertEquals("du", q.current!!.pronoun)
        q.answer("machst")
        q.advance()
        assertEquals("ihr", q.current!!.pronoun)
        q.answer("macht")
        q.advance()
        assertTrue(q.isRoundFinished)
        assertEquals(4, q.firstTryScore)
    }

    @Test
    fun answerTypedWorksDirectlyInAnyMode() {
        val q = quiz()
        assertFalse(q.isTyped)
        val ans = q.answerTyped("Mache")
        assertNotNull(ans)
        assertTrue(ans!!.correct)
    }

    @Test
    fun isNearMissIdentifiesOneLetterAddedMissingOrWrong() {
        // One letter added
        assertTrue(isNearMiss("macher", "mache"))
        assertTrue(isNearMiss("smache", "mache"))
        assertTrue(isNearMiss("fange ann", "fange an"))

        // One letter missing
        assertTrue(isNearMiss("mach", "mache"))
        assertTrue(isNearMiss("ache", "mache"))
        assertTrue(isNearMiss("fange a", "fange an"))

        // One letter wrong (substitution)
        assertTrue(isNearMiss("macht", "mache"))
        assertTrue(isNearMiss("sache", "mache"))
        assertTrue(isNearMiss("fange am", "fange an"))

        // First letter case is insensitive
        assertTrue(isNearMiss("Macht", "mache"))
        assertTrue(isNearMiss("Macher", "mache"))
        assertTrue(isNearMiss("macht", "Mache"))
    }

    @Test
    fun isNearMissIdentifiesUmlautEquivalents() {
        // ä <-> ae
        assertTrue(isNearMiss("waere", "wäre"))
        assertTrue(isNearMiss("Waere", "wäre"))
        assertTrue(isNearMiss("wäre", "waere"))

        // ö <-> oe
        assertTrue(isNearMiss("koennen", "können"))
        assertTrue(isNearMiss("Koennen", "können"))
        assertTrue(isNearMiss("können", "koennen"))

        // ü <-> ue
        assertTrue(isNearMiss("ueber", "über"))
        assertTrue(isNearMiss("Ueber", "über"))
        assertTrue(isNearMiss("über", "ueber"))

        // ß <-> ss
        assertTrue(isNearMiss("gross", "groß"))
        assertTrue(isNearMiss("Gross", "groß"))
        assertTrue(isNearMiss("groß", "gross"))

        // Base vowel vs umlaut is 1 letter wrong (covered by edit distance 1)
        assertTrue(isNearMiss("ware", "wäre"))
        assertTrue(isNearMiss("konnen", "können"))
        assertTrue(isNearMiss("uber", "über"))
    }

    @Test
    fun isNearMissRejectsExactMatches() {
        assertFalse(isNearMiss("mache", "mache"))
        assertFalse(isNearMiss("Mache", "mache"))
        assertFalse(isNearMiss("wäre", "wäre"))
        assertFalse(isNearMiss("Wäre", "wäre"))
        assertFalse(isNearMiss("groß", "groß"))
    }

    @Test
    fun isNearMissRejectsMultipleDifferences() {
        // Umlaut equivalent AND extra letter
        assertFalse(isNearMiss("waeren", "wäre"))

        // 2 or more letters wrong
        assertFalse(isNearMiss("machst", "mache"))
        assertFalse(isNearMiss("machen", "machst"))
        assertFalse(isNearMiss("xyz", "mache"))
        assertFalse(isNearMiss("completely wrong", "mache"))
    }

    @Test
    fun isNearMissRejectsEmptyAndWhitespaceStrings() {
        assertFalse(isNearMiss("", "mache"))
        assertFalse(isNearMiss("   ", "mache"))
        assertFalse(isNearMiss("mache", ""))
        assertFalse(isNearMiss("", ""))
    }

    @Test
    fun isNearMissHandlesSlashAlternativesAndTrimming() {
        assertTrue(isNearMiss("  macht  ", "mache"))
        assertTrue(isNearMiss("habe gefahre", "habe gefahren / bin gefahren"))
        assertTrue(isNearMiss("bin gefahre", "habe gefahren / bin gefahren"))
        assertFalse(isNearMiss("habe gefahren", "habe gefahren / bin gefahren"))
    }
}
