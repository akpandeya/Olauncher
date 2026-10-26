package app.olauncher.data.store

import org.junit.Assert.assertEquals
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
        assertEquals(DrillScores.Score(10, 8), scores["prepositions"])
        assertEquals(DrillScores.Score(5, 4), scores["pronouns"])
    }

    @Test
    fun `encode and decode round-trips accurately`() {
        val original = mapOf(
            "prepositions" to DrillScores.Score(asked = 15, firstTryRight = 12),
            "conjugation" to DrillScores.Score(asked = 6, firstTryRight = 5),
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
    fun `recordRound increments existing scores and creates new entries`() {
        val initial = mapOf(
            "prepositions" to DrillScores.Score(asked = 10, firstTryRight = 8)
        )

        val updated = DrillScores.recordRound(initial, "prepositions", asked = 5, firstTryRight = 4)
        assertEquals(DrillScores.Score(asked = 15, firstTryRight = 12), updated["prepositions"])

        val withNewType = DrillScores.recordRound(updated, "conjugation", asked = 6, firstTryRight = 6)
        assertEquals(DrillScores.Score(asked = 15, firstTryRight = 12), withNewType["prepositions"])
        assertEquals(DrillScores.Score(asked = 6, firstTryRight = 6), withNewType["conjugation"])
    }

    @Test
    fun `recordRound ignores non-positive asked counts`() {
        val initial = mapOf(
            "prepositions" to DrillScores.Score(asked = 10, firstTryRight = 8)
        )
        val unchanged = DrillScores.recordRound(initial, "prepositions", asked = 0, firstTryRight = 0)
        assertEquals(initial, unchanged)
    }
}
