package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grammar-rules index title search (#792): what a typed query finds in
 * [DrillHelpContent]'s rules, and what it keeps out. Checked against the real
 * registry so a query finds the rules a user can actually open.
 */
class GrammarRulesIndexTest {

    private fun rule(id: String, title: String) = DrillHelpRule(
        id = id,
        title = title,
        rule = "rule text",
        table = DrillHelpTable(headers = listOf("", "m"), rows = listOf(DrillHelpRow("Nom", listOf("der")))),
        example = "Ein Beispiel.",
        cue = "cue",
    )

    @Test
    fun blankQueryKeepsEveryRuleInAuthoredOrder() {
        val rules = listOf(rule("a", "Alpha"), rule("b", "Beta"))
        assertEquals(rules, GrammarRulesIndex.filter(rules, ""))
        assertEquals(rules, GrammarRulesIndex.filter(rules, "   "))
        // The real registry keeps its full authored length on a blank query.
        assertEquals(
            DrillHelpContent.allRules().size,
            GrammarRulesIndex.filter(DrillHelpContent.allRules(), "").size,
        )
    }

    @Test
    fun everyTermMustMatchTheTitleInAnyOrder() {
        val rules = listOf(
            rule("passive", "The passive: the helper verb"),
            rule("werden", "The verb werden in the present"),
        )
        // Both terms match one title, in either order.
        assertEquals(
            listOf(rules[0]),
            GrammarRulesIndex.filter(rules, "passive helper"),
        )
        assertEquals(
            listOf(rules[0]),
            GrammarRulesIndex.filter(rules, "helper passive"),
        )
        // Terms split across two titles match neither.
        assertTrue(GrammarRulesIndex.filter(rules, "passive present").isEmpty())
    }

    @Test
    fun matchingIsCaseInsensitiveAndSubstringBased() {
        val rules = listOf(rule("two-way", "Two-way prepositions"))
        assertEquals(rules, GrammarRulesIndex.filter(rules, "two-way"))
        assertEquals(rules, GrammarRulesIndex.filter(rules, "PREPOSITIONS"))
        assertEquals(rules, GrammarRulesIndex.filter(rules, "way"))
        assertTrue(GrammarRulesIndex.filter(rules, "conjugation").isEmpty())
    }

    @Test
    fun matchesAgreesWithFilterOnTheRealRegistry() {
        val all = DrillHelpContent.allRules()
        assertTrue(all.size > 50)
        // A real title from the registry is found by fragments of itself.
        val twoWay = all.single { it.id == "prep-two-way" }
        assertTrue(GrammarRulesIndex.matches(twoWay.title, "two-way"))
        assertTrue(GrammarRulesIndex.matches(twoWay.title, "Two Way"))
        assertFalse(GrammarRulesIndex.matches(twoWay.title, "imperative"))
        // Every filtered rule's title really contains every term — a count alone
        // proves nothing.
        for (query in listOf("passive", "the", "verb preposition")) {
            for (found in GrammarRulesIndex.filter(all, query)) {
                val terms = query.trim().lowercase().split(Regex("\\s+"))
                assertTrue(
                    "$query matched ${found.id} but its title \"${found.title}\" misses a term",
                    terms.all { it in found.title.lowercase() },
                )
            }
        }
    }
}
