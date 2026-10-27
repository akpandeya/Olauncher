package app.olauncher.data.store

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round construction for the redesigned drill round (#639): 3-5 steps, ladder formats
 * driven by per-item maturity, at most 3 typed and 1 flashcard per round, no two
 * consecutive questions of the same format, mixed rounds interleaving their types.
 */
class DrillRoundPlannerTest {

    /** Items with ids derived from [base] so maturity can be attached to them. */
    private fun pool(type: DrillType, base: String, count: Int): List<DrillItem> =
        (0 until count).map { index ->
            DrillItem(
                id = DrillItemId(type, "$base$index"),
                prompt = "Ich gehe $base$index ___ Park.",
                answer = "durch$base$index",
                choices = listOf("durch$base$index", "ande$base$index", "gegen$base$index"),
            )
        }

    private fun maturityOf(vararg pairs: Pair<DrillItem, Int>): Map<DrillItemId, Int> =
        pairs.associate { (item, level) -> item.id to level }

    // ------------------------------------------------------------------
    // Sizing
    // ------------------------------------------------------------------

    @Test
    fun aRoundHasThreeToFiveSteps() {
        val pools = mapOf(DrillType.PREPOSITIONS to pool(DrillType.PREPOSITIONS, "prep:", 20))
        for (seed in 0 until 100) {
            val steps = DrillRoundPlanner.plan(pools, random = Random(seed.toLong()))
            assertTrue(
                "seed $seed: round size ${steps.size} outside 3..5",
                steps.size in DrillRoundPlanner.MIN_ROUND_SIZE..DrillRoundPlanner.MAX_ROUND_SIZE,
            )
        }
    }

    @Test
    fun aRoundUsesAllItemsWhenThePoolIsSmallerThanFive() {
        val items = pool(DrillType.PRONOUNS, "pron:", 3)
        val steps = DrillRoundPlanner.plan(mapOf(DrillType.PRONOUNS to items), random = Random(1))
        assertEquals(items.size, steps.count { !it.isFlashcard })
    }

    // ------------------------------------------------------------------
    // Ladder / typed graduation
    // ------------------------------------------------------------------

    @Test
    fun firstExposureIsAlwaysTap() {
        val fresh = pool(DrillType.PREPOSITIONS, "prep:", 8)
        val steps = DrillRoundPlanner.plan(
            mapOf(DrillType.PREPOSITIONS to fresh),
            maturity = emptyMap(),
            random = Random(3),
        )
        assertTrue(steps.none { it.isTyped })
        assertTrue(steps.any { it.format == DrillFormat.TAP })
    }

    @Test
    fun onlyItemsAnsweredRightTwiceBecomeTyped() {
        val fresh = pool(DrillType.PREPOSITIONS, "prep:", 4)
        val once = pool(DrillType.PRONOUNS, "pron:", 4)
        val twice = pool(DrillType.ARTICLES, "art:", 4)
        val maturity = (
            once.map { it.id to 1 } + twice.map { it.id to 2 } + fresh.map { it.id to 0 }
            ).toMap()
        val pools = mapOf(
            DrillType.PREPOSITIONS to fresh,
            DrillType.PRONOUNS to once,
            DrillType.ARTICLES to twice,
        )
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(pools, maturity, Random(seed.toLong()))
            val typed = steps.filter { it.isTyped }
            assertTrue(typed.all { it.id.type == DrillType.ARTICLES })
        }
    }

    @Test
    fun atMostThreeTypedQuestionsPerRound() {
        val graduated = pool(DrillType.PREPOSITIONS, "prep:", 10)
        val maturity = graduated.map { it.id to DrillRoundPlanner.TYPED_MATURITY }.toMap()
        val steps = DrillRoundPlanner.plan(
            mapOf(DrillType.PREPOSITIONS to graduated),
            maturity,
            Random(5),
        )
        assertTrue(steps.count { it.isTyped } <= DrillRoundPlanner.MAX_TYPED_PER_ROUND)
    }

    @Test
    fun graduatedItemsBeyondTheTypedCapFallBackToTapOrVariety() {
        val graduated = pool(DrillType.PREPOSITIONS, "prep:", 8)
        val maturity = graduated.map { it.id to DrillRoundPlanner.TYPED_MATURITY }.toMap()
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(
                mapOf(DrillType.PREPOSITIONS to graduated),
                maturity,
                Random(seed.toLong()),
            )
            val typedCount = steps.count { it.isTyped }
            assertTrue(typedCount <= DrillRoundPlanner.MAX_TYPED_PER_ROUND)
            // The round still covers its items; nothing stalls because of the cap.
            assertTrue(steps.isNotEmpty())
        }
    }

    @Test
    fun aRoundNeverStallsWhenNothingIsTypeable() {
        val fresh = pool(DrillType.PREPOSITIONS, "prep:", 10)
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(
                mapOf(DrillType.PREPOSITIONS to fresh),
                emptyMap(),
                Random(seed.toLong()),
            )
            assertTrue(steps.isNotEmpty())
            assertTrue(steps.none { it.isTyped })
        }
    }

    // ------------------------------------------------------------------
    // Variety rung (true-false generation, tap degradation)
    // ------------------------------------------------------------------

    @Test
    fun itemsAnsweredRightOnceGetTheVarietyFormat() {
        val once = pool(DrillType.PREPOSITIONS, "prep:", 6)
        val maturity = once.map { it.id to 1 }.toMap()
        val steps = DrillRoundPlanner.plan(
            mapOf(DrillType.PREPOSITIONS to once),
            maturity,
            Random(11),
        )
        // Every such item supports true-false (gap + distractor), so none degrades;
        // an early item may carry the one flashcard step ahead of its question.
        assertTrue(steps.none { it.isTyped })
        assertTrue(steps.all { it.format == DrillFormat.TRUE_FALSE || it.isFlashcard })
    }

    @Test
    fun trueFalseStatementsFillTheGapWithTheAnswerOrASafeDistractor() {
        val once = pool(DrillType.PREPOSITIONS, "prep:", 6)
        val maturity = once.map { it.id to 1 }.toMap()
        val steps = DrillRoundPlanner.plan(
            mapOf(DrillType.PREPOSITIONS to once),
            maturity,
            Random(11),
        )
        for (step in steps) {
            if (step.isFlashcard) continue // the one teaching step carries no statement
            assertNotNull(step.statement)
            assertTrue(!step.statement!!.contains(DrillItem.GAP_MARKER))
            assertEquals(
                listOf(DrillQuizQuestion.TRUE_CHOICE, DrillQuizQuestion.FALSE_CHOICE),
                step.choices,
            )
            if (step.isTrue) {
                assertTrue(step.statement!!.contains(step.answer))
            } else {
                // A false statement must not be filled with an accepted answer.
                val accepted = step.answer.split(Regex("\\s*/\\s*"))
                assertTrue(accepted.none { step.statement!!.contains(it) })
            }
        }
    }

    @Test
    fun itemsWithoutAGapDegradeToTap() {
        val gapless = (0 until 6).map { index ->
            DrillItem(
                id = DrillItemId(DrillType.PREPOSITIONS, "g$index"),
                prompt = "Wo geht's hin? $index",
                answer = "nach Hause$index",
                choices = listOf("nach Hause$index", "ins Bett$index"),
            )
        }
        val maturity = gapless.map { it.id to 1 }.toMap()
        val steps = DrillRoundPlanner.plan(
            mapOf(DrillType.PREPOSITIONS to gapless),
            maturity,
            Random(13),
        )
        assertTrue(steps.all { it.format == DrillFormat.TAP || it.isFlashcard })
    }

    // ------------------------------------------------------------------
    // Flashcards
    // ------------------------------------------------------------------

    @Test
    fun atMostOneFlashcardPerRoundAndItPrecedesItsOwnQuestion() {
        val fresh = pool(DrillType.PREPOSITIONS, "prep:", 10)
        for (seed in 0 until 100) {
            val steps = DrillRoundPlanner.plan(
                mapOf(DrillType.PREPOSITIONS to fresh),
                emptyMap(),
                Random(seed.toLong()),
            )
            assertTrue(steps.count { it.isFlashcard } <= DrillRoundPlanner.MAX_FLASHCARDS_PER_ROUND)
            steps.forEachIndexed { index, step ->
                if (step.isFlashcard) {
                    val next = steps.getOrNull(index + 1)
                    assertNotNull("seed $seed: flashcard must be followed by its question", next)
                    assertEquals(step.item.id, next!!.item.id)
                    assertTrue(
                        "seed $seed: flashcard followed by ${next.format}",
                        next.format == DrillFormat.TAP || next.format == DrillFormat.TRUE_FALSE,
                    )
                }
            }
        }
    }

    @Test
    fun flashcardsGoToMaturityZeroAndOneItemsOnly() {
        val fresh = pool(DrillType.PREPOSITIONS, "prep:", 4)
        val once = pool(DrillType.PRONOUNS, "pron:", 4)
        val known = pool(DrillType.ARTICLES, "art:", 4)
        val maturity = once.map { it.id to 1 }.toMap() + known.map { it.id to 2 }.toMap()
        val pools = mapOf(
            DrillType.PREPOSITIONS to fresh,
            DrillType.PRONOUNS to once,
            DrillType.ARTICLES to known,
        )
        var flashcardOnMaturityOne = false
        for (seed in 0 until 100) {
            val steps = DrillRoundPlanner.plan(pools, maturity, Random(seed.toLong()))
            for (card in steps.filter { it.isFlashcard }) {
                assertTrue(
                    "seed $seed: flashcard on graduated item ${card.id}",
                    (maturity[card.id] ?: 0) <= 1,
                )
                flashcardOnMaturityOne = flashcardOnMaturityOne || card.id.type == DrillType.PRONOUNS
            }
        }
        assertTrue("expected some seed to flashcard a maturity-1 item", flashcardOnMaturityOne)
    }

    @Test
    fun everyPlannedChoiceQuestionHasAtLeastOneChoice() {
        // Planner invariant: a TAP or true-false question can never be planned without
        // choices, so the UI's empty-grid dead end is unreachable by content.
        val a = pool(DrillType.PREPOSITIONS, "prep:", 10)
        val b = pool(DrillType.PRONOUNS, "pron:", 10)
        val c = pool(DrillType.ARTICLES, "art:", 10)
        val pools = mapOf(
            DrillType.PREPOSITIONS to a,
            DrillType.PRONOUNS to b,
            DrillType.ARTICLES to c,
        )
        val maturity = (a.map { it.id to 0 } + b.map { it.id to 1 } + c.map { it.id to 2 }).toMap()
        for (seed in 0 until 100) {
            val random = Random(seed.toLong())
            for (step in DrillRoundPlanner.plan(pools, maturity, random)) {
                if (step.format != DrillFormat.TAP && step.format != DrillFormat.TRUE_FALSE) continue
                assertTrue(
                    "seed $seed: ${step.format} step ${step.id} planned without choices",
                    step.choices.isNotEmpty(),
                )
            }
            val missed = (a + b + c).take(4)
            for (step in DrillRoundPlanner.planMisses(missed, maturity, random)) {
                if (step.format != DrillFormat.TAP && step.format != DrillFormat.TRUE_FALSE) continue
                assertTrue(
                    "seed $seed: planned-misses ${step.format} step ${step.id} without choices",
                    step.choices.isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun aPractiseMissesRoundHasNoFlashcards() {
        val missed = pool(DrillType.PREPOSITIONS, "prep:", 6)
        val steps = DrillRoundPlanner.planMisses(missed, emptyMap(), Random(17))
        assertTrue(steps.none { it.isFlashcard })
        assertEquals(missed.map { it.id }.toSet(), steps.map { it.id }.toSet())
    }

    // ------------------------------------------------------------------
    // No two consecutive formats / interleaving
    // ------------------------------------------------------------------

    @Test
    fun noTwoConsecutiveQuestionsShareAFormat() {
        val fresh = pool(DrillType.PREPOSITIONS, "prep:", 4)
        val once = pool(DrillType.PRONOUNS, "pron:", 4)
        val twice = pool(DrillType.ARTICLES, "art:", 4)
        val maturity = (
            fresh.map { it.id to 0 } + once.map { it.id to 1 } + twice.map { it.id to 2 }
            ).toMap()
        val pools = mapOf(
            DrillType.PREPOSITIONS to fresh,
            DrillType.PRONOUNS to once,
            DrillType.ARTICLES to twice,
        )
        for (seed in 0 until 100) {
            val steps = DrillRoundPlanner.plan(pools, maturity, Random(seed.toLong()))
            steps.zipWithNext().forEach { (a, b) ->
                assertTrue(
                    "seed $seed: ${a.format} followed by ${b.format}",
                    a.format != b.format,
                )
            }
        }
    }

    @Test
    fun aBrandNewSingleTypeRoundIsAllTapAndRepeatsTheFormatBackToBack() {
        // Known shortfall (review round 1 of #639): a round where every item is brand
        // new and of one type is all TAP plus at most one flashcard, so the
        // no-consecutive-repeat rule cannot hold there. This test pins that shortfall
        // so a future change to it is a conscious one.
        val fresh = pool(DrillType.PREPOSITIONS, "prep:", 8)
        var roundsWithARepeat = 0
        for (seed in 0 until 100) {
            val steps = DrillRoundPlanner.plan(
                mapOf(DrillType.PREPOSITIONS to fresh),
                emptyMap(),
                Random(seed.toLong()),
            )
            assertTrue(steps.all { it.format == DrillFormat.TAP || it.isFlashcard })
            if (steps.zipWithNext().any { (a, b) -> a.format == b.format }) roundsWithARepeat++
        }
        assertTrue("expected the tap-only shortfall to show in some rounds", roundsWithARepeat > 0)
    }

    @Test
    fun aMixedRoundDrawsFromEverySampledType() {
        val a = pool(DrillType.PREPOSITIONS, "prep:", 10)
        val b = pool(DrillType.PRONOUNS, "pron:", 10)
        val c = pool(DrillType.ARTICLES, "art:", 10)
        val pools = mapOf(
            DrillType.PREPOSITIONS to a,
            DrillType.PRONOUNS to b,
            DrillType.ARTICLES to c,
        )
        for (seed in 0 until 50) {
            val steps = DrillRoundPlanner.plan(pools, emptyMap(), Random(seed.toLong()))
            val types = steps.map { it.id.type }.toSet()
            assertEquals(pools.keys, types)
        }
    }

    @Test
    fun emptyPoolsProduceNoRound() {
        val steps = DrillRoundPlanner.plan(mapOf(DrillType.PREPOSITIONS to emptyList()), random = Random(1))
        assertTrue(steps.isEmpty())
    }

    @Test
    fun roundSizeIsWithinTheBounds() {
        for (seed in 0 until 100) {
            val size = DrillRoundPlanner.roundSize(Random(seed.toLong()))
            assertTrue(size in DrillRoundPlanner.MIN_ROUND_SIZE..DrillRoundPlanner.MAX_ROUND_SIZE)
        }
    }

    @Test
    fun sizeParameterIsHonouredAndCapped() {
        val items = pool(DrillType.PREPOSITIONS, "prep:", 12)
        val small = DrillRoundPlanner.plan(
            mapOf(DrillType.PREPOSITIONS to items),
            random = Random(2),
            size = 5,
        )
        assertTrue(small.size in 5..6) // a flashcard may add one step within the cap
        val large = DrillRoundPlanner.plan(
            mapOf(DrillType.PREPOSITIONS to items),
            random = Random(2),
            size = 20,
        )
        assertTrue(large.size <= DrillRoundPlanner.MAX_ROUND_SIZE)
    }
}
