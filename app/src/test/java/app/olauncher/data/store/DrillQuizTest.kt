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

    private fun wrongChoice(item: DrillItem): String =
        item.choices.first { it != item.answer }

    @Test
    fun choiceRoundInitializesWithChoicesAndCorrectQuestion() {
        val quiz = DrillQuiz(listOf(item1, item2), Random(42))
        assertEquals(2, quiz.total)
        assertEquals(DrillRound.CHOICE, quiz.round)
        assertFalse(quiz.isTyped)
        assertFalse(quiz.isFinished)

        val q = quiz.current
        assertNotNull(q)
        assertEquals(item1.id, q!!.id)
        assertEquals(item1.prompt, q.prompt)
        assertEquals(item1.answer, q.answer)
        assertEquals(item1.hint, q.hint)
        assertEquals(DrillRound.CHOICE, q.round)
        assertEquals(item1.choices.toSet(), q.choices.toSet())
    }

    @Test
    fun rightAnswerFirstTryRecordsTrueInFirstAttempts() {
        val quiz = DrillQuiz(listOf(item1, item2))
        val ans = quiz.answer("mit")
        assertNotNull(ans)
        assertTrue(ans!!.correct)
        assertEquals(mapOf(item1.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)
    }

    @Test
    fun wrongAnswerInChoiceRoundRequeuesItemAtTheEnd() {
        val quiz = DrillQuiz(listOf(item1, item2, item3))
        // Answer item1 wrong
        val wrongAns = quiz.answer(wrongChoice(item1))
        assertNotNull(wrongAns)
        assertFalse(wrongAns!!.correct)
        quiz.advance()

        // Next should be item2
        assertEquals(item2.id, quiz.current!!.id)
        val ans2 = quiz.answer(item2.answer)
        assertTrue(ans2!!.correct)
        quiz.advance()

        // Next should be item3
        assertEquals(item3.id, quiz.current!!.id)
        val ans3 = quiz.answer(item3.answer)
        assertTrue(ans3!!.correct)
        quiz.advance()

        // Now item1 should have come back to the end of the choice round
        assertEquals(item1.id, quiz.current!!.id)
        assertEquals(DrillRound.CHOICE, quiz.round)
        val ans1Retry = quiz.answer(item1.answer)
        assertTrue(ans1Retry!!.correct)
        quiz.advance()

        // Choice round done, should have switched to typed round
        assertEquals(DrillRound.TYPED, quiz.round)
    }

    @Test
    fun retriesAndTypedRoundNeverChangeFirstAttempts() {
        val quiz = DrillQuiz(listOf(item1, item2))

        // item1 answered wrong on first attempt
        quiz.answer(wrongChoice(item1))
        quiz.advance()
        assertEquals(mapOf(item1.id to false), quiz.firstAttempts())
        assertEquals(0, quiz.firstTryScore)

        // item2 answered right on first attempt
        quiz.answer(item2.answer)
        quiz.advance()
        assertEquals(mapOf(item1.id to false, item2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)

        // item1 retried in choice round and answered right: firstAttempts must stay false!
        assertEquals(item1.id, quiz.current!!.id)
        quiz.answer(item1.answer)
        quiz.advance()
        assertEquals(mapOf(item1.id to false, item2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)

        // Switched to typed round: item1 asked again
        assertEquals(DrillRound.TYPED, quiz.round)
        assertEquals(item1.id, quiz.current!!.id)
        // In typed round, answered correctly; firstAttempts must stay false!
        quiz.answerTyped(item1.answer)
        quiz.advance()
        assertEquals(mapOf(item1.id to false, item2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)

        // item2 asked in typed round; answer incorrectly; firstAttempts must stay true!
        assertEquals(item2.id, quiz.current!!.id)
        quiz.answerTyped("wrong")
        quiz.advance()
        assertEquals(mapOf(item1.id to false, item2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)

        // item2 requeued in typed round; answered right
        assertEquals(item2.id, quiz.current!!.id)
        quiz.answerTyped(item2.answer)
        quiz.advance()
        assertEquals(mapOf(item1.id to false, item2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)
    }

    @Test
    fun switchToTheTypedRoundAfterChoiceRoundFinishes() {
        val quiz = DrillQuiz(listOf(item1, item2))
        assertEquals(DrillRound.CHOICE, quiz.round)

        quiz.answer(item1.answer)
        quiz.advance()
        quiz.answer(item2.answer)
        quiz.advance()

        // Automatically switches to typed round
        assertEquals(DrillRound.TYPED, quiz.round)
        assertTrue(quiz.isTyped)
        assertFalse(quiz.isFinished)
        assertEquals(item1.id, quiz.current!!.id)
        assertTrue(quiz.current!!.choices.isEmpty())
    }

    @Test
    fun typedRoundRequeuesWrongAnswersAtEnd() {
        val quiz = DrillQuiz(listOf(item1, item2))
        // Pass choice round
        quiz.answer(item1.answer)
        quiz.advance()
        quiz.answer(item2.answer)
        quiz.advance()

        assertEquals(DrillRound.TYPED, quiz.round)
        // In typed round, fail item1
        quiz.answerTyped("falsch")
        quiz.advance()

        // Next is item2
        assertEquals(item2.id, quiz.current!!.id)
        quiz.answerTyped(item2.answer)
        quiz.advance()

        // item1 comes back
        assertEquals(item1.id, quiz.current!!.id)
        quiz.answerTyped(item1.answer)
        quiz.advance()

        assertTrue(quiz.isFinished)
    }

    @Test
    fun roundEndCompletesWhenTypedRoundFinishes() {
        val quiz = DrillQuiz(listOf(item1))
        assertFalse(quiz.isFinished)

        // Choice round
        quiz.answer(item1.answer)
        quiz.advance()

        // Typed round
        assertEquals(DrillRound.TYPED, quiz.round)
        assertFalse(quiz.isFinished)
        quiz.answerTyped(item1.answer)
        quiz.advance()

        // Quiz complete
        assertTrue(quiz.isFinished)
        assertTrue(quiz.isRoundFinished)
        assertNull(quiz.current)
        assertNull(quiz.currentItem)
        assertEquals(1, quiz.firstTryScore)
    }

    @Test
    fun typedAnswersUseCheckTypedAnswerRules() {
        val quiz = DrillQuiz(listOf(item1))
        // Finish choice round
        quiz.answer(item1.answer)
        quiz.advance()

        // Typed round tests checkTypedAnswer
        // Leading/trailing whitespace trimmed
        val whitespaceAnswer = quiz.answerTyped("   Mit   ")
        assertNotNull(whitespaceAnswer)
        assertTrue(whitespaceAnswer!!.correct)
    }

    @Test
    fun typedQuizChecksUmlautsAndCasing() {
        val umlautItem = DrillItem(
            id = DrillItemId(DrillType.CLOZE, "umlaut"),
            prompt = "Ich ___ gerne.",
            answer = "wäre",
            choices = listOf("wäre", "bin"),
        )
        val quiz = DrillQuiz(listOf(umlautItem))
        quiz.answer(umlautItem.answer)
        quiz.advance()

        // In typed round: "waere" must fail (no ae folding) and offer near-miss retry
        val folded = quiz.answerTyped("waere")
        assertNotNull(folded)
        assertFalse(folded!!.correct)
        assertTrue(folded.isNearMiss)
        assertTrue(quiz.isAwaitingRetry)
        assertFalse(quiz.isAnswered)

        // Capitalized first letter accepted on retry: "Wäre"
        val cap = quiz.answerTyped("Wäre")
        assertNotNull(cap)
        assertTrue(cap!!.correct)
        assertTrue(cap.isAlmost)
        assertFalse(cap.isNearMiss)
        assertTrue(quiz.isAnswered)
        quiz.advance()

        assertTrue(quiz.isFinished)
    }

    @Test
    fun emptyItemListIsImmediatelyFinished() {
        val quiz = DrillQuiz(emptyList())
        assertTrue(quiz.isFinished)
        assertTrue(quiz.isRoundFinished)
        assertEquals(0, quiz.total)
        assertNull(quiz.current)
        assertNull(quiz.answer("test"))
        assertNull(quiz.answerTyped("test"))
        assertEquals(0, quiz.firstTryScore)
        assertTrue(quiz.firstAttempts().isEmpty())
    }

    @Test
    fun secondAnswerOnSameQuestionIsIgnored() {
        val quiz = DrillQuiz(listOf(item1))
        val first = quiz.answer(wrongChoice(item1))
        assertNotNull(first)
        assertFalse(first!!.correct)

        val second = quiz.answer(item1.answer)
        assertNull(second)
        assertEquals(first, quiz.lastAnswer)
        assertTrue(quiz.isAnswered)
    }

    @Test
    fun advanceWithoutAnsweringDoesNothing() {
        val quiz = DrillQuiz(listOf(item1, item2))
        quiz.advance()
        assertEquals(item1.id, quiz.current!!.id)
        assertFalse(quiz.isAnswered)
    }

    @Test
    fun answerMethodDelegatesToAnswerTypedInTypedRound() {
        val quiz = DrillQuiz(listOf(item1))
        quiz.answer(item1.answer)
        quiz.advance()

        assertEquals(DrillRound.TYPED, quiz.round)
        val result = quiz.answer("mit")
        assertNotNull(result)
        assertTrue(result!!.correct)
        quiz.advance()
        assertTrue(quiz.isFinished)
    }

    @Test
    fun choiceOnlyQuizFinishesAfterChoiceRoundWithoutTypedRound() {
        val caseItem1 = DrillItem(
            id = DrillItemId(DrillType.CASE_ENDINGS, "1"),
            prompt = "Ich helfe ___ Mann.",
            answer = "dem",
            choices = listOf("dem", "den", "der", "das"),
        )
        val caseItem2 = DrillItem(
            id = DrillItemId(DrillType.CASE_ENDINGS, "2"),
            prompt = "Sie sieht ___ Hund.",
            answer = "den",
            choices = listOf("dem", "den", "der", "das"),
        )
        val quiz = DrillQuiz(listOf(caseItem1, caseItem2))
        assertTrue(quiz.isChoiceOnly)
        assertFalse(quiz.hasTypedRound)
        assertEquals(DrillRound.CHOICE, quiz.round)

        quiz.answer(caseItem1.answer)
        quiz.advance()
        assertFalse(quiz.isFinished)
        assertEquals(DrillRound.CHOICE, quiz.round)

        quiz.answer(caseItem2.answer)
        quiz.advance()

        // Choice-only quiz finishes immediately without switching to typed round
        assertTrue(quiz.isFinished)
        assertTrue(quiz.isRoundFinished)
        assertNull(quiz.current)
        assertEquals(DrillRound.CHOICE, quiz.round)
        assertEquals(2, quiz.firstTryScore)
    }

    @Test
    fun choiceOnlyQuizRequeuesMissesAndFinishesWhenAllCleared() {
        val caseItem1 = DrillItem(
            id = DrillItemId(DrillType.CASE_ENDINGS, "1"),
            prompt = "Ich helfe ___ Mann.",
            answer = "dem",
            choices = listOf("dem", "den", "der", "das"),
        )
        val caseItem2 = DrillItem(
            id = DrillItemId(DrillType.CASE_ENDINGS, "2"),
            prompt = "Sie sieht ___ Hund.",
            answer = "den",
            choices = listOf("dem", "den", "der", "das"),
        )
        val quiz = DrillQuiz(listOf(caseItem1, caseItem2), isChoiceOnly = true)

        // Fail caseItem1 on first try
        val wrongAns = quiz.answer("den")
        assertNotNull(wrongAns)
        assertFalse(wrongAns!!.correct)
        quiz.advance()

        // Next is caseItem2; answer correctly
        assertEquals(caseItem2.id, quiz.current!!.id)
        val correctAns2 = quiz.answer(caseItem2.answer)
        assertTrue(correctAns2!!.correct)
        quiz.advance()

        // caseItem1 comes back!
        assertFalse(quiz.isFinished)
        assertEquals(caseItem1.id, quiz.current!!.id)
        val retryAns = quiz.answer(caseItem1.answer)
        assertTrue(retryAns!!.correct)
        quiz.advance()

        // Once cleared, finishes without entering typed round
        assertTrue(quiz.isFinished)
        assertEquals(mapOf(caseItem1.id to false, caseItem2.id to true), quiz.firstAttempts())
        assertEquals(1, quiz.firstTryScore)
    }

    @Test
    fun roundOverTenItemsCarriesTheExactTenItemsIntoTypedRound() {
        val tenItems = (1..10).map { idx ->
            DrillItem(
                id = DrillItemId(DrillType.ARTICLES, "item$idx"),
                prompt = "Prompt $idx",
                answer = "Das",
                choices = listOf("Der", "Die", "Das"),
            )
        }
        val quiz = DrillQuiz(tenItems)
        assertFalse(quiz.isChoiceOnly)
        assertTrue(quiz.hasTypedRound)
        assertEquals(10, quiz.total)

        // Answer all 10 in choice round
        for (item in tenItems) {
            assertEquals(item.id, quiz.current!!.id)
            quiz.answer(item.answer)
            quiz.advance()
        }

        // Switched to typed round
        assertEquals(DrillRound.TYPED, quiz.round)
        assertFalse(quiz.isFinished)

        // Typed round presents the EXACT same 10 items
        for (item in tenItems) {
            assertEquals(item.id, quiz.current!!.id)
            quiz.answerTyped(item.answer)
            quiz.advance()
        }

        // Completed
        assertTrue(quiz.isFinished)
        assertEquals(10, quiz.firstTryScore)
    }

    @Test
    fun nearMissOneLetterOffShowsRetryAndAnswerNotRevealed() {
        val quiz = DrillQuiz(listOf(item1), startRound = DrillRound.TYPED)
        // item1 answer is "mit". User types "mi" (one letter missing)
        val result = quiz.answerTyped("mi")
        assertNotNull(result)
        assertFalse(result!!.correct)
        assertTrue(result.isNearMiss)
        assertFalse(result.isAlmost)

        // Item is not marked answered, answer is not revealed, awaiting retry
        assertTrue(quiz.isAwaitingRetry)
        assertFalse(quiz.isAnswered)
        assertNull(quiz.lastAnswer)
    }

    @Test
    fun nearMissUmlautEquivalentShowsRetryAndAnswerNotRevealed() {
        val umlautItem = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "ueber"),
            prompt = "Das Bild hängt ___ dem Sofa.",
            answer = "über",
            choices = listOf("über", "unter"),
        )
        val quiz = DrillQuiz(listOf(umlautItem), startRound = DrillRound.TYPED)
        // User types "ueber" for "über" (umlaut equivalent)
        val result = quiz.answerTyped("ueber")
        assertNotNull(result)
        assertFalse(result!!.correct)
        assertTrue(result.isNearMiss)
        assertFalse(result.isAlmost)

        assertTrue(quiz.isAwaitingRetry)
        assertFalse(quiz.isAnswered)
        assertNull(quiz.lastAnswer)
    }

    @Test
    fun exactMatchStaysOnlyWayToBeRight() {
        val quiz = DrillQuiz(listOf(item1), startRound = DrillRound.TYPED)
        // One letter wrong is NOT marked right
        val near = quiz.answerTyped("mot")
        assertNotNull(near)
        assertFalse(near!!.correct)

        // Exact match on retry is right
        val exact = quiz.answerTyped("mit")
        assertNotNull(exact)
        assertTrue(exact!!.correct)
    }

    @Test
    fun retryThatIsExactCountsAsAlmost() {
        val quiz = DrillQuiz(listOf(item1), startRound = DrillRound.TYPED)
        // First try: near miss
        val near = quiz.answerTyped("mi")
        assertTrue(near!!.isNearMiss)
        assertTrue(quiz.isAwaitingRetry)

        // Retry: exact match
        val retry = quiz.answerTyped("mit")
        assertNotNull(retry)
        assertTrue(retry!!.correct)
        assertTrue(retry.isAlmost)
        assertFalse(retry.isNearMiss)
        assertTrue(quiz.isAnswered)

        // Counts as almost, not as firstTryRight
        assertEquals(DrillResult.ALMOST, quiz.itemResults()[item1.id])
        assertEquals(1, quiz.almostScore)
        assertEquals(0, quiz.firstTryScore)
    }

    @Test
    fun secondMissAfterNearMissCountsAsMiss() {
        val quiz = DrillQuiz(listOf(item1), startRound = DrillRound.TYPED)
        // First try: near miss
        val near = quiz.answerTyped("mi")
        assertTrue(near!!.isNearMiss)
        assertTrue(quiz.isAwaitingRetry)

        // Second try: miss again (even if another near miss)
        val second = quiz.answerTyped("mis")
        assertNotNull(second)
        assertFalse(second!!.correct)
        assertFalse(second.isNearMiss)
        assertFalse(second.isAlmost)

        // Now marked wrong, answer is revealed in quizAnswer
        assertTrue(quiz.isAnswered)
        assertFalse(quiz.isAwaitingRetry)
        assertEquals(item1.answer, second.answer)
        assertEquals(DrillResult.WRONG, quiz.itemResults()[item1.id])
        assertEquals(0, quiz.firstTryScore)
        assertEquals(0, quiz.almostScore)

        // Advancing requeues item1
        quiz.advance()
        assertFalse(quiz.isFinished)
        assertEquals(item1.id, quiz.current!!.id)
    }

    @Test
    fun farMissCountsAsMissImmediatelyWithoutRetry() {
        val quiz = DrillQuiz(listOf(item1), startRound = DrillRound.TYPED)
        // Completely wrong answer (not a near miss)
        val far = quiz.answerTyped("completely wrong")
        assertNotNull(far)
        assertFalse(far!!.correct)
        assertFalse(far.isNearMiss)
        assertFalse(far.isAlmost)

        // Immediately marked wrong
        assertTrue(quiz.isAnswered)
        assertFalse(quiz.isAwaitingRetry)
        assertEquals(DrillResult.WRONG, quiz.itemResults()[item1.id])
        assertEquals(0, quiz.firstTryScore)
        assertEquals(0, quiz.almostScore)
    }

    @Test
    fun missInTypedRoundStaysWrongWhenLaterRetryIsRight() {
        val quiz = DrillQuiz(listOf(item1), startRound = DrillRound.TYPED)
        // Far miss marks the item wrong and re-queues it
        quiz.answerTyped("completely wrong")
        quiz.advance()

        // Later correct answer on the re-queued pass does not erase the miss
        val retry = quiz.answerTyped("mit")
        assertTrue(retry!!.correct)
        assertEquals(DrillResult.WRONG, quiz.itemResults()[item1.id])
        assertEquals(0, quiz.almostScore)
    }

    @Test
    fun choiceMissDoesNotCarryIntoTypedRound() {
        val quiz = DrillQuiz(listOf(item1))
        // Choice round: wrong pick, then the re-queued item is picked right
        quiz.answer("bei")
        quiz.advance()
        quiz.answer("mit")
        quiz.advance()

        // Typed round: a correct typed answer sets the typed-round result
        assertEquals(DrillRound.TYPED, quiz.round)
        quiz.answerTyped("mit")
        assertEquals(DrillResult.RIGHT, quiz.itemResults()[item1.id])
    }

    @Test
    fun typedResultsTrackOnlyFinalTypedOutcomes() {
        val quiz = DrillQuiz(listOf(item1, item2), startRound = DrillRound.TYPED)

        // Near miss first: no final outcome yet, so no typed result.
        quiz.answerTyped("mi")
        assertTrue(quiz.typedResults().isEmpty())

        // Exact retry: final outcome ALMOST.
        quiz.answerTyped("mit")
        assertEquals(mapOf(item1.id to DrillResult.ALMOST), quiz.typedResults())
        quiz.advance()

        // Far miss on item2: final outcome WRONG.
        quiz.answerTyped("completely wrong")
        assertEquals(
            mapOf(item1.id to DrillResult.ALMOST, item2.id to DrillResult.WRONG),
            quiz.typedResults(),
        )
    }

    @Test
    fun typedResultsKeepAMissWhenTheRetryIsRight() {
        val quiz = DrillQuiz(listOf(item1), startRound = DrillRound.TYPED)
        quiz.answerTyped("completely wrong")
        quiz.advance()
        quiz.answerTyped("mit")

        assertEquals(mapOf(item1.id to DrillResult.WRONG), quiz.typedResults())
    }

    @Test
    fun choiceRoundAnswersDoNotAppearInTypedResults() {
        val quiz = DrillQuiz(listOf(item1))
        quiz.answer(item1.answer)

        assertTrue(quiz.typedResults().isEmpty())
    }

    @Test
    fun typedResultsCoverOnlyTheItemsTypedSoFar() {
        val quiz = DrillQuiz(listOf(item1, item2), startRound = DrillRound.TYPED)
        quiz.answerTyped("mit")
        quiz.advance()

        // item1 has a typed answer, item2 was not reached.
        assertEquals(mapOf(item1.id to DrillResult.RIGHT), quiz.typedResults())
        assertEquals(1, quiz.firstAttempts().size)
    }

    @Test
    fun choiceThenTypedRoundYieldsTheRankingEvidence() {
        // Choice round: item1 right first try, item2 wrong first try then right on retry.
        // Typed round: item1 typed right, item2 typed wrong and staying wrong on its retry.
        val quiz = DrillQuiz(listOf(item1, item2))
        quiz.answer(item1.answer)
        quiz.advance()
        quiz.answer(wrongChoice(item2))
        quiz.advance()
        quiz.answer(item2.answer)
        quiz.advance()
        assertEquals(DrillRound.TYPED, quiz.round)

        // Nothing typed yet, so only the choice evidence exists.
        assertTrue(quiz.typedResults().isEmpty())

        quiz.answerTyped(item1.answer)
        quiz.advance()
        quiz.answerTyped("completely wrong")
        quiz.advance()
        quiz.answerTyped(item2.answer)

        assertEquals(mapOf(item1.id to DrillResult.RIGHT, item2.id to DrillResult.WRONG), quiz.typedResults())
        assertEquals(mapOf(item1.id to true, item2.id to false), quiz.firstAttempts())

        // The evidence a full round records for the decayed counts: 2 items, right once in
        // both the choice and the typed phase.
        val evidence = DrillScores.roundEvidence(
            hasTypedRound = true,
            choiceAnswered = quiz.firstAttempts().size,
            choiceRight = quiz.firstTryScore,
            typedAnswered = quiz.typedResults().size,
            typedRight = quiz.typedResults().values.count { it == DrillResult.RIGHT },
        )
        assertEquals(2.0, evidence.askedDelta, 1e-9)
        assertEquals(1.0 / 3.0 + 1 * 2.0 / 3.0, evidence.rightDelta, 1e-9)
    }
}
