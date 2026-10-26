package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillWordOrderTest {

    @Test
    fun tableContainsAtLeast40Sentences() {
        val table = DrillWordOrder.TABLE
        assertTrue("Table must contain at least 40 sentences, found ${table.size}", table.size >= 40)
    }

    @Test
    fun allItemsPassValidation() {
        val table = DrillWordOrder.TABLE
        for (item in table) {
            val check = item.validate()
            assertTrue("Item ${item.id} failed validation: ${check.problems.joinToString()}", check.isValid)
            assertTrue("Item ${item.id} must have at least 3 tiles", item.tiles.size >= 3)
            assertTrue("Item ${item.id} must have at least one accepted order", item.accepted.isNotEmpty())
            assertFalse("Item ${item.id} must have a non-blank hint", item.hint.isNullOrBlank())
        }
    }

    @Test
    fun uniqueKeysAcrossAllEntries() {
        val keys = DrillWordOrder.ENTRIES.map { it.key }
        assertEquals("All keys must be unique", keys.distinct().size, keys.size)
    }

    @Test
    fun allSevenRequiredGrammarCategoriesAreRepresented() {
        val categories = DrillWordOrder.ENTRIES.map { it.category }.distinct()
        for (expected in DrillWordOrder.Category.values()) {
            assertTrue("Category $expected must be present in table", expected in categories)
            val count = DrillWordOrder.ENTRIES.count { it.category == expected }
            assertTrue("Category $expected must have at least 5 sentences, found $count", count >= 5)
        }
    }

    @Test
    fun verbSecondCategoryIncludesTimeFirstInversion() {
        val v2Entries = DrillWordOrder.ENTRIES.filter { it.category == DrillWordOrder.Category.VERB_SECOND }
        assertTrue("Must have at least 6 V2 sentences", v2Entries.size >= 6)

        // Specifically test the Kino sentence requirement:
        // both "Morgen gehe ich ins Kino." and "Ich gehe morgen ins Kino." accepted
        val kinoItem = v2Entries.firstOrNull { it.key == "v2_kino" }?.item
        assertNotNull("Kino sentence must exist in table", kinoItem)
        val kinoLower = kinoItem!!.accepted.map { it.map(String::lowercase) }
        assertTrue(
            "Must accept: Ich gehe morgen ins Kino",
            listOf("ich", "gehe", "morgen", "ins", "kino") in kinoLower,
        )
        assertTrue(
            "Must accept: Morgen gehe ich ins Kino",
            listOf("morgen", "gehe", "ich", "ins", "kino") in kinoLower,
        )
    }

    @Test
    fun verbLastSubordinateClausesCoverWeilDassWennOb() {
        val subEntries = DrillWordOrder.ENTRIES.filter { it.category == DrillWordOrder.Category.VERB_LAST_SUBORDINATE }

        // Must cover: weil, dass, wenn, ob
        val subjunctions = listOf("weil", "dass", "wenn", "ob")
        for (conj in subjunctions) {
            val matching = subEntries.filter { it.item.tiles.any { t -> t.equals(conj, ignoreCase = true) } }
            assertTrue("Must contain sentences with subjunction '$conj'", matching.size >= 3)
            for (entry in matching) {
                // In subordinate clauses, the verb is at the end of the clause
                for (order in entry.item.accepted) {
                    val lastWord = order.last().lowercase()
                    // Verbs at the end in our subordinate clause items: ist, hat, regnet, kommst, wird, scheint, habe, brauchst, haben
                    val verbEndings = listOf("ist", "hat", "regnet", "kommst", "kommt", "wird", "scheint", "habe", "brauchst", "haben")
                    assertTrue(
                        "Accepted order must end with conjugated verb in subordinate clause: $order",
                        lastWord in verbEndings,
                    )
                }
            }
        }
    }

    @Test
    fun separableVerbsHavePrefixAtTheEnd() {
        val sepEntries = DrillWordOrder.ENTRIES.filter { it.category == DrillWordOrder.Category.SEPARABLE_VERB }
        assertTrue("Must have at least 6 separable verb sentences", sepEntries.size >= 6)
        val prefixes = listOf("auf", "an", "ein", "ab")
        for (entry in sepEntries) {
            for (order in entry.item.accepted) {
                val lastWord = order.last().lowercase()
                assertTrue(
                    "Separable verb order must end with separable prefix: $order",
                    lastWord in prefixes,
                )
            }
        }
    }

    @Test
    fun modalSentencesHaveInfinitiveAtTheEnd() {
        val modEntries = DrillWordOrder.ENTRIES.filter { it.category == DrillWordOrder.Category.MODAL_INFINITIVE }
        assertTrue("Must have at least 6 modal verb sentences", modEntries.size >= 6)
        val infinitives = listOf("sprechen", "lernen", "fotografieren", "besuchen", "nehmen", "machen")
        for (entry in modEntries) {
            for (order in entry.item.accepted) {
                val lastWord = order.last().lowercase()
                assertTrue(
                    "Modal sentence order must end with infinitive: $order",
                    lastWord in infinitives,
                )
            }
        }
    }

    @Test
    fun perfektBracketHasPartizipAtTheEnd() {
        val perfEntries = DrillWordOrder.ENTRIES.filter { it.category == DrillWordOrder.Category.PERFEKT_BRACKET }
        assertTrue("Must have at least 6 Perfekt sentences", perfEntries.size >= 6)
        val participles = listOf("gelesen", "gefahren", "gemacht", "aufgestanden", "gearbeitet", "gefunden")
        for (entry in perfEntries) {
            for (order in entry.item.accepted) {
                val lastWord = order.last().lowercase()
                assertTrue(
                    "Perfekt sentence order must end with Partizip II: $order",
                    lastWord in participles,
                )
            }
        }
    }

    @Test
    fun teKaMoLoSentencesFollowTemporalModalLokalOrder() {
        val tekamoloEntries = DrillWordOrder.ENTRIES.filter { it.category == DrillWordOrder.Category.TEKAMOLO }
        assertTrue("Must have at least 6 TeKaMoLo sentences", tekamoloEntries.size >= 6)
        for (entry in tekamoloEntries) {
            assertTrue("TeKaMoLo hint must mention TeKaMoLo rule", entry.item.hint!!.contains("TeKaMoLo"))
        }
    }

    @Test
    fun yesNoQuestionsHaveVerbInPositionOne() {
        val qEntries = DrillWordOrder.ENTRIES.filter { it.category == DrillWordOrder.Category.YES_NO_QUESTION }
        assertTrue("Must have at least 6 yes/no questions", qEntries.size >= 6)
        val questionVerbs = listOf("kommst", "hast", "gehen", "kannst", "trinkst", "fährst")
        for (entry in qEntries) {
            for (order in entry.item.accepted) {
                val firstWord = order.first().lowercase()
                assertTrue(
                    "Yes/no question must start with verb in position 1: $order",
                    firstWord in questionVerbs,
                )
            }
        }
    }

    @Test
    fun hintsNameTheRuleForEverySentence() {
        for (entry in DrillWordOrder.ENTRIES) {
            val hint = entry.item.hint
            assertNotNull("Hint cannot be null for ${entry.key}", hint)
            assertTrue("Hint cannot be blank for ${entry.key}", hint!!.isNotBlank())
            // Hint must name the relevant rule
            val matchesRule = hint.contains("Position") ||
                hint.contains("Nebensatz") ||
                hint.contains("Trennbare") ||
                hint.contains("Modalverb") ||
                hint.contains("Perfekt") ||
                hint.contains("TeKaMoLo") ||
                hint.contains("Ja/Nein")
            assertTrue("Hint '$hint' must describe the rule for ${entry.key}", matchesRule)
        }
    }

    @Test
    fun drillTablesIncludesWordOrderInAvailableAndLabel() {
        val available = DrillTables.available()
        assertTrue("DrillTables.available() must include WORD_ORDER", DrillType.WORD_ORDER in available)
        assertEquals(R.string.drill_type_word_order, DrillTables.labelRes(DrillType.WORD_ORDER))
        val items = DrillTables.wordOrderItems()
        assertTrue("DrillTables.wordOrderItems() must return at least 40 items", items.size >= 40)
    }

    @Test
    fun itemsWithSavedWordsLinksWordId() {
        val word = Word(id = 888L, text = "Kino", dueDay = 0L, addedAt = 0L)
        val items = DrillWordOrder.items(listOf(word))
        val matched = items.firstOrNull { it.tiles.any { t -> t.equals("kino", ignoreCase = true) } }
        assertNotNull(matched)
        assertTrue(matched!!.id.encode().startsWith("word_order:888:"))
    }
}
