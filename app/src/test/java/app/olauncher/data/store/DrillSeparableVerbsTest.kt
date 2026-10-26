package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillSeparableVerbsTest {

    @Test
    fun allItemsPassValidation() {
        val items = DrillSeparableVerbs.items()
        assertTrue("Expected at least 100 items, found ${items.size}", items.size >= 100)
        for (item in items) {
            val check = item.validate()
            assertTrue(
                "Item ${item.id} failed validation: ${check.problems.joinToString()}",
                check.isValid,
            )
            assertTrue(
                "Item prompt must contain gap marker: ${item.prompt}",
                DrillItem.GAP_MARKER in item.prompt,
            )
        }
    }

    @Test
    fun tableContainsAtLeast40VerbsWithAllRequiredPrefixes() {
        val table = DrillSeparableVerbs.TABLE
        assertTrue("Table must contain at least 40 verbs, found ${table.size}", table.size >= 40)

        // Separable prefixes (an-, auf-, aus-, ein-, mit-, vor-, zu-, zurück-, fern-)
        val requiredSeparablePrefixes = listOf("an", "auf", "aus", "ein", "mit", "vor", "zu", "zurück", "fern")
        for (prefix in requiredSeparablePrefixes) {
            assertTrue(
                "Table must contain separable prefix '$prefix'",
                table.any { it.prefix == prefix && it.isSeparable },
            )
        }

        // Inseparable controls (be-, ver-, er-, ent-, zer-, ge-, emp-, miss-)
        val requiredInseparablePrefixes = listOf("be", "ver", "er", "ent", "zer", "ge", "emp", "miss")
        for (prefix in requiredInseparablePrefixes) {
            assertTrue(
                "Table must contain inseparable control prefix '$prefix'",
                table.any { it.prefix == prefix && !it.isSeparable },
            )
        }

        // Dual prefixes (über-, um-, unter-, durch-, wieder-)
        val requiredDualPrefixes = listOf("über", "um", "unter", "durch", "wieder")
        for (prefix in requiredDualPrefixes) {
            val matching = table.filter { it.prefix == prefix }
            assertTrue(
                "Table must contain dual prefix '$prefix'",
                matching.isNotEmpty(),
            )
            // Meaning fixed in the hint for all dual prefixes
            for (entry in matching) {
                assertTrue("Dual prefix '${entry.infinitive}' must have non-blank meaning", entry.meaning.isNotBlank())
            }
        }
    }

    @Test
    fun threeItemKindsRepresentedWithOneGapEach() {
        val items = DrillSeparableVerbs.items()

        // Kind 1: trennbar or untrennbar
        val kind1 = items.filter { it.choices == listOf("trennbar", "untrennbar") }
        assertTrue("Kind 1 (trennbar/untrennbar) items must be present", kind1.isNotEmpty())
        for (item in kind1) {
            assertTrue("Answer must be 'trennbar' or 'untrennbar'", item.answer in listOf("trennbar", "untrennbar"))
            assertTrue("Prompt must contain one gap", DrillItem.GAP_MARKER in item.prompt)
        }

        // Kind 2: Partizip II (e.g. angerufen against distractors like geanrufen)
        val kind2 = items.filter { it.prompt.startsWith("Partizip II von") }
        assertTrue("Kind 2 (Partizip II) items must be present", kind2.isNotEmpty())
        for (item in kind2) {
            assertEquals("Kind 2 items must offer 4 choices", 4, item.choices.size)
            assertTrue("Answer must be in choices", item.answer in item.choices)
            assertTrue("Prompt must contain one gap", DrillItem.GAP_MARKER in item.prompt)
        }

        val anrufenParticiple = kind2.firstOrNull { it.prompt.contains("anrufen") }
        assertNotNull("Must include Partizip II for anrufen", anrufenParticiple)
        assertEquals("angerufen", anrufenParticiple?.answer)
        assertTrue(
            "Choices for angerufen must include 'geanrufen' distractor",
            anrufenParticiple?.choices?.contains("geanrufen") == true,
        )

        // Kind 3: prefix at the end of a main clause
        val kind3 = items.filter {
            it.choices != listOf("trennbar", "untrennbar") && !it.prompt.startsWith("Partizip II von")
        }
        assertTrue("Kind 3 (clause prefix) items must be present", kind3.isNotEmpty())
        for (item in kind3) {
            assertEquals("Kind 3 items must offer 4 choices", 4, item.choices.size)
            assertTrue("Answer must be in choices", item.answer in item.choices)
            assertTrue("Prompt must contain one gap", DrillItem.GAP_MARKER in item.prompt)
        }
    }

    @Test
    fun kind1HintsNeverStateSeparabilityInAnyForm() {
        // Saved separable verb found through the present-form lookup
        val savedWords = listOf(
            Word(id = 101L, text = "anrufen", dueDay = 0L, addedAt = 0L),
        )
        val presentLookup: (String) -> String? = { verb ->
            if (verb == "anrufen") "ruft an" else null
        }

        val items = DrillSeparableVerbs.items(savedWords, presentLookup) + DrillSeparableVerbs.items()
        val kind1 = items.filter { it.choices == listOf("trennbar", "untrennbar") }
        assertTrue("Kind 1 (trennbar/untrennbar) items must be present", kind1.isNotEmpty())
        for (item in kind1) {
            val hint = item.hint ?: continue
            assertFalse(
                "Kind 1 hint must not state separability in any form: '$hint'",
                hint.lowercase().contains("trennbar"),
            )
        }
    }

    @Test
    fun phoneCheckAnrufenPrefixAtEndOfClause() {
        // "Ich rufe dich morgen ___." accepts "an"
        val items = DrillSeparableVerbs.items()
        val anrufenItem = items.firstOrNull { it.prompt == "Ich rufe dich morgen ${DrillItem.GAP_MARKER}." }
        assertNotNull("Phone check prompt must be present", anrufenItem)
        assertEquals("an", anrufenItem?.answer)
        assertTrue("Choices must include 'an'", anrufenItem?.choices?.contains("an") == true)
        assertEquals(4, anrufenItem?.choices?.size)
    }

    @Test
    fun savedVerbWithPresentSpaceCountsAsSeparableBesuchtDoesNot() {
        val savedWords = listOf(
            Word(id = 101L, text = "anrufen", dueDay = 0L, addedAt = 0L),
            Word(id = 202L, text = "besuchen", dueDay = 0L, addedAt = 0L),
        )

        val presentLookup: (String) -> String? = { verb ->
            when (verb) {
                "anrufen" -> "ruft an"
                "besuchen" -> "besucht"
                else -> null
            }
        }

        val items = DrillSeparableVerbs.items(savedWords, presentLookup)

        // "ruft an" contains a space -> counts as separable and gives items keyed by word id 101
        val anrufenItems = items.filter { it.id.key == "101" }
        assertTrue("anrufen must give items keyed by word id 101", anrufenItems.isNotEmpty())
        for (item in anrufenItems) {
            assertEquals("101", item.id.key)
        }

        // "besucht" does NOT contain a space -> does NOT count as separable and gives NO items keyed by word id 202
        val besuchenItemsWithWordId = items.filter { it.id.key == "202" }
        assertTrue("besuchen must NOT give items keyed by word id 202", besuchenItemsWithWordId.isEmpty())

        // Table items for besuchen should remain under table keys
        val besuchenTableItems = items.filter { it.id.key.startsWith("sep:besuchen:") }
        assertTrue("besuchen table items must keep table keys", besuchenTableItems.isNotEmpty())
    }

    @Test
    fun drillTablesAvailableIncludesSeparableVerbs() {
        val available = DrillTables.available()
        assertTrue("DrillTables.available() must include SEPARABLE_VERBS", DrillType.SEPARABLE_VERBS in available)
    }

    @Test
    fun drillTablesLabelResReturnsCorrectString() {
        assertEquals(R.string.drill_type_separable_verbs, DrillTables.labelRes(DrillType.SEPARABLE_VERBS))
    }

    @Test
    fun drillTablesItemsDelegatesToSeparableVerbs() {
        val directItems = DrillSeparableVerbs.items()
        val tableItems = DrillTables.items(DrillType.SEPARABLE_VERBS)
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
