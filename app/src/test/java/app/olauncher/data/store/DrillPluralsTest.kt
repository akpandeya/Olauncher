package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillPluralsTest {

    @Test
    fun allItemsPassValidation() {
        val items = DrillPlurals.items()
        assertTrue("Expected at least 50 items, found ${items.size}", items.size >= 50)
        for (item in items) {
            val check = item.validate()
            assertTrue(
                "Item ${item.id} failed validation: ${check.problems.joinToString()}",
                check.isValid,
            )
        }
    }

    @Test
    fun bundledTableHasAtLeast50Nouns() {
        assertTrue(
            "Bundled table must contain at least 50 nouns, found ${DrillPlurals.TABLE_ENTRIES.size}",
            DrillPlurals.TABLE_ENTRIES.size >= 50,
        )
    }

    @Test
    fun bundledTableCoversEveryPattern() {
        val patternsPresent = DrillPlurals.TABLE_ENTRIES.map { it.pattern }.toSet()
        val allPatterns = DrillPlurals.PluralPattern.values().toSet()

        assertEquals("Table must cover all 8 plural patterns", allPatterns, patternsPresent)

        for (pattern in allPatterns) {
            val count = DrillPlurals.TABLE_ENTRIES.count { it.pattern == pattern }
            assertTrue("Pattern $pattern must have at least 5 nouns in table, found $count", count >= 5)
        }
    }

    @Test
    fun itemShapeMatchesExpectedPattern() {
        val items = DrillPlurals.items().associateBy { it.id.key }

        val haus = items["plur:haus"]
        assertNotNull("das Haus should be present in bundled table items", haus)
        assertEquals("das Haus → die ___", haus?.prompt)
        assertEquals("Häuser", haus?.answer)

        val hund = items["plur:hund"]
        assertNotNull("der Hund should be present in bundled table items", hund)
        assertEquals("der Hund → die ___", hund?.prompt)
        assertEquals("Hunde", hund?.answer)

        val katze = items["plur:katze"]
        assertNotNull("die Katze should be present in bundled table items", katze)
        assertEquals("die Katze → die ___", katze?.prompt)
        assertEquals("Katzen", katze?.answer)
    }

    @Test
    fun choiceRoundOffers4FormsWithDistinctDistractorsNeverEqualingAnswer() {
        val items = DrillPlurals.items()
        for (item in items) {
            assertEquals("Item ${item.id} must offer exactly 4 choices", 4, item.choices.size)
            assertEquals("Choices for ${item.id} must not contain duplicates", 4, item.choices.distinct().size)
            assertTrue("Answer '${item.answer}' must be in choices for ${item.id}", item.answer in item.choices)

            val distractors = item.choices.filter { it != item.answer }
            assertEquals("Item ${item.id} must have exactly 3 distractors", 3, distractors.size)
            assertEquals("Distractors for ${item.id} must be distinct", 3, distractors.distinct().size)
            assertFalse(
                "Distractors for ${item.id} must never equal answer '${item.answer}'",
                distractors.contains(item.answer),
            )
        }
    }

    @Test
    fun distractorForHausIncludesHauser() {
        val haus = DrillPlurals.items().firstOrNull { it.id.key == "plur:haus" }
        assertNotNull(haus)
        assertEquals("Häuser", haus?.answer)
        assertTrue(
            "Choices for 'das Haus' must include un-umlauted 'Hauser' as a distractor",
            haus?.choices?.contains("Hauser") == true,
        )
    }

    @Test
    fun savedNounsWithDictionaryPluralGiveItemsKeyedByWordId() {
        val savedWords = listOf(
            Word(id = 101L, text = "das Haus", dueDay = 1L, addedAt = 1000L),
            Word(id = 102L, text = "Hund", dueDay = 1L, addedAt = 1000L),
        )
        val lookupPlural: (String) -> String? = { word ->
            when (word) {
                "Haus" -> "Häuser"
                "Hund" -> "Hunde"
                else -> null
            }
        }
        val lookupGender: (String) -> String? = { word ->
            when (word) {
                "Hund" -> "der"
                else -> null
            }
        }

        val items = DrillPlurals.items(savedWords, pluralLookup = lookupPlural, genderLookup = lookupGender)
        val byId = items.associateBy { it.id.toString() }

        val haus = byId["plurals:101"]
        assertNotNull(haus)
        assertEquals("das Haus → die ___", haus?.prompt)
        assertEquals("Häuser", haus?.answer)
        assertEquals(4, haus?.choices?.size)
        assertTrue(haus?.choices?.contains("Häuser") == true)

        val hund = byId["plurals:102"]
        assertNotNull(hund)
        assertEquals("der Hund → die ___", hund?.prompt)
        assertEquals("Hunde", hund?.answer)
    }

    @Test
    fun savedNounsWithBlankOrDashPluralAreSkipped() {
        val savedWords = listOf(
            Word(id = 201L, text = "das Wasser", dueDay = 1L, addedAt = 1000L),
            Word(id = 202L, text = "das Gehen", dueDay = 1L, addedAt = 1000L),
            Word(id = 203L, text = "der Käse", dueDay = 1L, addedAt = 1000L),
            Word(id = 204L, text = "etwas", dueDay = 1L, addedAt = 1000L),
        )
        val lookup: (String) -> String? = { word ->
            when (word) {
                "Wasser" -> "-"
                "Gehen" -> ""
                "Käse" -> "   "
                else -> null
            }
        }

        val items = DrillPlurals.items(savedWords, pluralLookup = lookup)
        val savedItemIds = items.map { it.id.key }
        assertFalse(savedItemIds.contains("201"))
        assertFalse(savedItemIds.contains("202"))
        assertFalse(savedItemIds.contains("203"))
        assertFalse(savedItemIds.contains("204"))
    }

    @Test
    fun savedNounsDeduplicateMatchingTableNouns() {
        val savedWords = listOf(
            Word(id = 500L, text = "das Haus", dueDay = 1L, addedAt = 1000L),
        )
        val lookupPlural: (String) -> String? = { if (it == "Haus") "Häuser" else null }

        val items = DrillPlurals.items(savedWords, pluralLookup = lookupPlural)
        val hausItems = items.filter { it.prompt.equals("das Haus → die ___", ignoreCase = true) }

        assertEquals("Should only have one item for Haus", 1, hausItems.size)
        assertEquals("Item for Haus should use saved word ID", "500", hausItems[0].id.key)
        assertFalse("Table item plur:haus should not be present", items.any { it.id.key == "plur:haus" })
    }

    @Test
    fun drillTablesAvailableIncludesPlurals() {
        val available = DrillTables.available()
        assertTrue(DrillType.PLURALS in available)
    }

    @Test
    fun drillTablesLabelResForPlurals() {
        assertEquals(R.string.drill_type_plurals, DrillTables.labelRes(DrillType.PLURALS))
    }

    @Test
    fun drillTablesItemsDelegatesToPlurals() {
        val directItems = DrillPlurals.items()
        val tableItems = DrillTables.items(DrillType.PLURALS)
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
