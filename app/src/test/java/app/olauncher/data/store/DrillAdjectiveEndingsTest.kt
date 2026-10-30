package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillAdjectiveEndingsTest {

    @Test
    fun itemsContainAtLeast60Items() {
        val items = DrillAdjectiveEndings.items()
        assertTrue("Expected at least 60 items, found ${items.size}", items.size >= 60)
    }

    @Test
    fun everyItemPassesValidate() {
        val items = DrillAdjectiveEndings.items()
        for (item in items) {
            val check = item.validate()
            assertTrue("Item ${item.id} failed validation: ${check.problems}", check.isValid)
            assertTrue("Item ${item.id} prompt missing gap marker", item.prompt.contains(DrillItem.GAP_MARKER))
            assertTrue("Item ${item.id} answer must be in choices", item.answer in item.choices)
            assertEquals("Item ${item.id} choices must be distinct", item.choices.distinct().size, item.choices.size)
            assertTrue("Item ${item.id} choices count must be in 2..4", item.choices.size in 2..4)
        }
    }

    @Test
    fun choicesComeFromAllowedAdjectiveEndingsSet() {
        val items = DrillAdjectiveEndings.items()
        val allowedEndings = setOf("e", "en", "er", "es", "em")
        for (item in items) {
            assertTrue("Item ${item.id} answer '${item.answer}' must be in allowed set", item.answer in allowedEndings)
            for (choice in item.choices) {
                assertTrue("Item ${item.id} choice '$choice' must be in -e, -en, -er, -es, -em", choice in allowedEndings)
            }
        }
    }

    @Test
    fun itemIdsAreUnique() {
        val items = DrillAdjectiveEndings.items()
        val distinctIds = items.map { it.id }.distinct()
        assertEquals("Each item must have a unique DrillItemId", items.size, distinctIds.size)
    }

    @Test
    fun promptMatchesDriverExampleMitEinemKleinHundGivesEn() {
        val items = DrillAdjectiveEndings.items()
        val mitItem = items.firstOrNull { it.prompt == "mit einem klein${DrillItem.GAP_MARKER} Hund" }
        assertNotNull("Must include 'mit einem klein___ Hund' item", mitItem)
        assertEquals("en", mitItem?.answer)
        assertTrue("Choices must include 'en'", "en" in (mitItem?.choices ?: emptyList()))
        val hint = mitItem?.hint ?: ""
        assertTrue("Hint must name mixed declension", hint.contains("Gemischte Deklination") || hint.contains("gemischt", ignoreCase = true))
        assertTrue("Hint must name Dativ", hint.contains("Dativ"))
    }

    @Test
    fun hintsNameDeclensionTypeAndCaseForEveryItem() {
        val items = DrillAdjectiveEndings.items()
        val validCases = listOf("Nominativ", "Akkusativ", "Dativ", "Genitiv")
        val validDeclensions = listOf("Schwache Deklination", "Gemischte Deklination", "Starke Deklination", "schwach", "gemischt", "stark")

        for (item in items) {
            val hint = item.hint
            assertNotNull("Item ${item.id} must have a non-null hint", hint)
            assertTrue("Item ${item.id} hint must not be blank", hint!!.isNotBlank())

            val hasCase = validCases.any { it in hint }
            assertTrue("Hint '$hint' for item ${item.id} must name one of the 4 cases", hasCase)

            val hasDeclension = validDeclensions.any { it in hint }
            assertTrue("Hint '$hint' for item ${item.id} must name the declension type", hasDeclension)
        }
    }

    @Test
    fun allFourCasesAndBothDeclensionsAreCovered() {
        val items = DrillAdjectiveEndings.items()
        val nomItems = items.filter { it.hint?.contains("Nominativ") == true }
        val akkItems = items.filter { it.hint?.contains("Akkusativ") == true }
        val datItems = items.filter { it.hint?.contains("Dativ") == true }
        val genItems = items.filter { it.hint?.contains("Genitiv") == true }

        assertTrue("Must have Nominative items", nomItems.isNotEmpty())
        assertTrue("Must have Accusative items", akkItems.isNotEmpty())
        assertTrue("Must have Dative items", datItems.isNotEmpty())
        assertTrue("Must have Genitive items", genItems.isNotEmpty())

        val weakItems = items.filter { it.hint?.contains("Schwach", ignoreCase = true) == true }
        val mixedItems = items.filter { it.hint?.contains("Gemischt", ignoreCase = true) == true }

        assertTrue("Must have weak declension items", weakItems.isNotEmpty())
        assertTrue("Must have mixed declension items", mixedItems.isNotEmpty())
    }

    @Test
    fun drillTablesAvailableIncludesAdjectiveEndings() {
        val available = DrillTables.available()
        assertTrue("DrillTables.available() must include ADJECTIVE_ENDINGS", DrillType.ADJECTIVE_ENDINGS in available)
    }

    @Test
    fun drillTablesLabelResForAdjectiveEndings() {
        assertEquals(R.string.drill_type_adjective_endings, DrillTables.labelRes(DrillType.ADJECTIVE_ENDINGS))
    }

    @Test
    fun drillTablesItemsDelegatesToAdjectiveEndings() {
        val directItems = DrillAdjectiveEndings.items()
        val tableItems = DrillTables.items(DrillType.ADJECTIVE_ENDINGS)
        assertEquals(directItems.size, tableItems.size)
        for (i in directItems.indices) {
            assertEquals(directItems[i].id, tableItems[i].id)
            assertEquals(directItems[i].prompt, tableItems[i].prompt)
            assertEquals(directItems[i].answer, tableItems[i].answer)
            assertEquals(directItems[i].choices, tableItems[i].choices)
            assertEquals(directItems[i].hint, tableItems[i].hint)
        }
    }

    @Test
    fun savedWordsMatchingTriggerIncorporateWordId() {
        val word = Word(id = 88L, text = "mit", dueDay = 0L, addedAt = 0L)
        val items = DrillAdjectiveEndings.items(listOf(word))
        val matched = items.firstOrNull { it.id.key.startsWith("88:") }
        assertNotNull("Matching trigger word should incorporate word id into item key", matched)
    }

    @Test
    fun builtInItemsMatchTheAuthoredRuleAssignment() {
        val built = DrillAdjectiveEndings.items().associate { it.id.key to it.ruleId!! }
        val authored = ADJECTIVE_ENDING_RULE_ITEMS.entries.associate { (key, ruleId) ->
            "adj:$key" to ruleId
        }
        // Both directions: every item carries the rule its content file assigns, and
        // every authored item is a built-in one.
        assertEquals(authored, built)
    }

    @Test
    fun missLinesExplainEveryWrongChoice() {
        for (item in DrillAdjectiveEndings.items()) {
            assertEquals(
                "Item ${item.id}: miss lines must cover exactly the wrong choices",
                (item.choices - item.answer).toSet(),
                item.missByChoice.keys,
            )
            assertTrue(
                "Item ${item.id}: every miss line must be filled in",
                item.missByChoice.values.all { it.isNotBlank() },
            )
        }
    }

    @Test
    fun missExplainsTheWrongEndingInTheItemOwnGroup() {
        val mitItem = DrillAdjectiveEndings.items().firstOrNull {
            it.prompt == "mit einem klein${DrillItem.GAP_MARKER} Hund"
        }
        assertNotNull("Expected the 'mit einem klein___ Hund' item", mitItem)
        assertEquals("adj-mixed-dat", mitItem?.ruleId)
        assertEquals(
            "-e follows eine and the nominative or accusative die. einem, einer and keinen here " +
                "show the dative, so the adjective takes -en.",
            mitItem?.missByChoice?.getValue("e"),
        )
    }

    @Test
    fun savedWordItemsCarryNoRuleId() {
        val word = Word(id = 88L, text = "mit", dueDay = 0L, addedAt = 0L)
        val items = DrillAdjectiveEndings.items(listOf(word))
        val matched = items.filter { it.id.key.startsWith("88:") }
        assertTrue("Expected saved-word items for mit", matched.isNotEmpty())
        for (item in matched) {
            assertNull("Saved-word item ${item.id} must carry no rule id", item.ruleId)
            assertTrue("Saved-word item ${item.id} must carry no miss lines", item.missByChoice.isEmpty())
        }
    }
}
