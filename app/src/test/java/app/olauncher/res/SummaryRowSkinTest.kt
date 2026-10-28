package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The shared summary-row skin (#623, owner decision 2026-10-28): one row layout and
 * mark styling serve both the drill round summary and the review summary, each with
 * its own thin adapter. These tests pin the shared skin and block per-feature forks.
 */
class SummaryRowSkinTest {

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

    private fun forEachElement(node: Node, action: (Element) -> Unit) {
        if (node.nodeType == Node.ELEMENT_NODE) {
            action(node as Element)
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            forEachElement(children.item(i), action)
        }
    }

    private fun findElementById(doc: Document, id: String): Element? {
        val target = id.removePrefix("@+id/").removePrefix("@id/")
        var found: Element? = null
        forEachElement(doc.documentElement) { el ->
            val elId = el.getAttribute("android:id").removePrefix("@+id/").removePrefix("@id/")
            if (elId == target) {
                found = el
            }
        }
        return found
    }

    @Test
    fun `the one shared row layout carries the mark slot and Today text styles`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_summary_row.xml"))

        val root = doc.documentElement
        assertEquals("@style/SummaryRow", root.getAttribute("style"))

        val mark = findElementById(doc, "ivMark")
        assertNotNull("shared row must have a mark slot (ivMark)", mark)

        val primary = findElementById(doc, "tvPrimary")
        assertNotNull(primary)
        assertEquals("@style/TodayCardBody", primary?.getAttribute("style"))

        val secondary = findElementById(doc, "tvSecondary")
        assertNotNull(secondary)
        assertEquals("@style/TodayCardBodyLight", secondary?.getAttribute("style"))
    }

    @Test
    fun `no per-feature row layouts fork the shared skin`() {
        // The pre-decision copies (item_drill_summary, item_review_summary) were
        // identical duplicates; recreate neither — the shared layout is the only row.
        assertFalse(File("app/src/main/res/layout/item_drill_summary.xml").exists())
        assertFalse(File("app/src/main/res/layout/item_review_summary.xml").exists())
        assertTrue(resolveFile("src/main/res/layout/item_summary_row.xml").isFile)
    }

    @Test
    fun `both summary screens pin a header above a scrollable summaryList`() {
        for (layout in listOf("fragment_drill.xml", "fragment_review.xml")) {
            val doc = parseXml(resolveFile("src/main/res/layout/$layout"))
            assertNotNull("$layout must keep the pinned summaryHeader", findElementById(doc, "summaryHeader"))
            assertNotNull("$layout must keep the scrollable summaryList", findElementById(doc, "summaryList"))
        }
    }
}
