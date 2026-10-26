package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillVerbPrepositionsTest {

    @Test
    fun allItemsPassValidation() {
        val items = DrillVerbPrepositions.items()
        assertTrue("Expected at least 100 items (>= 50 pairs * 2), found ${items.size}", items.size >= 100)
        for (item in items) {
            val check = item.validate()
            assertTrue(
                "Item ${item.id} failed validation: ${check.problems.joinToString()}",
                check.isValid,
            )
        }
    }

    @Test
    fun tableContainsAtLeast50PairsWithRequiredExamples() {
        val pairs = DrillVerbPrepositions.PAIRS
        assertTrue("Expected at least 50 pairs, found ${pairs.size}", pairs.size >= 50)

        // Check required named pairs from acceptance criteria
        val wartenAuf = pairs.firstOrNull { it.verb == "warten" && it.preposition == "auf" }
        assertNotNull("Must include 'warten auf'", wartenAuf)
        assertEquals("Akkusativ", wartenAuf?.case)

        val denkenAn = pairs.firstOrNull { it.verb == "denken" && it.preposition == "an" }
        assertNotNull("Must include 'denken an'", denkenAn)
        assertEquals("Akkusativ", denkenAn?.case)

        val interessierenFuer = pairs.firstOrNull {
            DrillVerbPrepositions.normalizeVerb(it.verb) == "interessieren" && it.preposition == "für"
        }
        assertNotNull("Must include 'sich interessieren für'", interessierenFuer)
        assertEquals("Akkusativ", interessierenFuer?.case)

        val teilnehmenAn = pairs.firstOrNull { it.verb == "teilnehmen" && it.preposition == "an" }
        assertNotNull("Must include 'teilnehmen an'", teilnehmenAn)
        assertEquals("Dativ", teilnehmenAn?.case)

        val angstHabenVor = pairs.firstOrNull {
            it.verb.equals("Angst haben", ignoreCase = true) && it.preposition == "vor"
        }
        assertNotNull("Must include 'Angst haben vor'", angstHabenVor)
        assertEquals("Dativ", angstHabenVor?.case)

        // Verify each pair structure
        for (pair in pairs) {
            assertTrue("Verb cannot be blank", pair.verb.isNotBlank())
            assertTrue("Preposition cannot be blank", pair.preposition.isNotBlank())
            assertTrue("Case must be Akkusativ or Dativ (found ${pair.case})", pair.case in listOf("Akkusativ", "Dativ"))
            assertTrue("Sentence must contain GAP_MARKER", DrillItem.GAP_MARKER in pair.sentence)
            assertEquals("Choices must contain exactly 4 options", 4, pair.choices.size)
            assertEquals("Choices must have no duplicates", 4, pair.choices.distinct().size)
            assertTrue("Preposition answer must be in choices", pair.preposition in pair.choices)
        }
    }

    @Test
    fun tableContainsBothItemKinds() {
        val items = DrillVerbPrepositions.items()
        val pairCount = DrillVerbPrepositions.PAIRS.size

        // Kind 1: Preposition in gap sentence
        val gapItems = items.filter { DrillItem.GAP_MARKER in it.prompt }
        assertEquals("Each pair must produce one gap sentence item", pairCount, gapItems.size)
        for (item in gapItems) {
            assertEquals("Gap item must have 4 preposition choices", 4, item.choices.size)
            assertTrue("Preposition answer must be in choices", item.answer in item.choices)
        }

        // Kind 2: Pick the case the pair takes
        val caseItems = items.filter { it.choices == listOf("Akkusativ", "Dativ") }
        assertEquals("Each pair must produce one case item", pairCount, caseItems.size)
        for (item in caseItems) {
            assertTrue("Case answer must be Akkusativ or Dativ", item.answer in listOf("Akkusativ", "Dativ"))
            assertTrue("Prompt must ask about case", item.prompt.startsWith("Welcher Fall"))
        }

        // Phone check verification: "Ich warte ___ den Bus." offers "auf" among 4 prepositions
        val busItem = gapItems.firstOrNull { it.prompt == "Ich warte ___ den Bus." }
        assertNotNull("Phone check gap sentence must be present", busItem)
        assertEquals("auf", busItem?.answer)
        assertEquals(4, busItem?.choices?.size)
        assertTrue("auf" in busItem!!.choices)
    }

    @Test
    fun keysUseTableFormatWhenNoMatchingSavedWord() {
        val items = DrillVerbPrepositions.items(emptyList())
        for (item in items) {
            val key = item.id.key
            assertTrue(
                "Key '$key' should match 'verbprep:<verb>:<prep>:<index>'",
                key.startsWith("verbprep:"),
            )
        }

        // Spot check specific table keys
        val wartenItems = items.filter { it.id.key.startsWith("verbprep:warten:auf:") }
        assertEquals(2, wartenItems.size)
        assertEquals("verbprep:warten:auf:0", wartenItems[0].id.key)
        assertEquals("verbprep:warten:auf:1", wartenItems[1].id.key)
    }

    @Test
    fun keysUseWordIdWhenMatchingSavedWordExists() {
        val savedWords = listOf(
            Word(id = 101L, text = "warten", dueDay = 0L, addedAt = 0L),
            Word(id = 202L, text = "  DENKEN  ", dueDay = 0L, addedAt = 0L), // trimmed and uppercase
            Word(id = 303L, text = "interessieren", dueDay = 0L, addedAt = 0L), // "sich " stripped
            Word(id = 404L, text = "sich freuen", dueDay = 0L, addedAt = 0L), // with "sich "
            Word(id = 505L, text = "angst haben", dueDay = 0L, addedAt = 0L), // multi-word verb phrase
            Word(id = 999L, text = "unrelated", dueDay = 0L, addedAt = 0L),
        )

        val items = DrillVerbPrepositions.items(savedWords)

        // 1. "warten" items should have key "101"
        val wartenItems = items.filter { it.hint?.startsWith("warten ") == true }
        assertTrue("Expected warten items", wartenItems.isNotEmpty())
        for (item in wartenItems) {
            assertEquals("101", item.id.key)
        }

        // 2. "  DENKEN  " items should have key "202"
        val denkenItems = items.filter { it.hint?.startsWith("denken ") == true }
        assertTrue("Expected denken items", denkenItems.isNotEmpty())
        for (item in denkenItems) {
            assertEquals("202", item.id.key)
        }

        // 3. "interessieren" (table has "sich interessieren") items should have key "303"
        val interessierenItems = items.filter { it.hint?.contains("interessieren") == true }
        assertTrue("Expected interessieren items", interessierenItems.isNotEmpty())
        for (item in interessierenItems) {
            assertEquals("303", item.id.key)
        }

        // 4. "sich freuen" items should have key "404"
        val freuenItems = items.filter { it.hint?.contains("freuen") == true }
        assertTrue("Expected freuen items", freuenItems.isNotEmpty())
        for (item in freuenItems) {
            assertEquals("404", item.id.key)
        }

        // 5. "angst haben" items should have key "505"
        val angstItems = items.filter { it.hint?.contains("Angst haben") == true }
        assertTrue("Expected angst haben items", angstItems.isNotEmpty())
        for (item in angstItems) {
            assertEquals("505", item.id.key)
        }

        // 6. Unmatched pairs should keep table format key
        val teilnehmenItems = items.filter { it.hint?.contains("teilnehmen") == true }
        assertTrue("Expected teilnehmen items", teilnehmenItems.isNotEmpty())
        for (item in teilnehmenItems) {
            assertTrue("Unmatched pair should keep table key", item.id.key.startsWith("verbprep:teilnehmen:"))
        }
    }

    @Test
    fun drillTablesAvailableListsVerbPrepositions() {
        val available = DrillTables.available()
        assertTrue("DrillTables.available() must include VERB_PREPOSITIONS", DrillType.VERB_PREPOSITIONS in available)
    }

    @Test
    fun drillTablesLabelResReturnsCorrectString() {
        assertEquals(R.string.drill_type_verb_prepositions, DrillTables.labelRes(DrillType.VERB_PREPOSITIONS))
    }

    @Test
    fun drillTablesItemsDelegatesToVerbPrepositions() {
        val directItems = DrillVerbPrepositions.items()
        val tableItems = DrillTables.items(DrillType.VERB_PREPOSITIONS)
        assertEquals(directItems.size, tableItems.size)
        for (i in directItems.indices) {
            assertEquals(directItems[i].id, tableItems[i].id)
            assertEquals(directItems[i].prompt, tableItems[i].prompt)
            assertEquals(directItems[i].answer, tableItems[i].answer)
            assertEquals(directItems[i].choices, tableItems[i].choices)
            assertEquals(directItems[i].hint, tableItems[i].hint)
        }
    }
}
