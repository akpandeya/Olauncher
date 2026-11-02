package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The grammar-rules index groups (#809, docs/design/german-hidden-surfaces.md
 * §4.1): the map covers every registry rule exactly once, its keys are stable
 * and labelled, and the grouped filter keeps the group and rule order while
 * dropping the groups a query finds nothing in.
 */
class GrammarRuleGroupsTest {

    private fun rule(id: String, title: String) = DrillHelpRule(
        id = id,
        title = title,
        rule = "rule text",
        table = DrillHelpTable(headers = listOf("", "m"), rows = listOf(DrillHelpRow("Nom", listOf("der")))),
        example = "Ein Beispiel.",
        cue = "cue",
    )

    @Test
    fun groupedIdsCoverTheRegistryInBothDirections() {
        val registry = DrillHelpContent.ids()
        val grouped = GrammarRuleGroups.ordered.flatMap { it.ruleIds }.toSet()
        assertEquals(
            "Grouped ids missing from the registry: ${grouped - registry}",
            registry,
            grouped,
        )
        assertEquals(
            "Registry ids not grouped: ${registry - grouped}",
            grouped,
            registry,
        )
    }

    @Test
    fun noRuleIdAppearsTwiceWithinOrAcrossGroups() {
        val all = GrammarRuleGroups.ordered.flatMap { it.ruleIds }
        assertEquals(all.size, all.toSet().size)
        for (group in GrammarRuleGroups.ordered) {
            assertEquals("Duplicate id in group ${group.key}", group.ruleIds.size, group.ruleIds.toSet().size)
        }
    }

    @Test
    fun groupKeysAreUniqueLowercaseAndDescriptive() {
        val keys = GrammarRuleGroups.ordered.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (key in keys) {
            assertTrue(
                "Group key \"$key\" must be lowercase snake_case so `grammar_rules_group_\$key` is a valid resource name",
                Regex("[a-z][a-z0-9_]*").matches(key),
            )
        }
        assertTrue(GrammarRuleGroups.ordered.isNotEmpty())
    }

    @Test
    fun everyGroupKeyHasALabelThatIsNoCaseName() {
        val doc = parseXml(resolveFile("src/main/res/values/strings.xml"))
        val strings = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            strings[el.getAttribute("name")] = el.textContent.trim()
        }

        val caseNames = setOf(
            "nominative", "nominativ", "accusative", "akkusativ",
            "dative", "dativ", "genitive", "genitiv",
            "nom", "akk", "dat", "gen",
        )
        for (group in GrammarRuleGroups.ordered) {
            val label = strings["grammar_rules_group_${group.key}"]
            assertNotNull("strings.xml must define grammar_rules_group_${group.key}", label)
            assertFalse(
                "Label \"${group.key}\" must not be blank",
                label!!.isBlank(),
            )
            assertFalse(
                "Label \"$label\" of group ${group.key} must be a topic name, never a case name — the case is the answer of the which-case items",
                label.trim().lowercase() in caseNames,
            )
        }
    }

    @Test
    fun groupedFilterKeepsGroupAndRuleOrderAndDropsEmptyGroups() {
        val rules = listOf(
            rule("a1", "Alpha one"),
            rule("a2", "Alpha two"),
            rule("b1", "Beta one"),
            rule("c1", "Gamma one"),
        )
        val groups = listOf(
            GrammarRuleGroup("alpha", listOf("a2", "a1")),
            GrammarRuleGroup("beta", listOf("b1")),
            GrammarRuleGroup("gamma", listOf("c1")),
        )

        // A blank query keeps every rule: the groups' order, then each group's
        // own rule order (a2 before a1, as listed).
        assertEquals(
            listOf("alpha", "beta", "gamma"),
            GrammarRulesIndex.filterGrouped(groups, rules, "").map { it.group.key },
        )
        assertEquals(
            listOf("a2", "a1", "b1", "c1"),
            GrammarRulesIndex.filterGrouped(groups, rules, "").flatMap { it.rules }.map { it.id },
        )

        // A query keeps the group order and the matched rule order: "one" drops
        // a2 ("Alpha two" has no "one") but keeps all three groups.
        val filtered = GrammarRulesIndex.filterGrouped(groups, rules, "one")
        assertEquals(listOf("alpha", "beta", "gamma"), filtered.map { it.group.key })
        assertEquals(listOf("a1", "b1", "c1"), filtered.flatMap { it.rules }.map { it.id })

        // A query that leaves a group empty drops it whole: "alpha one" matches
        // only a1, so beta and gamma disappear.
        val narrowed = GrammarRulesIndex.filterGrouped(groups, rules, "alpha one")
        assertEquals(listOf("alpha"), narrowed.map { it.group.key })
        assertEquals(listOf("a1"), narrowed.flatMap { it.rules }.map { it.id })

        // A query matching nothing drops every group.
        assertTrue(GrammarRulesIndex.filterGrouped(groups, rules, "delta").isEmpty())
    }

    @Test
    fun groupedFilterAgreesWithTheFlatFilterOnTheRealRegistry() {
        val all = DrillHelpContent.allRules()
        for (query in listOf("", "   ", "the", "prepositions", "passive werden", "zzz-no-match")) {
            val groupedIds = GrammarRulesIndex
                .filterGrouped(GrammarRuleGroups.ordered, all, query)
                .flatMap { it.rules }
                .map { it.id }
            assertEquals(
                "Query \"$query\": grouped and flat search must find the same rules",
                GrammarRulesIndex.filter(all, query).map { it.id }.sorted(),
                groupedIds.sorted(),
            )
        }
    }

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("File $relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }
}
