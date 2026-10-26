package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillTest {

    @Test
    fun drillTypePinsAllIdsSoRenamesFailBuild() {
        val expected = mapOf(
            "prepositions" to DrillType.PREPOSITIONS,
            "pronouns" to DrillType.PRONOUNS,
            "articles" to DrillType.ARTICLES,
            "plurals" to DrillType.PLURALS,
            "case_endings" to DrillType.CASE_ENDINGS,
            "verb_prepositions" to DrillType.VERB_PREPOSITIONS,
            "separable_verbs" to DrillType.SEPARABLE_VERBS,
            "adjective_endings" to DrillType.ADJECTIVE_ENDINGS,
            "word_order" to DrillType.WORD_ORDER,
            "conjugation" to DrillType.CONJUGATION,
            "cloze" to DrillType.CLOZE,
        )
        assertEquals("DrillType entry count changed", expected.size, DrillType.values().size)
        for ((expectedId, expectedType) in expected) {
            assertEquals("Id mismatch for $expectedType", expectedId, expectedType.id)
            assertEquals("fromId mismatch for $expectedId", expectedType, DrillType.fromId(expectedId))
        }
    }

    @Test
    fun drillTypeFromIdReturnsNullForUnknownOrEmptyId() {
        assertNull(DrillType.fromId("unknown"))
        assertNull(DrillType.fromId("PREPOSITIONS"))
        assertNull(DrillType.fromId(""))
        assertNull(DrillType.fromId("   "))
    }

    @Test
    fun drillItemIdEncodesAsTypeIdAndKey() {
        val id = DrillItemId(DrillType.PREPOSITIONS, "mit_dativ")
        assertEquals("prepositions:mit_dativ", id.encode())
        assertEquals("prepositions:mit_dativ", id.toString())
    }

    @Test
    fun drillItemIdParseSucceedsForValidString() {
        val parsed = DrillItemId.parse("prepositions:mit")
        assertNotNull(parsed)
        assertEquals(DrillType.PREPOSITIONS, parsed!!.type)
        assertEquals("mit", parsed.key)
    }

    @Test
    fun drillItemIdParseHandlesKeyContainingColons() {
        val parsed = DrillItemId.parse("cloze:word:42:sentence")
        assertNotNull(parsed)
        assertEquals(DrillType.CLOZE, parsed!!.type)
        assertEquals("word:42:sentence", parsed.key)
    }

    @Test
    fun drillItemIdParseReturnsNullForMissingColon() {
        assertNull(DrillItemId.parse("prepositions"))
        assertNull(DrillItemId.parse("cloze"))
        assertNull(DrillItemId.parse(""))
    }

    @Test
    fun drillItemIdParseReturnsNullForUnknownType() {
        assertNull(DrillItemId.parse("unknown:key"))
        assertNull(DrillItemId.parse("PREPOSITIONS:key"))
        assertNull(DrillItemId.parse(":key"))
    }

    @Test
    fun drillItemIdParseReturnsNullForBlankKey() {
        assertNull(DrillItemId.parse("prepositions:"))
        assertNull(DrillItemId.parse("prepositions:   "))
        assertNull(DrillItemId.parse("prepositions:\t\n"))
    }

    @Test
    fun drillItemPromptGapMarkerIsTripleUnderscore() {
        assertEquals("___", DrillItem.GAP_MARKER)
        assertEquals("___", DRILL_GAP_MARKER)
    }

    @Test
    fun validateAcceptsValidItem() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "mit"),
            prompt = "Ich fahre ___ dem Bus.",
            answer = "mit",
            choices = listOf("mit", "bei", "zu"),
            hint = "Takes dative",
        )
        val check = item.validate()
        assertTrue(check.isValid)
        assertTrue(check.problems.isEmpty())
    }

    @Test
    fun validateRejectsBlankPrompt() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "1"),
            prompt = "   ",
            answer = "mit",
            choices = listOf("mit", "bei"),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "Prompt" in it })
    }

    @Test
    fun validateRejectsBlankAnswer() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "1"),
            prompt = "Ich fahre ___ dem Bus.",
            answer = "",
            choices = listOf("", "mit"),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "Answer" in it })
    }

    @Test
    fun validateRejectsAnswerNotInChoices() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "1"),
            prompt = "Ich fahre ___ dem Bus.",
            answer = "mit",
            choices = listOf("bei", "nach", "zu"),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "Answer must be one of the choices" in it })
    }

    @Test
    fun validateRejectsDuplicateChoices() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "1"),
            prompt = "Ich fahre ___ dem Bus.",
            answer = "mit",
            choices = listOf("mit", "bei", "mit"),
        )
        val check = item.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "duplicates" in it.lowercase() })
    }

    @Test
    fun validateRejectsFewerThanTwoChoices() {
        val singleChoice = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "1"),
            prompt = "Ich fahre ___ dem Bus.",
            answer = "mit",
            choices = listOf("mit"),
        )
        val checkSingle = singleChoice.validate()
        assertFalse(checkSingle.isValid)
        assertTrue(checkSingle.problems.any { "between 2 and 4" in it })

        val emptyChoices = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "1"),
            prompt = "Ich fahre ___ dem Bus.",
            answer = "mit",
            choices = emptyList(),
        )
        val checkEmpty = emptyChoices.validate()
        assertFalse(checkEmpty.isValid)
        assertTrue(checkEmpty.problems.any { "between 2 and 4" in it })
    }

    @Test
    fun validateRejectsMoreThanFourChoices() {
        val fiveChoices = DrillItem(
            id = DrillItemId(DrillType.PREPOSITIONS, "1"),
            prompt = "Ich fahre ___ dem Bus.",
            answer = "mit",
            choices = listOf("mit", "bei", "zu", "nach", "von"),
        )
        val check = fiveChoices.validate()
        assertFalse(check.isValid)
        assertTrue(check.problems.any { "between 2 and 4" in it })
    }
}
