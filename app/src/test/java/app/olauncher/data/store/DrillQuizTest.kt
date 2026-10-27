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

    // ------------------------------------------------------------------
    // Pacing (#drill-pacing): self-shrinking rounds and the miss breather
    // ------------------------------------------------------------------

    private fun itemsOf(type: DrillType, base: String, count: Int): List<DrillItem> =
        (0 until count).map { index ->
            DrillItem(
                id = DrillItemId(type, "$base$index"),
                prompt = "Ich gehe $base$index ___ Park.",
                answer = "mit$base$index",
                choices = listOf("mit$base$index", "bei$base$index"),
                hint = "Dativ",
            )
        }

    @Test
    fun roundShrinksByOnePerWrongAnswerDownToTheFloor() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 5)
        val quiz = DrillQuiz(items.map { tap(it) })
        assertEquals(5, quiz.total)

        quiz.answer(wrongChoice(items[0]))
        assertEquals("first miss shrinks the round by one", 4, quiz.total)
        quiz.advance()

        // Second miss: the breather replaced the next step of this type with a recap,
        // and the floor keeps the round at three scored questions — the unscored recap
        // does not eat into them, so the last scored question stays.
        quiz.answer(wrongChoice(items[1]))
        assertEquals(DrillRoundPlanner.MIN_ROUND_SIZE, quiz.items.size)
        quiz.advance()
        assertEquals(DrillFormat.RECAP, quiz.current!!.format)
        quiz.advance()
        assertEquals(items[3].id, quiz.current!!.id)
        quiz.answer(items[3].answer)
        quiz.advance()
        assertTrue(quiz.isFinished)
    }

    @Test
    fun theShrinkFloorCountsScoredStepsNotTeachingOnes() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 3)
        // A flashcard step must not eat into the floor of three scored questions.
        val quiz = DrillQuiz(listOf(flashcard(items[0]), tap(items[0]), tap(items[1]), tap(items[2])))
        assertEquals(4, quiz.total)
        assertEquals(3, quiz.items.size)

        quiz.answer(wrongChoice(items[0]))
        assertEquals("no scored question is dropped below the floor", 4, quiz.total)
        assertEquals(3, quiz.items.size)
    }

    @Test
    fun aWrongAnswerDropsAPairedFlashcardTogetherWithItsQuestion() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 4)
        // Q1, Q2, Q3, F4, Q4: a miss on Q1 must not leave F4 without its question.
        val quiz = DrillQuiz(
            listOf(tap(items[0]), tap(items[1]), tap(items[2]), flashcard(items[3]), tap(items[3])),
        )
        assertEquals(5, quiz.total)

        quiz.answer(wrongChoice(items[0]))
        // The trailing flashcard and its question go together, so the round never
        // ends on a card whose question was dropped.
        assertEquals(items.take(3).map { it.id }, quiz.steps.map { it.id })
        assertTrue(quiz.steps.none { it.isFlashcard })
        assertEquals(3, quiz.items.size)

        quiz.advance()
        quiz.answer(items[1].answer)
        quiz.advance()
        quiz.answer(items[2].answer)
        quiz.advance()
        assertTrue(quiz.isFinished)
    }

    @Test
    fun theRoundOnlyLosesFutureStepsItNeverReached() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 5)
        val quiz = DrillQuiz(items.map { tap(it) })
        quiz.answer(items[0].answer)
        quiz.advance()
        quiz.answer(wrongChoice(items[1]))
        quiz.advance()
        // Only the tail was dropped; every reached step stays in the plan in order.
        assertEquals(items.take(2).map { it.id }, quiz.steps.take(2).map { it.id })
        assertEquals(items[2].id, quiz.current!!.id)
        assertEquals(4, quiz.total)
    }

    @Test
    fun aRightAnswerDoesNotShrinkTheRound() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 4)
        val quiz = DrillQuiz(items.map { tap(it) })
        quiz.answer(items[0].answer)
        assertEquals(4, quiz.total)
    }

    @Test
    fun twoConsecutiveMissesOfATypeInsertAnUnscoredRecap() {
        val preps = itemsOf(DrillType.PREPOSITIONS, "p", 3)
        val arts = itemsOf(DrillType.ARTICLES, "a", 2)
        // p0 misses, an articles question answers right in between (that does not
        // reset the prepositions streak), then p1 misses: prepositions breathe.
        val steps = listOf(tap(preps[0]), tap(arts[0]), tap(preps[1]), tap(preps[2]), tap(arts[1]))
        val quiz = DrillQuiz(steps)

        quiz.answer(wrongChoice(preps[0]))
        quiz.advance()
        quiz.answer(arts[0].answer)
        quiz.advance()
        quiz.answer(wrongChoice(preps[1]))
        quiz.advance()

        val current = quiz.current!!
        assertEquals(DrillFormat.RECAP, current.format)
        assertEquals(preps[1].id, current.id)
        assertEquals(preps[1].hint, current.hint)
        assertTrue(quiz.isAnswered)

        // The recap is never scored and never answered.
        assertNull(quiz.answer(preps[1].answer))
        assertNull(quiz.lastAnswer)
        assertEquals(
            setOf(preps[0].id, arts[0].id, preps[1].id),
            quiz.itemResults().keys,
        )

        // The recap replaced the next prepositions step rather than adding a step.
        assertEquals(quiz.steps.size, quiz.total)
        quiz.advance()
        assertTrue(quiz.isFinished)
    }

    @Test
    fun theRecapReplacesTheNextStepOfTheMissedTypeAboveTheFloor() {
        val preps = itemsOf(DrillType.PREPOSITIONS, "p", 5)
        val steps = listOf(tap(preps[0]), tap(preps[1]), tap(preps[2]), tap(preps[3]), tap(preps[4]))
        val quiz = DrillQuiz(steps, Random(3))

        quiz.answer(wrongChoice(preps[0]))
        quiz.advance()
        quiz.answer(wrongChoice(preps[1]))
        quiz.advance()

        assertEquals(DrillFormat.RECAP, quiz.current!!.format)
        // preps[2]'s question step was replaced, so it is never asked this round.
        assertTrue(quiz.steps.none { it.id == preps[2].id && it.format == DrillFormat.TAP })
        assertEquals(
            setOf(preps[0].id, preps[1].id),
            quiz.itemResults().keys,
        )
    }

    @Test
    fun atTheFloorTheBreatherInsertsTheRecapInsteadOfReplacingAQuestion() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 3)
        // A planned size of three: three scored questions plus a flashcard fronting
        // the first one — the round already sits at the scored-step floor.
        val quiz = DrillQuiz(
            listOf(flashcard(items[0]), tap(items[0]), tap(items[1]), tap(items[2])),
            Random(11),
        )
        assertEquals(3, quiz.items.size)

        quiz.advance() // past the opening flashcard
        quiz.answer(wrongChoice(items[0]))
        quiz.advance()
        quiz.answer(wrongChoice(items[1]))

        // Two misses of the same type at the floor: the recap is inserted before the
        // third question, not swapped for it, so the round keeps its scored floor.
        assertTrue(quiz.items.size >= DrillRoundPlanner.MIN_ROUND_SIZE)
        assertEquals(3, quiz.items.size)
        assertEquals(items[2].id, quiz.steps.last().id)

        quiz.advance()
        assertEquals(DrillFormat.RECAP, quiz.current!!.format)

        // The inserted recap is unscored and never counted in items.
        assertEquals(1, quiz.steps.count { it.isRecap })
        assertNull(quiz.answer(items[1].answer))
        assertEquals(
            setOf(items[0].id, items[1].id),
            quiz.itemResults().keys,
        )

        quiz.advance()
        assertEquals(items[2].id, quiz.current!!.id)
        quiz.answer(items[2].answer)
        quiz.advance()
        assertTrue(quiz.isFinished)
        assertEquals(
            setOf(items[0].id, items[1].id, items[2].id),
            quiz.itemResults().keys,
        )
    }

    @Test
    fun aRightAnswerResetsTheMissStreakSoNoBreatherIsInserted() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 3)
        val quiz = DrillQuiz(items.map { tap(it) })

        quiz.answer(wrongChoice(items[0]))
        quiz.advance()
        quiz.answer(items[1].answer)
        quiz.advance()
        quiz.answer(wrongChoice(items[2]))
        quiz.advance()

        assertTrue(quiz.isFinished)
        assertTrue(quiz.steps.none { it.isRecap })
    }

    @Test
    fun atTheFloorThePairedFlashcardTradesItsSlotToTheRecapButTheQuestionStays() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 3)
        // Q1, Q2, F3, Q3: at the floor the recap takes the flashcard's slot — the card
        // must not front a recap of a different item — and Q3 stays scored.
        val quiz = DrillQuiz(listOf(tap(items[0]), tap(items[1]), flashcard(items[2]), tap(items[2])))

        quiz.answer(wrongChoice(items[0]))
        quiz.advance()
        quiz.answer(wrongChoice(items[1]))
        quiz.advance()

        // The flashcard that fronted the next question went with it.
        assertTrue(quiz.steps.none { it.isFlashcard })
        val current = quiz.current!!
        assertEquals(DrillFormat.RECAP, current.format)
        assertEquals(items[1].id, current.id)
        assertTrue(quiz.isAnswered)
        assertEquals(3, quiz.items.size)

        quiz.advance()
        assertEquals(items[2].id, quiz.current!!.id)
        quiz.answer(items[2].answer)
        quiz.advance()
        assertTrue(quiz.isFinished)
    }

    @Test
    fun aboveTheFloorTheBreatherTakesTheReplacedQuestionFlashcardWithIt() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 5)
        // The trailing flashcard blocks the round from shrinking past four scored
        // questions, so the breather lands above the floor and replaces a step that
        // has its own flashcard right before it.
        val quiz = DrillQuiz(
            listOf(
                tap(items[0]),
                tap(items[1]),
                flashcard(items[2]),
                tap(items[2]),
                tap(items[3]),
                flashcard(items[4]),
            ),
        )

        quiz.answer(wrongChoice(items[0]))
        quiz.advance()
        quiz.answer(wrongChoice(items[1]))
        quiz.advance()

        assertEquals(DrillFormat.RECAP, quiz.current!!.format)
        // The flashcard fronting the replaced question went with it; that question is
        // never asked this round.
        assertTrue(quiz.steps.none { it.isFlashcard && it.id == items[2].id })
        assertTrue(quiz.steps.none { it.id == items[2].id && it.format == DrillFormat.TAP })
        assertEquals(3, quiz.items.size)
    }

    @Test
    fun typedSecondMissCountsTowardTheBreather() {
        val items = itemsOf(DrillType.PREPOSITIONS, "p", 4)
        val quiz = DrillQuiz(listOf(typed(items[0]), tap(items[1]), tap(items[2]), tap(items[3])))

        quiz.answerTyped("zzz")
        quiz.advance()
        quiz.answer(wrongChoice(items[1]))
        quiz.advance()

        assertEquals(DrillFormat.RECAP, quiz.current!!.format)
    }

    // ------------------------------------------------------------------
    // Reveal fallback: a choice question without choices never dead-ends
    // ------------------------------------------------------------------

    @Test
    fun aChoiceQuestionWithoutChoicesIsAnUnscoredRevealThatCanAdvance() {
        val broken = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "broken"),
            prompt = "Ich gehe ___ Park.",
            answer = "durch",
            choices = emptyList(),
        )
        val next = itemsOf(DrillType.ARTICLES, "a", 1)[0]
        val quiz = DrillQuiz(listOf(tap(broken), tap(next)))

        assertTrue("the reveal step counts as dealt-with", quiz.isAnswered)
        assertNull("the reveal step is never graded", quiz.answer(broken.answer))
        assertNull(quiz.lastAnswer)

        quiz.advance()
        assertEquals(next.id, quiz.current!!.id)

        // The reveal is not a scored item either: it never reaches the summary, so it
        // cannot inflate the wrong count or surface an empty "Practise misses" round.
        assertEquals(listOf(next), quiz.items)
        assertFalse(broken.id in quiz.itemResults())
        assertFalse(broken.id in quiz.firstAttempts())

        quiz.answer(next.answer)
        quiz.advance()
        assertTrue(quiz.isFinished)
        val summaryItems = quiz.items.mapIndexed { index, item ->
            val res = quiz.itemResults()[item.id]
                ?: if (quiz.firstAttempts()[item.id] == true) DrillResult.RIGHT else DrillResult.WRONG
            DrillSummaryItem(
                id = item.id,
                prompt = item.prompt,
                answer = item.answer,
                hint = item.hint,
                result = res,
                roundIndex = index,
            )
        }
        val counts = DrillSummaryLogic.counts(summaryItems)
        assertEquals(1, counts.right)
        assertEquals(0, counts.almost)
        assertEquals(0, counts.wrong)
    }
}
