package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillPrepositionsTest {

    @Test
    fun allItemsPassValidation() {
        val items = DrillPrepositions.items()
        assertTrue("Expected at least 40 items, found ${items.size}", items.size >= 40)
        for (item in items) {
            val check = item.validate()
            assertTrue(
                "Item ${item.id} failed validation: ${check.problems.joinToString()}",
                check.isValid,
            )
        }
    }

    @Test
    fun tableContainsAllRequiredPrepositions() {
        val items = DrillPrepositions.items()
        val accusative = listOf("durch", "für", "gegen", "ohne", "um", "bis", "entlang")
        val dative = listOf("aus", "bei", "mit", "nach", "seit", "von", "zu", "gegenüber")
        val twoWay = listOf("an", "auf", "hinter", "in", "neben", "über", "unter", "vor", "zwischen")
        val genitive = listOf("wegen", "trotz", "während", "statt")

        val allExpected = accusative + dative + twoWay + genitive

        val entryPrepositions = DrillPrepositions.ENTRIES.map { it.preposition }
        for (prep in allExpected) {
            assertTrue("Missing preposition entry: $prep", prep in entryPrepositions)
        }

        // Check each two-way preposition has a location (Dativ) and a direction (Akkusativ) sentence
        for (prep in twoWay) {
            val entry = DrillPrepositions.ENTRIES.first { it.preposition == prep }
            val hints = entry.templates.mapNotNull { it.hint }
            assertTrue(
                "Preposition $prep must have a location Dativ item",
                hints.any { "Dativ" in it || "Wo?" in it },
            )
            assertTrue(
                "Preposition $prep must have a direction Akkusativ item",
                hints.any { "Akkusativ" in it || "Wohin?" in it },
            )
        }
    }

    @Test
    fun tableContainsBothItemKinds() {
        val items = DrillPrepositions.items()

        // "which case" items with choices Akkusativ / Dativ / Genitiv
        val whichCaseItems = items.filter { item ->
            item.choices == listOf("Akkusativ", "Dativ", "Genitiv")
        }
        assertTrue("Expected which-case items, found ${whichCaseItems.size}", whichCaseItems.isNotEmpty())

        // "article form" in gap sentences with "___" marker
        val gapItems = items.filter { item ->
            DrillItem.GAP_MARKER in item.prompt
        }
        assertTrue("Expected gap sentences with marker, found ${gapItems.size}", gapItems.isNotEmpty())
    }

    @Test
    fun keysUseTableFormatWhenNoMatchingSavedWord() {
        val items = DrillPrepositions.items(emptyList())
        for (item in items) {
            val key = item.id.key
            assertTrue(
                "Key '$key' should match 'prep:<preposition>:<n>'",
                key.matches(Regex("prep:[a-zA-ZäöüÄÖÜß]+:\\d+")),
            )
        }

        // Spot check specific prepositions
        val durchItems = items.filter { it.id.key.startsWith("prep:durch:") }
        assertEquals(2, durchItems.size)
        assertEquals("prep:durch:0", durchItems[0].id.key)
        assertEquals("prep:durch:1", durchItems[1].id.key)

        val anItems = items.filter { it.id.key.startsWith("prep:an:") }
        assertEquals(2, anItems.size)
        assertEquals("prep:an:0", anItems[0].id.key)
        assertEquals("prep:an:1", anItems[1].id.key)
    }

    @Test
    fun keysUseWordIdWhenMatchingSavedWordExists() {
        val savedWords = listOf(
            Word(id = 101L, text = "durch", dueDay = 0L, addedAt = 0L),
            Word(id = 202L, text = "AN", dueDay = 0L, addedAt = 0L), // case-insensitive
            Word(id = 303L, text = "unrelated", dueDay = 0L, addedAt = 0L),
        )

        val items = DrillPrepositions.items(savedWords)

        // "durch" items should have key "101"
        val durchItems = items.filter { it.prompt.contains("durch") || it.hint?.contains("durch") == true }
        assertTrue("Expected durch items", durchItems.isNotEmpty())
        for (item in durchItems) {
            assertEquals("101", item.id.key)
        }

        // "an" items should have key "202"
        val anEntry = DrillPrepositions.ENTRIES.first { it.preposition == "an" }
        val anPrompts = anEntry.templates.map { it.prompt }
        val anItems = items.filter { it.prompt in anPrompts }
        assertEquals(2, anItems.size)
        for (item in anItems) {
            assertEquals("202", item.id.key)
        }

        // Other prepositions (e.g. "für") without saved word should still use "prep:für:<n>"
        val fuerItems = items.filter { it.id.key.startsWith("prep:für:") }
        assertEquals(2, fuerItems.size)
        assertEquals("prep:für:0", fuerItems[0].id.key)
        assertEquals("prep:für:1", fuerItems[1].id.key)
    }

    @Test
    fun drillTablesAvailableListsTypesWithContent() {
        val available = DrillTables.available()
        assertTrue(DrillType.PREPOSITIONS in available)
    }

    @Test
    fun drillTablesItemsDelegatesToPrepositions() {
        val directItems = DrillPrepositions.items()
        val tableItems = DrillTables.items(DrillType.PREPOSITIONS)
        assertEquals(directItems.size, tableItems.size)
        for (i in directItems.indices) {
            assertEquals(directItems[i].id, tableItems[i].id)
            assertEquals(directItems[i].prompt, tableItems[i].prompt)
            assertEquals(directItems[i].answer, tableItems[i].answer)
            assertEquals(directItems[i].choices, tableItems[i].choices)
        }

        // Unsupported types return empty list
        assertTrue(DrillTables.items(DrillType.CONJUGATION).isEmpty())
    }

    @Test
    fun builtInItemsCarryTheirAuthoredHelpRule() {
        val items = DrillPrepositions.items().associateBy { it.id.key }
        // Which-case items: the answer is the case name, so the miss line names the case.
        assertEquals("prep-which-fixed", items["prep:bis:0"]!!.ruleId)
        assertEquals(
            setOf("Dativ", "Genitiv"),
            items["prep:bis:0"]!!.missByChoice.keys,
        )
        assertEquals(
            "this preposition never takes the Dativ; it always takes the Akkusativ.",
            items["prep:bis:0"]!!.missByChoice["Dativ"],
        )
        // Gap items: two-way, fixed accusative, fixed genitive.
        assertEquals("prep-two-way", items["prep:an:0"]!!.ruleId)
        assertEquals(
            "the movement (Wohin?) form. hängen describes a fixed position (Wo?), so der.",
            items["prep:an:0"]!!.missByChoice["die"],
        )
        assertEquals("prep-fixed-acc", items["prep:für:1"]!!.ruleId)
        assertEquals(
            "feminine dative or masculine nominative. The noun is masculine, so den.",
            items["prep:für:1"]!!.missByChoice["der"],
        )
        assertEquals("prep-fixed-dat", items["prep:von:1"]!!.ruleId)
        assertEquals("prep-fixed-gen", items["prep:während:1"]!!.ruleId)
    }

}
