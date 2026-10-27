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
            "passive" to DrillType.PASSIVE,
            "konjunktiv_ii" to DrillType.KONJUNKTIV_II,
            "relative_pronouns" to DrillType.RELATIVE_PRONOUNS,
            "genitive" to DrillType.GENITIVE,
            "n_declension" to DrillType.N_DECLENSION,
            "imperative" to DrillType.IMPERATIVE,
            "werden" to DrillType.WERDEN,
            "praeteritum" to DrillType.PRAETERITUM,
            "infinitiv_zu" to DrillType.INFINITIV_ZU,
            "modal_verbs" to DrillType.MODAL_VERBS,
            "konjunktiv_i" to DrillType.KONJUNKTIV_I,
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

    @Test
    fun roundShapePerTypeFlagsMatchSpecification() {
        val expectedChoiceOnly = setOf(
            DrillType.CASE_ENDINGS,
            DrillType.ADJECTIVE_ENDINGS,
            DrillType.WORD_ORDER,
        )

        val expectedTyped = setOf(
            DrillType.PREPOSITIONS,
            DrillType.PRONOUNS,
            DrillType.ARTICLES,
            DrillType.PLURALS,
            DrillType.VERB_PREPOSITIONS,
            DrillType.SEPARABLE_VERBS,
        )

        for (type in expectedChoiceOnly) {
            assertTrue("Expected $type to be choice-only", type.isChoiceOnly)
            assertFalse("Expected $type not to have typed round", type.hasTypedRound)
            assertEquals("Expected $type roundShape to be CHOICE_ONLY", DrillRoundShape.CHOICE_ONLY, type.roundShape)
            assertTrue("DrillTables.isChoiceOnly mismatch for $type", DrillTables.isChoiceOnly(type))
            assertFalse("DrillTables.hasTypedRound mismatch for $type", DrillTables.hasTypedRound(type))
            assertEquals(DrillRoundShape.CHOICE_ONLY, DrillTables.roundShape(type))
        }

        for (type in expectedTyped) {
            assertFalse("Expected $type to not be choice-only", type.isChoiceOnly)
            assertTrue("Expected $type to have typed round", type.hasTypedRound)
            assertEquals("Expected $type roundShape to be CHOICE_THEN_TYPED", DrillRoundShape.CHOICE_THEN_TYPED, type.roundShape)
            assertFalse("DrillTables.isChoiceOnly mismatch for $type", DrillTables.isChoiceOnly(type))
            assertTrue("DrillTables.hasTypedRound mismatch for $type", DrillTables.hasTypedRound(type))
            assertEquals(DrillRoundShape.CHOICE_THEN_TYPED, DrillTables.roundShape(type))
        }
    }

    @Test
    fun selectRoundTakesTenShuffledItemsOrAllIfFewer() {
        val items15 = (1..15).map { "item$it" }
        val round15 = DrillTables.selectRound(items15, limit = 10, random = java.util.Random(123))
        assertEquals(10, round15.size)
        assertEquals(10, round15.distinct().size)
        assertTrue(items15.containsAll(round15))

        val items4 = (1..4).map { "item$it" }
        val round4 = DrillTables.selectRound(items4, limit = 10, random = java.util.Random(123))
        assertEquals(4, round4.size)
        assertEquals(items4.toSet(), round4.toSet())

        val items10 = (1..10).map { "item$it" }
        val round10 = DrillTables.selectRound(items10, limit = 10, random = java.util.Random(123))
        assertEquals(10, round10.size)
        assertEquals(items10.toSet(), round10.toSet())

        val empty = emptyList<String>()
        val roundEmpty = DrillTables.selectRound(empty, limit = 10)
        assertTrue(roundEmpty.isEmpty())
    }

    @Test
    fun roundItemsLimitsToTenItems() {
        val prepositionRound = DrillTables.roundItems(DrillType.PREPOSITIONS)
        assertEquals(10, prepositionRound.size)

        val caseEndingsRound = DrillTables.roundItems(DrillType.CASE_ENDINGS)
        assertEquals(10, caseEndingsRound.size)

        val wordOrderRound = DrillTables.roundWordOrderItems()
        assertEquals(10, wordOrderRound.size)
    }
}
