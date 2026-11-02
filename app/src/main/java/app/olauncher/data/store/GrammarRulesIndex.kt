package app.olauncher.data.store

import java.util.Locale

/**
 * One rendered group of the index (#809): the topic [GrammarRuleGroup] and the
 * rules of it that the query finds, in the group's rule order.
 */
data class GrammarRuleSection(
    val group: GrammarRuleGroup,
    val rules: List<DrillHelpRule>,
)

/**
 * Title search of the grammar-rules index (#792, docs/design/german-hidden-surfaces.md
 * §4.1): which of [DrillHelpContent]'s rules a query finds. The index screen shows
 * [DrillHelpContent.allRules] grouped by [GrammarRuleGroups] in a suggested learning
 * order and narrows the groups as the user types; this object holds the matching so
 * it is testable without a Context.
 *
 * - Case does not matter.
 * - Every whitespace-separated term of the query must appear in the rule's title,
 *   in any order ("passive werden" finds "The passive: the helper verb").
 * - A blank query keeps every rule: [filter] in the order it was given,
 *   [filterGrouped] in the groups' learning order with each group's rule order.
 */
object GrammarRulesIndex {

    private val whitespace = Regex("\\s+")

    /** The rules whose title matches [query], keeping the given order. */
    fun filter(rules: List<DrillHelpRule>, query: String): List<DrillHelpRule> {
        val terms = queryTerms(query)
        if (terms.isEmpty()) return rules
        return rules.filter { rule -> terms.all { it in fold(rule.title) } }
    }

    /**
     * The [groups] in their order, each with the rules whose title matches [query],
     * in the group's own rule order. A blank query keeps every rule of every group;
     * a group whose rules all miss the query is dropped whole (#809).
     */
    fun filterGrouped(
        groups: List<GrammarRuleGroup>,
        rules: List<DrillHelpRule>,
        query: String,
    ): List<GrammarRuleSection> {
        val terms = queryTerms(query)
        val ruleById = rules.associateBy { it.id }
        return groups.mapNotNull { group ->
            val matched = group.ruleIds.mapNotNull { ruleById[it] }
                .filter { rule -> terms.all { it in fold(rule.title) } }
            if (matched.isEmpty()) null else GrammarRuleSection(group, matched)
        }
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
