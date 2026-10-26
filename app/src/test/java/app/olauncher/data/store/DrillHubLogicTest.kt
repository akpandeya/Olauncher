package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillHubLogicTest {

    private val types = listOf(
        DrillType.PREPOSITIONS,
        DrillType.PRONOUNS,
        DrillType.ARTICLES,
        DrillType.PLURALS,
        DrillType.CASE_ENDINGS,
        DrillType.WORD_ORDER,
    )

    /** A played type with the given decayed counts; asked equals rightDecayed's raw count. */
    private fun played(asked: Int, right: Int, rounds: Int = 3) = DrillScores.Score(
        asked = asked,
        firstTryRight = right,
        askedDecayed = asked.toDouble(),
        rightDecayed = right.toDouble(),
        rounds = rounds,
    )

    @Test
    fun `rows list scored types weakest first`() {
        val scores = mapOf(
            DrillType.PREPOSITIONS.id to played(10, 9),
            DrillType.PRONOUNS.id to played(10, 5),
            DrillType.WORD_ORDER.id to played(10, 7),
        )

        val ranked = DrillHubLogic.rows(types, scores).filterNot { it.neverPlayed }

        assertEquals(
            listOf(DrillType.PRONOUNS, DrillType.WORD_ORDER, DrillType.PREPOSITIONS),
            ranked.map { it.type },
        )
    }

    @Test
    fun `rows keep the table order between equal scores`() {
        val scores = mapOf(
            DrillType.PREPOSITIONS.id to played(10, 7),
            DrillType.PRONOUNS.id to played(10, 7),
        )

        val ranked = DrillHubLogic.rows(types, scores).filterNot { it.neverPlayed }

        assertEquals(
            listOf(DrillType.PREPOSITIONS, DrillType.PRONOUNS),
            ranked.map { it.type },
        )
    }

    @Test
    fun `never played types come after the ranked ones in table order`() {
        val scores = mapOf(
            DrillType.WORD_ORDER.id to played(10, 7),
        )

        val rows = DrillHubLogic.rows(types, scores)

        assertEquals(DrillType.WORD_ORDER, rows.first().type)
        assertEquals(
            listOf(DrillType.PREPOSITIONS, DrillType.PRONOUNS, DrillType.ARTICLES, DrillType.PLURALS, DrillType.CASE_ENDINGS),
            rows.drop(1).map { it.type },
        )
    }

    @Test
    fun `a zero-asked score entry counts as never played`() {
        val scores = mapOf(
            DrillType.CASE_ENDINGS.id to DrillScores.Score(0, 0),
        )

        val caseRow = DrillHubLogic.rows(types, scores).first { it.type == DrillType.CASE_ENDINGS }

        assertTrue(caseRow.neverPlayed)
        assertEquals(DrillHubStat.NeverPlayed, DrillHubLogic.stat(caseRow))
        assertEquals(0f, DrillHubLogic.barFraction(caseRow))
    }

    @Test
    fun `a row without a score entry is never played`() {
        val row = DrillHubLogic.rows(types, emptyMap()).first()

        assertTrue(row.neverPlayed)
        assertNull(row.score)
    }

    @Test
    fun `stat shows the smoothed percentage with the round count after about two rounds`() {
        // (14 + 3) / (20 + 4) = 70.8 -> 71 percent.
        val row = DrillHubRow(DrillType.PRONOUNS, played(20, 14, rounds = 2))

        assertEquals(DrillHubStat.Percentage(71, 2), DrillHubLogic.stat(row))
    }

    @Test
    fun `stat shows one round before a percentage is shown`() {
        val row = DrillHubRow(DrillType.PRONOUNS, played(10, 7, rounds = 1))

        assertEquals(DrillHubStat.OneRound, DrillHubLogic.stat(row))
    }

    @Test
    fun `stat shows nothing for a never played type`() {
        assertEquals(DrillHubStat.NeverPlayed, DrillHubLogic.stat(DrillHubRow(DrillType.ARTICLES, null)))
    }

    @Test
    fun `bar fraction is the smoothed score, clamped to the bar`() {
        // (7 + 3) / (10 + 4) = 0.714...
        val fraction = DrillHubLogic.barFraction(DrillHubRow(DrillType.PRONOUNS, played(10, 7)))

        assertEquals(10.0 / 14.0, fraction.toDouble(), 1e-6)
        // A perfect round tops out below 1, an empty one stays above 0.
        assertTrue(DrillHubLogic.barFraction(DrillHubRow(DrillType.PRONOUNS, played(10, 10))) < 1f)
        assertTrue(DrillHubLogic.barFraction(DrillHubRow(DrillType.PRONOUNS, played(10, 0))) > 0f)
    }

    @Test
    fun `the new group label shows only when ranked and new rows both exist`() {
        val mixed = listOf(
            DrillHubRow(DrillType.PRONOUNS, played(10, 5)),
            DrillHubRow(DrillType.ARTICLES, null),
        )
        val allNew = listOf(DrillHubRow(DrillType.ARTICLES, null))
        val allRanked = listOf(DrillHubRow(DrillType.PRONOUNS, played(10, 5)))

        assertTrue(DrillHubLogic.showsNewGroupLabel(mixed))
        assertFalse(DrillHubLogic.showsNewGroupLabel(allNew))
        assertFalse(DrillHubLogic.showsNewGroupLabel(allRanked))
    }

    @Test
    fun `continue prefers the last type when it is still available`() {
        assertEquals(DrillType.PRONOUNS, DrillHubLogic.continueType(types, DrillType.PRONOUNS.id))
        assertEquals(DrillType.WORD_ORDER, DrillHubLogic.continueType(types, DrillType.WORD_ORDER.id))
    }

    @Test
    fun `continue falls back to the first type when the last type is unknown or unavailable`() {
        assertEquals(DrillType.PREPOSITIONS, DrillHubLogic.continueType(types, null))
        assertEquals(DrillType.PREPOSITIONS, DrillHubLogic.continueType(types, "unknown"))
        // A valid type that is not in the available list.
        assertEquals(DrillType.PREPOSITIONS, DrillHubLogic.continueType(types, DrillType.CONJUGATION.id))
    }

    @Test
    fun `continue needs at least one available type`() {
        assertNull(DrillHubLogic.continueType(emptyList(), DrillType.PRONOUNS.id))
    }

    @Test
    fun `practise weakest is the lowest-ranked type that has a score`() {
        val scores = mapOf(
            DrillType.PREPOSITIONS.id to played(10, 9),
            DrillType.PRONOUNS.id to played(10, 5),
            DrillType.WORD_ORDER.id to played(10, 7),
        )

        assertEquals(DrillType.PRONOUNS, DrillHubLogic.weakestType(types, scores, DrillType.PREPOSITIONS.id))
    }

    @Test
    fun `practise weakest falls back to the last type when no type has a score yet`() {
        assertEquals(DrillType.ARTICLES, DrillHubLogic.weakestType(types, emptyMap(), DrillType.ARTICLES.id))
        // Scores that never crossed the asked threshold do not rank either.
        val zeroScores = mapOf(DrillType.CASE_ENDINGS.id to DrillScores.Score(0, 0))
        assertEquals(DrillType.WORD_ORDER, DrillHubLogic.weakestType(types, zeroScores, DrillType.WORD_ORDER.id))
    }
}
