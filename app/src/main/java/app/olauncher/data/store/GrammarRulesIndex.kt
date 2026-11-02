package app.olauncher.data.store

import java.util.Locale

/**
 * Title search of the grammar-rules index (#792, docs/design/german-hidden-surfaces.md
 * §4.1): which of [DrillHelpContent]'s rules a query finds. The index screen lists
 * [DrillHelpContent.allRules] in authored order and narrows it as the user types;
 * this object holds the matching so it is testable without a Context.
 *
 * - Case does not matter.
 * - Every whitespace-separated term of the query must appear in the rule's title,
 *   in any order ("passive werden" finds "The passive: the helper verb").
 * - A blank query keeps every rule, in the authored order it was given.
 */
object GrammarRulesIndex {

    private val whitespace = Regex("\\s+")

    /** The rules whose title matches [query], keeping the given order. */
    fun filter(rules: List<DrillHelpRule>, query: String): List<DrillHelpRule> {
        val terms = queryTerms(query)
        if (terms.isEmpty()) return rules
        return rules.filter { rule -> terms.all { it in fold(rule.title) } }
    }

    /** Whether [query] would find [title]; a blank query finds everything. */
    fun matches(title: String, query: String): Boolean {
        val terms = queryTerms(query)
        return terms.isEmpty() || terms.all { it in fold(title) }
    }

    private fun queryTerms(query: String): List<String> =
        fold(query).split(whitespace).filter { it.isNotEmpty() }

    private fun fold(text: String): String = text.trim().lowercase(Locale.ROOT)
}
