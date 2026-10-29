package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillCaseEndingsTest {

    @Test
    fun itemsContainAtLeast48Items() {
        val items = DrillCaseEndings.items()
        assertTrue("Expected at least 48 items, found ${items.size}", items.size >= 48)
    }

    @Test
    fun everyItemPassesValidate() {
        val items = DrillCaseEndings.items()
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
    fun itemIdsAreUnique() {
        val items = DrillCaseEndings.items()
        val distinctIds = items.map { it.id }.distinct()
        assertEquals("Each item must have a unique DrillItemId", items.size, distinctIds.size)
    }

    @Test
    fun promptMatchesDriverExampleIchSeheHundGivesDen() {
        val items = DrillCaseEndings.items()
        val sehenHund = items.firstOrNull { it.prompt == "Ich sehe ${DrillItem.GAP_MARKER} Hund." }
        assertNotNull("Must include 'Ich sehe ___ Hund.' item", sehenHund)
        assertEquals("den", sehenHund?.answer)
        assertTrue("Choices must include 'den'", "den" in (sehenHund?.choices ?: emptyList()))
        assertEquals("sehen + Akkusativ", sehenHund?.hint)
    }

    @Test
    fun phoneCheckItemAfterMitGradesDemRight() {
        val items = DrillCaseEndings.items()
        val mitItem = items.firstOrNull { it.prompt.contains(" mit ${DrillItem.GAP_MARKER} ") }
        assertNotNull("Must include an item with preposition 'mit'", mitItem)
        assertEquals("dem", mitItem?.answer)
        assertTrue("Choices must contain 'dem'", "dem" in (mitItem?.choices ?: emptyList()))
        assertTrue("Hint must name mit and Dativ", mitItem?.hint?.contains("mit") == true && mitItem.hint?.contains("Dativ") == true)
    }

    @Test
    fun hintsNameCaseAndWhatTriggersItForEveryItem() {
        val items = DrillCaseEndings.items()
        val validCases = listOf("Nominativ", "Akkusativ", "Dativ", "Genitiv")

        for (item in items) {
            val hint = item.hint
            assertNotNull("Item ${item.id} must have a non-null hint", hint)
            assertTrue("Item ${item.id} hint must not be blank", hint!!.isNotBlank())

            val hasCase = validCases.any { it in hint }
            assertTrue("Hint '${hint}' for item ${item.id} must name one of the 4 cases", hasCase)

            val hasTrigger = hint.contains("+") || hint.contains(":") || hint.contains("(")
            assertTrue("Hint '${hint}' for item ${item.id} must name what triggers the case", hasTrigger)
        }
    }

    @Test
    fun allFourCasesAreCovered() {
        val items = DrillCaseEndings.items()
        val nomItems = items.filter { it.hint?.contains("Nominativ") == true }
        val akkItems = items.filter { it.hint?.contains("Akkusativ") == true }
        val datItems = items.filter { it.hint?.contains("Dativ") == true }
        val genItems = items.filter { it.hint?.contains("Genitiv") == true }

        assertTrue("Must have Nominative items", nomItems.isNotEmpty())
        assertTrue("Must have Accusative items", akkItems.isNotEmpty())
        assertTrue("Must have Dative items", datItems.isNotEmpty())
        assertTrue("Must have Genitive items", genItems.isNotEmpty())
    }

    @Test
    fun drillTablesAvailableIncludesCaseEndings() {
        val available = DrillTables.available()
        assertTrue("DrillTables.available() must include CASE_ENDINGS", DrillType.CASE_ENDINGS in available)
    }

    @Test
    fun drillTablesLabelResForCaseEndings() {
        assertEquals(R.string.drill_type_case_endings, DrillTables.labelRes(DrillType.CASE_ENDINGS))
    }

    @Test
    fun drillTablesItemsDelegatesToCaseEndings() {
        val directItems = DrillCaseEndings.items()
        val tableItems = DrillTables.items(DrillType.CASE_ENDINGS)
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
        val word = Word(id = 99L, text = "sehen", dueDay = 0L, addedAt = 0L)
        val items = DrillCaseEndings.items(listOf(word))
        val matched = items.firstOrNull { it.id.key.startsWith("99:") }
        assertNotNull("Matching trigger word should incorporate word id into item key", matched)
    }

    @Test
    fun builtInItemsCarryTheirAuthoredHelpRule() {
        val items = DrillCaseEndings.items().associateBy { it.id.key }
        assertEquals("case-subject-nom", items["case:nom_m_lehrer"]!!.ruleId)
        assertEquals("case-sein-nom", items["case:nom_f_loesung"]!!.ruleId)
        // Same dative trigger word, different rules: fixed preposition vs dative verb.
        assertEquals("prep-fixed-dat", items["case:dat_m_mit_bus"]!!.ruleId)
        assertEquals("case-verb-dat", items["case:dat_m_helfen_mann"]!!.ruleId)
        val trotz = items["case:gen_m_trotz_regens"]!!
        assertEquals("prep-fixed-gen", trotz.ruleId)
        assertEquals(
            "den is the accusative masculine (or dative plural) form. These prepositions " +
                "take the genitive, so des.",
            trotz.missByChoice["den"],
        )
        assertEquals(
            setOf("der", "dem"),
            trotz.missByChoice.keys - "den",
        )
    }

}
