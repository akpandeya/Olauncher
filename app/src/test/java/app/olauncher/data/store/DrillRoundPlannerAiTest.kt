package app.olauncher.data.store

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI practice items in mixed rounds (#621, Epic 9 #561): capped at
 * [DrillRoundPlanner.MAX_AI_PER_ROUND] per round (their own cap, independent of the
 * built-in flashcard cap), inside the planned round size. A word-order AI item with a
 * parseable tile card joins as a scored [DrillFormat.WORD_TILES] step (#804 part 2);
 * everything else stays the flashcard-only teaching step of #621, excluded from
 * scoring. Practise-misses rounds stay AI-free, and the planner is a no-op when no
 * AI items are stored.
 */
class DrillRoundPlannerAiTest {

    /** Built-in items with distinguishing prompts, answers and choices. */
    private fun pool(type: DrillType, base: String, count: Int): List<DrillItem> =
        (0 until count).map { index ->
            DrillItem(
                id = DrillItemId(type, "$base$index"),
                prompt = "Ich gehe $base$index ___ Park.",
                answer = "durch$base$index",
                choices = listOf("durch$base$index", "ande$base$index", "gegen$base$index"),
            )
        }

    /** AI practice items with distinguishing answers, as the adapter produces them. */
    private fun aiItems(count: Int): List<DrillItem> =
        (0 until count).map { index ->
            PracticeDrillItems.toDrillItem(
                PracticeItem(
                    id = 100L + index,
                    wordId = 7,
                    type = DrillType.CLOZE,
                    prompt = "Ai item $index: ___",
                    answer = "ai$index",
                    choices = listOf("ai$index", "aiOther$index", "aiThird$index"),
                    hint = "ai hint $index",
                ),
            )
        }

    /**
     * AI word-order items with parseable tile cards (#804 part 2): the generation
     * contract's "Ordne: " prompt, the answer as the accepted order, one wrong order
     * among the choices.
     */
    private fun aiWordOrderItems(count: Int): List<DrillItem> =
        (0 until count).map { index ->
            PracticeDrillItems.toDrillItem(
                PracticeItem(
                    id = 300L + index,
                    wordId = 7,
                    type = DrillType.WORD_ORDER,
                    prompt = "Ordne: Wir$index / sehen / heute / den / Film",
                    answer = "Wir$index sehen heute den Film",
                    choices = listOf("Wir$index sehen heute den Film", "Wir$index heute sehen den Film"),
                    hint = "Hauptsatz: Position 2 ist das Verb",
                ),
            )
        }

    private fun aiSteps(steps: List<DrillQuizQuestion>) =
        steps.filter { PracticeDrillItems.isAiItem(it.id) }

    private fun builtInSteps(steps: List<DrillQuizQuestion>) =
        steps.filterNot { PracticeDrillItems.isAiItem(it.id) }

    private val mixedPools = mapOf(
        DrillType.PREPOSITIONS to pool(DrillType.PREPOSITIONS, "prep:", 8),
        DrillType.PRONOUNS to pool(DrillType.PRONOUNS, "pron:", 8),
    )

    // ------------------------------------------------------------------
    // Empty pool: no-op
    // ------------------------------------------------------------------

    @Test
    fun anEmptyAiPoolLeavesTheRoundByteIdentical() {
        for (seed in 0 until 100) {
            val baseline = DrillRoundPlanner.plan(mixedPools, emptyMap(), Random(seed.toLong()))
            val withEmptyAi = DrillRoundPlanner.plan(
                mixedPools,
                emptyMap(),
                Random(seed.toLong()),
                aiItems = PracticeDrillItems.toDrillItems(emptyList()),
            )
            assertEquals("seed $seed", baseline, withEmptyAi)
        }
    }

    // ------------------------------------------------------------------
    // Cap and round size
    // ------------------------------------------------------------------

    @Test
    fun atMostTwoAiItemsJoinARoundOfFive() {
        val ai = aiItems(4)
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(
                mixedPools,
                emptyMap(),
                Random(seed.toLong()),
                size = 5,
                aiItems = ai,
            )
            assertEquals("seed $seed", DrillRoundPlanner.MAX_AI_PER_ROUND, aiSteps(steps).size)
            // The cap keeps the round at its planned size: AI cards take slots.
            assertEquals("seed $seed", 5, steps.size)
        }
    }

    @Test
    fun oneOrTwoAiItemsAppearInFull() {
        for (count in 1..2) {
            val ai = aiItems(count)
            for (seed in 0 until 20) {
                val steps = DrillRoundPlanner.plan(
                    mixedPools,
                    emptyMap(),
                    Random(seed.toLong()),
                    size = 5,
                    aiItems = ai,
                )
                assertEquals("count $count seed $seed", count, aiSteps(steps).size)
                // Round size still wins: the total never grows past the planned size.
                assertEquals("count $count seed $seed", 5, steps.size)
            }
        }
    }

    @Test
    fun bothAiItemsJoinEvenTheSmallestRound() {
        val steps = DrillRoundPlanner.plan(
            mixedPools,
            emptyMap(),
            Random(11),
            size = DrillRoundPlanner.MIN_ROUND_SIZE,
            aiItems = aiItems(2),
        )
        // 1-2 available: all of them appear, and the round size still wins — the
        // built-in items fill only what the AI cards leave of the 3 steps.
        assertEquals(2, aiSteps(steps).size)
        assertEquals(DrillRoundPlanner.MIN_ROUND_SIZE, steps.size)
    }

    // ------------------------------------------------------------------
    // Flashcard-only
    // ------------------------------------------------------------------

    @Test
    fun aiItemsAreAlwaysFlashcardSteps() {
        val ai = aiItems(3)
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(
                mixedPools,
                emptyMap(),
                Random(seed.toLong()),
                size = 5,
                aiItems = ai,
            )
            for (step in aiSteps(steps)) {
                assertEquals("seed $seed", DrillFormat.FLASHCARD, step.format)
                assertTrue("seed $seed", step.isTeaching)
                assertFalse("seed $seed", step.isTyped)
                assertTrue("seed $seed", step.choices.isEmpty())
            }
        }
    }

    @Test
    fun aiFlashcardsDoNotCountAgainstTheBuiltInFlashcardCap() {
        val ai = aiItems(3)
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(
                mixedPools,
                emptyMap(),
                Random(seed.toLong()),
                size = 5,
                aiItems = ai,
            )
            // The built-in cap still holds for built-in teaching steps...
            val builtInFlashcards = builtInSteps(steps).count { it.format == DrillFormat.FLASHCARD }
            assertTrue(
                "seed $seed: $builtInFlashcards built-in flashcards",
                builtInFlashcards <= DrillRoundPlanner.MAX_FLASHCARDS_PER_ROUND,
            )
            // ...and the AI cards ride on top of it: their own cap of 2.
            assertEquals("seed $seed", DrillRoundPlanner.MAX_AI_PER_ROUND, aiSteps(steps).size)
        }
    }

    // ------------------------------------------------------------------
    // No score side effects
    // ------------------------------------------------------------------

    /** Answers a round to the end, correctly, so every scored item earns evidence. */
    private fun runRound(steps: List<DrillQuizQuestion>): DrillQuiz {
        val quiz = DrillQuiz(steps, Random(7))
        while (!quiz.isFinished) {
            val question = quiz.current ?: break
            if (!quiz.isAnswered) {
                if (question.isTyped) {
                    quiz.answerTyped(question.answer)
                } else {
                    quiz.answer(question.correctChoice ?: question.answer)
                }
            } else {
                quiz.advance()
            }
        }
        return quiz
    }

    /** A tap round over [items] — the same built-in questions the with-AI round carries. */
    private fun builtInRound(items: List<DrillItem>): List<DrillQuizQuestion> =
        items.map { DrillQuizQuestion(it, DrillFormat.TAP, it.choices) }

    @Test
    fun aiItemsChangeNoTypeScoreEvidence() {
        val builtIns = pool(DrillType.PREPOSITIONS, "prep:", 3) + pool(DrillType.PRONOUNS, "pron:", 2)
        val withoutAi = runRound(builtInRound(builtIns))
        val withAi = runRound(
            builtInRound(builtIns) +
                aiItems(2).map { DrillQuizQuestion(it, DrillFormat.FLASHCARD) },
        )

        // The AI cards leave no trace in any evidence map...
        assertTrue(withAi.firstAttempts().keys.none { PracticeDrillItems.isAiItem(it) })
        assertTrue(withAi.itemResults().keys.none { PracticeDrillItems.isAiItem(it) })
        assertTrue(withAi.typedResults().keys.none { PracticeDrillItems.isAiItem(it) })
        // ...and the per-type evidence is identical to the same round without them.
        val evidenceWithout = DrillScores.roundEvidenceByType(
            withoutAi.firstAttempts(),
            withoutAi.typedResults(),
            abandoned = false,
        )
        val evidenceWith = DrillScores.roundEvidenceByType(
            withAi.firstAttempts(),
            withAi.typedResults(),
            abandoned = false,
        )
        assertEquals(evidenceWithout, evidenceWith)
    }

    // ------------------------------------------------------------------
    // Word-tiles steps (#804 part 2)
    // ------------------------------------------------------------------

    @Test
    fun parseableAiWordOrderItemBecomesAScoredTileStep() {
        val ai = aiWordOrderItems(1)
        for (seed in 0 until 20) {
            val steps = DrillRoundPlanner.plan(
                mixedPools,
                emptyMap(),
                Random(seed.toLong()),
                size = 5,
                aiItems = ai,
            )
            val tileSteps = aiSteps(steps)
            assertEquals("seed $seed", 1, tileSteps.size)
            val step = tileSteps.single()
            assertEquals("seed $seed", DrillFormat.WORD_TILES, step.format)
            // A scored question, not a teaching beat or a reveal fallback.
            assertFalse("seed $seed", step.isTeaching)
            assertFalse("seed $seed", step.isReveal)
            assertEquals("seed $seed", listOf("Wir0", "sehen", "heute", "den", "Film"), step.tiles)
            assertEquals(
                "seed $seed",
                listOf(listOf("Wir0", "sehen", "heute", "den", "Film")),
                step.acceptedOrders,
            )
            assertEquals("seed $seed", "Hauptsatz: Position 2 ist das Verb", step.hint)
        }
    }

    @Test
    fun unparseableAiWordOrderItemStaysAFlashcard() {
        // The prompt is not "Ordne: " + tiles joined by " / ", so the task-01
        // parser rejects it and the item keeps the flashcard-only behaviour of #621.
        val ai = listOf(
            PracticeDrillItems.toDrillItem(
                PracticeItem(
                    id = 310L,
                    wordId = 7,
                    type = DrillType.WORD_ORDER,
                    prompt = "Ordne die Wörter richtig",
                    answer = "Wir sehen heute den Film",
                    choices = listOf("Wir sehen heute den Film"),
                    hint = "hint",
                ),
            ),
        )
        for (seed in 0 until 20) {
            val steps = DrillRoundPlanner.plan(
                mixedPools,
                emptyMap(),
                Random(seed.toLong()),
                size = 5,
                aiItems = ai,
            )
            assertEquals("seed $seed", 1, aiSteps(steps).size)
            assertEquals("seed $seed", DrillFormat.FLASHCARD, aiSteps(steps).single().format)
        }
    }

    @Test
    fun aiTileStepsKeepThePerRoundCapAndRoundSize() {
        val ai = aiWordOrderItems(4)
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(
                mixedPools,
                emptyMap(),
                Random(seed.toLong()),
                size = 5,
                aiItems = ai,
            )
            assertEquals("seed $seed", DrillRoundPlanner.MAX_AI_PER_ROUND, aiSteps(steps).size)
            assertEquals("seed $seed", 5, steps.size)
            assertTrue("seed $seed", aiSteps(steps).all { it.format == DrillFormat.WORD_TILES })
        }
    }

    @Test
    fun planMissesStaysAiFreeIncludingTileSteps() {
        val missed = pool(DrillType.PREPOSITIONS, "prep:", 2) + aiWordOrderItems(1)
        val steps = DrillRoundPlanner.planMisses(missed, emptyMap(), Random(3))
        assertTrue(steps.none { PracticeDrillItems.isAiItem(it.id) })
        // The built-in misses still come back, in ladder formats.
        assertEquals(2, steps.size)
    }
}
