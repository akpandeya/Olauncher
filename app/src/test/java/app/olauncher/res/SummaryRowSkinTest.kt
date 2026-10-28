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

    /**
     * Gradle runs unit tests with the working directory at `app/`, Android Studio
     * sometimes at the repo root — try both. If neither exists we FAIL LOUDLY on
     * purpose: a silent skip would let the skin drift without anyone noticing.
     */
    private fun moduleFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.exists() } ?: error(
            "Module file $relative not found from ${File(".").absolutePath} " +
                "(tried both the working directory and the repo root)"
        )
    }

    private val layoutDir: File by lazy {
        listOf(
            File("src/main/res/layout"),
            File("app/src/main/res/layout"),
        ).firstOrNull { it.isDirectory } ?: error(
            "res/layout not found from ${File(".").absolutePath} " +
                "(tried both the working directory and the repo root)"
        )
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

    private fun isDescendantOf(node: Element, ancestor: Element): Boolean {
        var parent = node.parentNode
        while (parent is Element) {
            if (parent === ancestor) return true
            parent = parent.parentNode
        }
        return false
    }

    @Test
    fun `the one shared row layout carries the mark slot and Today text styles`() {
        val doc = parseXml(moduleFile("src/main/res/layout/item_summary_row.xml"))

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
    fun `the mark slot keeps its pinned size and position`() {
        val doc = parseXml(moduleFile("src/main/res/layout/item_summary_row.xml"))
        val mark = checkNotNull(findElementById(doc, "ivMark")) { "shared row must have a mark slot (ivMark)" }

        assertEquals("22dp", mark.getAttribute("android:layout_width"))
        assertEquals("22dp", mark.getAttribute("android:layout_height"))
        assertEquals("top", mark.getAttribute("android:layout_gravity"))
        assertEquals("2dp", mark.getAttribute("android:layout_marginTop"))
        assertEquals("12dp", mark.getAttribute("android:layout_marginEnd"))
    }

    @Test
    fun `the correction line keeps its 2dp gap and stays gone by default`() {
        val doc = parseXml(moduleFile("src/main/res/layout/item_summary_row.xml"))
        val secondary = checkNotNull(findElementById(doc, "tvSecondary")) { "shared row must have a correction line (tvSecondary)" }

        assertEquals("2dp", secondary.getAttribute("android:layout_marginTop"))
        assertEquals("gone", secondary.getAttribute("android:visibility"))
    }

    @Test
    fun `both summary screens inflate the one shared row binding`() {
        for (source in listOf(
            "src/main/java/app/olauncher/ui/words/DrillFragment.kt",
            "src/main/java/app/olauncher/ui/words/ReviewFragment.kt",
        )) {
            val text = moduleFile(source).readText()
            assertTrue("$source must inflate the shared ItemSummaryRowBinding", text.contains("ItemSummaryRowBinding"))
            assertFalse(
                "$source must not inflate a per-feature row binding",
                text.contains("ItemDrillSummaryBinding") || text.contains("ItemReviewSummaryBinding"),
            )
        }
    }

    @Test
    fun `no per-feature row layouts fork the shared skin`() {
        // The pre-decision copies (item_drill_summary, item_review_summary) were
        // identical duplicates; recreate neither — the shared layout is the only row
        // that pairs a mark slot with primary text. A new fork (say,
        // item_review_row.xml) fails this test even under a fresh name.
        val layouts = layoutDir.listFiles { file -> file.isFile && file.name.endsWith(".xml") }.orEmpty()
        assertTrue(
            "res/layout must be readable and non-empty from ${layoutDir.absolutePath}",
            layouts.isNotEmpty(),
        )
        val forks = layouts
            .filter { it.name != SHARED_ROW_LAYOUT }
            .filter { file ->
                val doc = parseXml(file)
                findElementById(doc, "ivMark") != null && findElementById(doc, "tvPrimary") != null
            }
        assertTrue(
            "per-feature row forks found: $forks — " +
                "$SHARED_ROW_LAYOUT is the only layout allowed to carry an ivMark + tvPrimary pair",
            forks.isEmpty(),
        )
        assertTrue(moduleFile("src/main/res/layout/$SHARED_ROW_LAYOUT").isFile)
    }

    @Test
    fun `the header is pinned outside the scrolling view in both summary screens`() {
        for ((layout, scrollView) in listOf(
            "fragment_drill.xml" to "drillScrollView",
            "fragment_review.xml" to "reviewScrollView",
        )) {
            val doc = parseXml(moduleFile("src/main/res/layout/$layout"))

            val header = checkNotNull(findElementById(doc, "summaryHeader")) { "$layout must keep the pinned summaryHeader" }
            val list = checkNotNull(findElementById(doc, "summaryList")) { "$layout must keep the scrollable summaryList" }
            val scroller = checkNotNull(findElementById(doc, scrollView)) { "$layout must keep its scroll container $scrollView" }

            assertTrue("$layout: summaryList must scroll inside $scrollView", isDescendantOf(list, scroller))
            assertFalse(
                "$layout: summaryHeader must sit outside $scrollView — the pinned header " +
                    "must not scroll away with the list",
                isDescendantOf(header, scroller),
            )
        }
    }

    private companion object {
        const val SHARED_ROW_LAYOUT = "item_summary_row.xml"
    }
}
