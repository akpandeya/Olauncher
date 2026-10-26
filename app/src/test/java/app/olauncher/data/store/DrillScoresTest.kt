package app.olauncher.data.store

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillScoresTest {

    @Test
    fun `decode returns empty map for null, empty, or blank string`() {
        assertTrue(DrillScores.decode(null).isEmpty())
        assertTrue(DrillScores.decode("").isEmpty())
        assertTrue(DrillScores.decode("   ").isEmpty())
    }

    @Test
    fun `decode returns empty map for malformed values`() {
        // Not valid JSON syntax
        assertTrue(DrillScores.decode("not a json").isEmpty())
        assertTrue(DrillScores.decode("{unclosed json").isEmpty())
        assertTrue(DrillScores.decode("[1, 2, 3]").isEmpty())

        // Valid JSON but malformed structure
        assertTrue(DrillScores.decode("{\"prepositions\": \"not-an-object\"}").isEmpty())
        assertTrue(DrillScores.decode("{\"prepositions\": {\"asked\": \"invalid\"}}").isEmpty())
        assertTrue(DrillScores.decode("{\"prepositions\": {}}").isEmpty())
    }

    @Test
    fun `decode parses valid scores per type id`() {
        val raw = """
            {
                "prepositions": {"asked": 10, "firstTryRight": 8},
                "pronouns": {"asked": 5, "firstTryRight": 4}
            }
        """.trimIndent()
        val scores = DrillScores.decode(raw)

        assertEquals(2, scores.size)
        // Only the old keys: the decayed counts and rounds are seeded from them.
        assertEquals(DrillScores.Score(10, 8, 10.0, 8.0, 1), scores["prepositions"])
        assertEquals(DrillScores.Score(5, 4, 5.0, 4.0, 1), scores["pronouns"])
    }

    @Test
    fun `decode reads the new keys beside the old keys`() {
        val raw = """
            {
                "prepositions": {"asked": 30, "firstTryRight": 21, "askedDecayed": 24.4, "rightDecayed": 17.08, "rounds": 3}
            }
        """.trimIndent()

        assertEquals(DrillScores.Score(30, 21, 24.4, 17.08, 3), DrillScores.decode(raw)["prepositions"])
    }

    @Test
    fun `malformed new keys fall back to the seeded counts`() {
        val raw = """
            {
                "prepositions": {"asked": 10, "firstTryRight": 8, "askedDecayed": "invalid", "rounds": -2}
            }
        """.trimIndent()
        val score = DrillScores.decode(raw)["prepositions"]

        assertNotNull(score)
        assertEquals(10.0, score!!.askedDecayed, 0.0)
        assertEquals(8.0, score.rightDecayed, 0.0)
        assertEquals(0, score.rounds)
    }

    @Test
    fun `encode keeps the old keys and adds the new keys beside them`() {
        val encoded = DrillScores.encode(
            mapOf("prepositions" to DrillScores.Score(10, 7, 10.0, 7.0, 1))
        )
        val scoreObj = JSONObject(encoded).getJSONObject("prepositions")

        assertTrue(scoreObj.has("asked"))
        assertTrue(scoreObj.has("firstTryRight"))
        assertTrue(scoreObj.has("askedDecayed"))
        assertTrue(scoreObj.has("rightDecayed"))
        assertTrue(scoreObj.has("rounds"))
    }

    @Test
    fun `encode and decode round-trips accurately`() {
        val original = mapOf(
            "prepositions" to DrillScores.Score(asked = 15, firstTryRight = 12, rounds = 2),
            "conjugation" to DrillScores.Score(
                asked = 6,
                firstTryRight = 5,
                askedDecayed = 5.2,
                rightDecayed = 4.4,
                rounds = 1,
            ),
        )
        val encoded = DrillScores.encode(original)
        val decoded = DrillScores.decode(encoded)

        assertEquals(original, decoded)
    }

    @Test
    fun `percentage calculation handles rounding and zero division`() {
        assertEquals(0, DrillScores.Score(0, 0).percentage)
        assertEquals(100, DrillScores.Score(10, 10).percentage)
        assertEquals(0, DrillScores.Score(10, 0).percentage)
        // 8 / 10 = 80%
        assertEquals(80, DrillScores.Score(10, 8).percentage)
        // 2 / 3 = 66.666% -> rounds to 67%
        assertEquals(67, DrillScores.Score(3, 2).percentage)
        // 1 / 3 = 33.333% -> rounds to 33%
        assertEquals(33, DrillScores.Score(3, 1).percentage)
    }

    @Test
    fun `recordRound decays old counts and adds the round`() {
        val initial = mapOf(
            "prepositions" to DrillScores.Score(asked = 10, firstTryRight = 8, askedDecayed = 10.0, rightDecayed = 8.0, rounds = 1)
        )

        val updated = DrillScores.recordRound(initial, "prepositions", asked = 5, firstTryRight = 4)
        val score = updated["prepositions"]!!
        assertEquals(15, score.asked)
        assertEquals(12, score.firstTryRight)
        // 10 * 0.8 + 5 and 8 * 0.8 + 4
        assertEquals(13.0, score.askedDecayed, 1e-9)
        assertEquals(10.4, score.rightDecayed, 1e-9)
        assertEquals(2, score.rounds)

        val withNewType = DrillScores.recordRound(updated, "conjugation", asked = 6, firstTryRight = 6)
        val newScore = withNewType["conjugation"]!!
        assertEquals(6, newScore.asked)
        assertEquals(6, newScore.firstTryRight)
        assertEquals(6.0, newScore.askedDecayed, 1e-9)
        assertEquals(6.0, newScore.rightDecayed, 1e-9)
        assertEquals(1, newScore.rounds)
        assertEquals(score, withNewType["prepositions"])
    }

    @Test
    fun `recordRound takes explicit round evidence for the decayed counts`() {
        val initial = mapOf(
            "prepositions" to DrillScores.Score(asked = 10, firstTryRight = 8, askedDecayed = 10.0, rightDecayed = 8.0, rounds = 1)
        )

        val updated = DrillScores.recordRound(
            initial,
            "prepositions",
            asked = 10,
            firstTryRight = 6,
            askedEvidence = 10.0,
            rightEvidence = 8.0,
        )
        val score = updated["prepositions"]!!
        // Raw keys keep their cumulative meaning, decayed counts take the evidence.
        assertEquals(20, score.asked)
        assertEquals(14, score.firstTryRight)
        assertEquals(10.0 * DrillScores.DECAY + 10.0, score.askedDecayed, 1e-9)
        assertEquals(8.0 * DrillScores.DECAY + 8.0, score.rightDecayed, 1e-9)
    }

    @Test
    fun `recordRound ignores non-positive asked counts`() {
        val initial = mapOf(
            "prepositions" to DrillScores.Score(asked = 10, firstTryRight = 8)
        )
        val unchanged = DrillScores.recordRound(initial, "prepositions", asked = 0, firstTryRight = 0)
        assertEquals(initial, unchanged)
    }

    /** Records [rounds] rounds of [rightPerRound] right out of 10 asked, and returns the score. */
    private fun playRounds(rounds: Int, rightPerRound: Int): DrillScores.Score {
        var scores = emptyMap<String, DrillScores.Score>()
        repeat(rounds) {
            scores = DrillScores.recordRound(scores, "prepositions", asked = 10, firstTryRight = rightPerRound)
        }
        return scores["prepositions"]!!
    }

    @Test
    fun `worked example - 70 percent raw over 3, 10 and 50 rounds`() {
        val after3 = playRounds(3, 7)
        assertEquals(24.4, after3.askedDecayed, 0.05)
        assertEquals(17.1, after3.rightDecayed, 0.05)
        assertEquals(71, after3.smoothedPercentage)
        // The raw keys stay cumulative.
        assertEquals(30, after3.asked)
        assertEquals(21, after3.firstTryRight)

        val after10 = playRounds(10, 7)
        assertEquals(44.6, after10.askedDecayed, 0.05)
        assertEquals(31.2, after10.rightDecayed, 0.05)
        assertEquals(70, after10.smoothedPercentage)

        val after50 = playRounds(50, 7)
        assertEquals(50.0, after50.askedDecayed, 0.05)
        assertEquals(35.0, after50.rightDecayed, 0.05)
        assertEquals(70, after50.smoothedPercentage)
    }

    @Test
    fun `worked example - 10 of 10 every round over 3, 10 and 50 rounds`() {
        val after3 = playRounds(3, 10)
        assertEquals(24.4, after3.askedDecayed, 0.05)
        assertEquals(24.4, after3.rightDecayed, 0.05)
        assertEquals(96, after3.smoothedPercentage)

        val after10 = playRounds(10, 10)
        assertEquals(44.6, after10.askedDecayed, 0.05)
        assertEquals(44.6, after10.rightDecayed, 0.05)
        assertEquals(98, after10.smoothedPercentage)

        val after50 = playRounds(50, 10)
        assertEquals(50.0, after50.askedDecayed, 0.05)
        assertEquals(50.0, after50.rightDecayed, 0.05)
        assertEquals(98, after50.smoothedPercentage)
    }

    @Test
    fun `typed answers are the main evidence for types with a typed round`() {
        val choiceOnlyRight = DrillScores.roundEvidence(
            hasTypedRound = true,
            choiceAnswered = 10,
            choiceRight = 10,
            typedAnswered = 10,
            typedRight = 0,
        )
        val typedOnlyRight = DrillScores.roundEvidence(
            hasTypedRound = true,
            choiceAnswered = 10,
            choiceRight = 0,
            typedAnswered = 10,
            typedRight = 10,
        )

        // A full round still adds exactly the item count to the decayed asked count.
        assertEquals(10.0, choiceOnlyRight.askedDelta, 1e-9)
        assertEquals(10.0, typedOnlyRight.askedDelta, 1e-9)
        // The typed answer weighs twice the choice answer.
        assertEquals(10.0 / 3.0, choiceOnlyRight.rightDelta, 1e-9)
        assertEquals(20.0 / 3.0, typedOnlyRight.rightDelta, 1e-9)
    }

    @Test
    fun `choice-only types use choice results at full weight`() {
        val evidence = DrillScores.roundEvidence(hasTypedRound = false, choiceAnswered = 10, choiceRight = 7)

        assertEquals(10.0, evidence.askedDelta, 1e-9)
        assertEquals(7.0, evidence.rightDelta, 1e-9)
    }

    @Test
    fun `abandoned rounds count the answers already given`() {
        // Abandoned in the choice phase of a typed type: only the choice weight counts.
        val abandonedInChoice = DrillScores.abandonedRoundEvidence(
            hasTypedRound = true,
            choiceAnswered = 4,
            choiceRight = 3,
        )
        assertNotNull(abandonedInChoice)
        assertEquals(4.0 / 3.0, abandonedInChoice!!.askedDelta, 1e-9)
        assertEquals(3.0 / 3.0, abandonedInChoice.rightDelta, 1e-9)

        // Abandoned mid typed phase: the typed answers given so far carry the main weight.
        val abandonedInTyped = DrillScores.abandonedRoundEvidence(
            hasTypedRound = true,
            choiceAnswered = 10,
            choiceRight = 6,
            typedAnswered = 4,
            typedRight = 4,
        )
        assertNotNull(abandonedInTyped)
        assertEquals(10.0 / 3.0 + 4 * 2.0 / 3.0, abandonedInTyped!!.askedDelta, 1e-9)
        assertEquals(6.0 / 3.0 + 4 * 2.0 / 3.0, abandonedInTyped.rightDelta, 1e-9)
    }

    @Test
    fun `abandoned rounds below the minimum of three answers are not recorded`() {
        assertNull(DrillScores.abandonedRoundEvidence(hasTypedRound = true, choiceAnswered = 2, choiceRight = 2))
        assertNull(DrillScores.abandonedRoundEvidence(hasTypedRound = false, choiceAnswered = 0, choiceRight = 0))
    }

    @Test
    fun `round evidence by type splits a mixed round per type`() {
        val first = mapOf(
            DrillItemId(DrillType.PREPOSITIONS, "prep:a") to true,
            DrillItemId(DrillType.PREPOSITIONS, "prep:b") to false,
            DrillItemId(DrillType.ARTICLES, "art:c") to true,
            DrillItemId(DrillType.ARTICLES, "art:d") to true,
        )

        val byType = DrillScores.roundEvidenceByType(first, emptyMap(), abandoned = false)

        assertEquals(setOf(DrillType.PREPOSITIONS, DrillType.ARTICLES), byType.keys)
        val prep = byType[DrillType.PREPOSITIONS]!!
        val articles = byType[DrillType.ARTICLES]!!
        assertEquals(2, prep.asked)
        assertEquals(1, prep.firstTryRight)
        assertEquals(1, prep.rightAnswers)
        assertEquals(2.0, prep.evidence.askedDelta, 1e-9)
        assertEquals(1.0, prep.evidence.rightDelta, 1e-9)
        assertEquals(2, articles.asked)
        assertEquals(2, articles.firstTryRight)
        assertEquals(2.0, articles.evidence.askedDelta, 1e-9)
        assertEquals(2.0, articles.evidence.rightDelta, 1e-9)
    }

    @Test
    fun `typed answers stay on their own type in a mixed round`() {
        val typed = mapOf(
            DrillItemId(DrillType.PREPOSITIONS, "prep:a") to DrillResult.RIGHT,
            DrillItemId(DrillType.PREPOSITIONS, "prep:b") to DrillResult.RIGHT,
        )
        val first = mapOf(
            DrillItemId(DrillType.PREPOSITIONS, "prep:a") to true,
            DrillItemId(DrillType.PREPOSITIONS, "prep:b") to true,
            DrillItemId(DrillType.ARTICLES, "art:c") to false,
        )

        val byType = DrillScores.roundEvidenceByType(first, typed, abandoned = false)

        // Two typed rights on prepositions must not add anything to articles.
        val articles = byType[DrillType.ARTICLES]!!
        assertEquals(1, articles.asked)
        assertEquals(0, articles.firstTryRight)
        assertEquals(0, articles.rightAnswers)
        assertEquals(1.0, articles.evidence.askedDelta, 1e-9)
        assertEquals(0.0, articles.evidence.rightDelta, 1e-9)
        // The prepositions side carries the typed weight: 2 typed at 2/3 each.
        val prep = byType[DrillType.PREPOSITIONS]!!
        assertEquals(2, prep.asked)
        assertEquals(2 * 2.0 / 3.0, prep.evidence.askedDelta, 1e-9)
        assertEquals(2 * 2.0 / 3.0, prep.evidence.rightDelta, 1e-9)
    }

    @Test
    fun `typed answers on a choice-only type carry the typed weight`() {
        // The ladder graduates case-endings items into typed questions even though the
        // type used to be choice-only; their typed answers must count as evidence.
        val typed = mapOf(
            DrillItemId(DrillType.CASE_ENDINGS, "case:a") to DrillResult.RIGHT,
            DrillItemId(DrillType.CASE_ENDINGS, "case:b") to DrillResult.WRONG,
        )
        val first = mapOf(
            DrillItemId(DrillType.CASE_ENDINGS, "case:a") to true,
            DrillItemId(DrillType.CASE_ENDINGS, "case:b") to false,
        )

        val byType = DrillScores.roundEvidenceByType(first, typed, abandoned = false)
        val caseEndings = byType[DrillType.CASE_ENDINGS]!!

        assertEquals(2, caseEndings.asked)
        assertEquals(1, caseEndings.firstTryRight)
        assertEquals(1, caseEndings.rightAnswers)
        assertEquals(2 * 2.0 / 3.0, caseEndings.evidence.askedDelta, 1e-9)
        assertEquals(1 * 2.0 / 3.0, caseEndings.evidence.rightDelta, 1e-9)
    }

    @Test
    fun `an abandoned mixed round checks the three-answer threshold once for the round`() {
        val first = mapOf(
            DrillItemId(DrillType.PREPOSITIONS, "prep:a") to true,
            DrillItemId(DrillType.PREPOSITIONS, "prep:b") to false,
            DrillItemId(DrillType.ARTICLES, "art:c") to true,
            DrillItemId(DrillType.ARTICLES, "art:d") to false,
        )

        // Four answers across the round is enough for both types, though neither type
        // reaches three answers alone.
        val byType = DrillScores.roundEvidenceByType(first, emptyMap(), abandoned = true)
        assertEquals(setOf(DrillType.PREPOSITIONS, DrillType.ARTICLES), byType.keys)
        assertEquals(2, byType[DrillType.PREPOSITIONS]!!.asked)
        assertEquals(2, byType[DrillType.ARTICLES]!!.asked)

        // Below three answers for the whole round, nothing is recorded at all.
        val short = first - DrillItemId(DrillType.ARTICLES, "art:d") - DrillItemId(DrillType.ARTICLES, "art:c")
        assertTrue(DrillScores.roundEvidenceByType(short, emptyMap(), abandoned = true).isEmpty())
    }

    @Test
    fun `a number is shown only after about two rounds`() {
        // One round: no number, "1 round" is shown instead.
        assertNull(DrillScores.Score(10, 7, 10.0, 7.0, rounds = 1).displayPercentage)
        // Two rounds: the smoothed percentage, (7 + 3) / (10 + 4) = 71%.
        assertEquals(71, DrillScores.Score(10, 7, 10.0, 7.0, rounds = 2).displayPercentage)
        // Never played: no number either.
        assertNull(DrillScores.Score(0, 0).displayPercentage)
    }

    @Test
    fun `rankedWeakestFirst orders scored types by smoothed score`() {
        val weak = DrillScores.Score(asked = 10, firstTryRight = 2, askedDecayed = 10.0, rightDecayed = 2.0, rounds = 3)
        val mid = DrillScores.Score(asked = 10, firstTryRight = 7, askedDecayed = 10.0, rightDecayed = 7.0, rounds = 3)
        val strong = DrillScores.Score(asked = 10, firstTryRight = 10, askedDecayed = 10.0, rightDecayed = 10.0, rounds = 3)
        val scores = mapOf(
            DrillType.PREPOSITIONS.id to strong,
            DrillType.PRONOUNS.id to mid,
            DrillType.WORD_ORDER.id to weak,
            // Never really played: not ranked.
            DrillType.ARTICLES.id to DrillScores.Score(0, 0),
        )
        val types = listOf(
            DrillType.PREPOSITIONS,
            DrillType.PRONOUNS,
            DrillType.ARTICLES,
            DrillType.WORD_ORDER,
            DrillType.PLURALS,
        )

        assertEquals(
            listOf(DrillType.WORD_ORDER, DrillType.PRONOUNS, DrillType.PREPOSITIONS),
            DrillScores.rankedWeakestFirst(types, scores),
        )
    }

    @Test
    fun `old stored data seeds its decayed counts and later rounds decay them`() {
        val raw = """{"prepositions": {"asked": 30, "firstTryRight": 21}}"""
        val seeded = DrillScores.decode(raw)["prepositions"]!!

        assertEquals(30.0, seeded.askedDecayed, 0.0)
        assertEquals(21.0, seeded.rightDecayed, 0.0)
        assertEquals(3, seeded.rounds)

        // The next round decays the seeded counts instead of starting from zero.
        val afterNext = DrillScores.recordRound(mapOf("prepositions" to seeded), "prepositions", asked = 10, firstTryRight = 7)
        val score = afterNext["prepositions"]!!
        assertEquals(30 * DrillScores.DECAY + 10, score.askedDecayed, 1e-9)
        assertEquals(21 * DrillScores.DECAY + 7, score.rightDecayed, 1e-9)
        assertEquals(4, score.rounds)
    }
}
