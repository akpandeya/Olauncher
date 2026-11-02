package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

/**
 * The bundled further-reading map (#810, docs/design/grammar-further-reading.md):
 * network-free checks only. Every link is https on an explicitly allowed
 * domain that was opened and read by hand during implementation; sources, URLs
 * and angles are non-blank; no URL repeats within a topic; and every key is a
 * real [GrammarRuleGroups] key so the rule detail can always resolve a rule's
 * group. No test here opens a connection — the manual link-health script
 * (scripts/check-further-reading-links.sh) is the only thing that fetches.
 */
class GrammarFurtherReadingTest {

    /** Domains hand-checked during implementation (see the design doc for dates). */
    private val allowedHosts = setOf(
        "deutsch.lingolia.com",
    )

    private fun allLinks(): List<Pair<String, GrammarFurtherReadingLink>> =
        GrammarFurtherReading.byGroup.flatMap { (key, links) -> links.map { key to it } }

    @Test
    fun everyLinkIsHttpsOnAnAllowedDomain() {
        for ((key, link) in allLinks()) {
            val uri = assertNotNullAndParse(link.url, key)
            assertEquals(
                "Link of $key must be https: ${link.url}",
                "https",
                uri.scheme,
            )
            assertTrue(
                "Link host ${uri.host} of $key is not on the allowed-domain list: ${link.url}",
                uri.host in allowedHosts,
            )
        }
    }

    @Test
    fun noBlankSourceUrlOrAngle() {
        for ((key, link) in allLinks()) {
            assertTrue("Blank source in $key", link.source.isNotBlank())
            assertTrue("Blank url in $key", link.url.isNotBlank())
            assertTrue("Blank angle in $key", link.angle.isNotBlank())
        }
    }

    @Test
    fun noDuplicateUrlWithinATopic() {
        for ((key, links) in GrammarFurtherReading.byGroup) {
            assertEquals(
                "Duplicate URL within topic $key",
                links.size,
                links.map { it.url }.toSet().size,
            )
        }
    }

    @Test
    fun everyKeyIsARealGroupKey() {
        val groupKeys = GrammarRuleGroups.ordered.map { it.key }.toSet()
        for (key in GrammarFurtherReading.byGroup.keys) {
            assertTrue(
                "\"$key\" is not a GrammarRuleGroups key",
                key in groupKeys,
            )
        }
    }

    @Test
    fun topicsWithoutLinksSimplyHaveNoEntry() {
        // The map covers a subset of the group keys; no key maps to an empty
        // list — a topic with no good source has no entry instead.
        for ((key, links) in GrammarFurtherReading.byGroup) {
            assertTrue("Topic $key has an empty link list", links.isNotEmpty())
        }
    }

    @Test
    fun forRuleResolvesTheOwningGroup() {
        // Wiring check with distinguishing values: the group that owns the
        // rule must be the one whose links come back, not another group's.
        val keysWithLinks = GrammarRuleGroups.ordered
            .map { it.key }
            .filter { GrammarFurtherReading.byGroup.containsKey(it) }
        assertTrue(keysWithLinks.size >= 2)
        val firstKey = keysWithLinks[0]
        val secondKey = keysWithLinks[1]
        val firstGroup = GrammarRuleGroups.ordered.first { it.key == firstKey }
        val secondGroup = GrammarRuleGroups.ordered.first { it.key == secondKey }

        assertEquals(
            GrammarFurtherReading.byGroup.getValue(firstKey),
            GrammarFurtherReading.forRule(firstGroup.ruleIds.first()),
        )
        assertEquals(
            GrammarFurtherReading.byGroup.getValue(secondKey),
            GrammarFurtherReading.forRule(secondGroup.ruleIds.first()),
        )
        assertTrue(
            "Two groups' link lists must differ, otherwise the wiring check is vacuous",
            GrammarFurtherReading.byGroup.getValue(firstKey) != GrammarFurtherReading.byGroup.getValue(secondKey),
        )
        // A rule id that exists in no group gets no links.
        assertEquals(emptyList<GrammarFurtherReadingLink>(), GrammarFurtherReading.forRule("no-such-rule"))
    }

    private fun assertNotNullAndParse(url: String, key: String): URI {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw AssertionError("Unparseable URL in $key: $url", e)
        }
        assertNotNull(uri)
        return uri
    }
}
