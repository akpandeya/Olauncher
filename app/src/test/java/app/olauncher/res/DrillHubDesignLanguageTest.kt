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

class DrillHubDesignLanguageTest {

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
    fun `fragment_drill_hub uses the drill frame and Today tokens`() {
        val doc = parseXml(resolveFile("src/main/res/layout/fragment_drill_hub.xml"))

        val expectedStyles = mapOf(
            "tvTitle" to "@style/TodayCardLabel",
            "btnContinue" to "@style/TodayButton",
            "btnWeakest" to "@style/TodayButton",
        )
        for ((id, style) in expectedStyles) {
            val el = findElementById(doc, id)
            assertNotNull("View '$id' must exist in fragment_drill_hub.xml", el)
            assertEquals("View '$id' must use style '$style'", style, el!!.getAttribute("style"))
        }

        val root = doc.documentElement
        assertEquals("The hub is a full screen on the drill scrim", "?attr/todayScrimColor", root.getAttribute("android:background"))
        assertEquals("match_parent", root.getAttribute("android:layout_width"))
        assertEquals("match_parent", root.getAttribute("android:layout_height"))

        // Both suggestion buttons are tappable.
        for (id in listOf("btnContinue", "btnWeakest")) {
            val el = findElementById(doc, id)!!
            assertEquals("View '$id' must be clickable", "true", el.getAttribute("android:clickable"))
            assertEquals("View '$id' must be focusable", "true", el.getAttribute("android:focusable"))
        }

        // The hub rows sit in one today_panel, like the drill summary panel.
        val panel = findElementById(doc, "hubRowsPanel")!!
        assertEquals("@drawable/today_panel", panel.getAttribute("android:background"))

        // The same 24dp side padding as the drill screen, on the pinned header.
        val header = findElementById(doc, "hubHeader")
        assertNotNull("View 'hubHeader' must exist", header)
        assertEquals("24dp", header!!.getAttribute("android:paddingHorizontal"))
    }

    @Test
    fun `fragment_drill_hub avoids old Text styles and raw sp sizes`() {
        val file = resolveFile("src/main/res/layout/fragment_drill_hub.xml")
        val content = file.readText()

        assertFalse("fragment_drill_hub.xml must not contain @style/Text", content.contains("@style/Text"))
        forEachElement(parseXml(file).documentElement) { el ->
            val textSize = el.getAttribute("android:textSize")
            assertFalse(
                "Element <${el.tagName}> must not use raw sp textSize '$textSize'",
                Regex(".*[0-9]+(\\.[0-9]+)?sp.*").matches(textSize),
            )
        }
    }

    @Test
    fun `item_drill_hub_row shows name, stat and the thin bar with a 48dp touch target`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_drill_hub_row.xml"))
        val row = doc.documentElement

        val expectedStyles = mapOf(
            "tvName" to "@style/TodayCardBodyBold",
            "tvStat" to "@style/TodayCardBodyLight",
        )
        for ((id, style) in expectedStyles) {
            val el = findElementById(doc, id)
            assertNotNull("View '$id' must exist in item_drill_hub_row.xml", el)
            assertEquals("View '$id' must use style '$style'", style, el!!.getAttribute("style"))
        }

        assertEquals("@dimen/today_touch_min_height", row.getAttribute("android:minHeight"))
        assertEquals("?android:attr/selectableItemBackground", row.getAttribute("android:background"))
        assertEquals("true", row.getAttribute("android:clickable"))

        // The thin score bar is the custom view, not a ProgressBar.
        val bar = findElementById(doc, "scoreBar")
        assertNotNull("Row must hold a DrillHubBarView", bar)
        assertEquals("app.olauncher.ui.words.DrillHubBarView", bar!!.tagName)
    }

    @Test
    fun `item_drill_hub_new_label is a TodayChip pointing at the New string`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_drill_hub_new_label.xml"))
        val chip = findElementById(doc, "tvNewLabel")

        assertNotNull("The New group label must exist", chip)
        assertEquals("@style/TodayChip.Small", chip!!.getAttribute("style"))
        assertEquals("@drawable/today_chip", chip.getAttribute("android:background"))
        assertEquals("@string/drill_hub_new", chip.getAttribute("android:text"))
    }

    @Test
    fun `the hub strings exist`() {
        val doc = parseXml(resolveFile("src/main/res/values/strings.xml"))
        val strings = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            strings[el.getAttribute("name")] = el.textContent.trim()
        }

        val required = listOf(
            "drill_hub_continue",
            "drill_hub_practise_weakest",
            "drill_hub_stat",
            "drill_hub_one_round",
            "drill_hub_new",
        )
        for (name in required) {
            assertNotNull("strings.xml must define $name", strings[name])
        }
        assertEquals("Continue: %1\$s", strings["drill_hub_continue"])
        assertEquals("Practise weakest: %1\$s", strings["drill_hub_practise_weakest"])
        assertEquals("%1\$d%% · %2\$d rounds", strings["drill_hub_stat"])
    }

    @Test
    fun `nav graph wires the hub between Words and the drill round`() {
        val doc = parseXml(resolveFile("src/main/res/navigation/nav_graph.xml"))

        // The hub is a destination on its own screen, not a dialog.
        val hubFragment = findElementById(doc, "drillHubFragment")
        assertNotNull("nav_graph.xml must define drillHubFragment", hubFragment)
        assertEquals(
            "app.olauncher.ui.words.DrillHubFragment",
            hubFragment!!.getAttribute("android:name"),
        )

        // Words opens the hub (the title tap and the long-press menu entry).
        val wordsToHub = findElementById(doc, "action_wordsFragment_to_drillHubFragment")
        assertNotNull("nav_graph.xml must define action_wordsFragment_to_drillHubFragment", wordsToHub)
        assertEquals("@id/drillHubFragment", wordsToHub!!.getAttribute("app:destination"))

        // A hub row tap starts the 10-item round through the same drill destination.
        val hubToDrill = findElementById(doc, "action_drillHubFragment_to_drillFragment")
        assertNotNull("nav_graph.xml must define action_drillHubFragment_to_drillFragment", hubToDrill)
        assertEquals("@id/drillFragment", hubToDrill!!.getAttribute("app:destination"))

        // The drill destination still takes its drillType argument.
        val drillFragment = findElementById(doc, "drillFragment")!!
        var argument: Element? = null
        forEachElement(drillFragment) { el ->
            if (el.tagName == "argument" && el.getAttribute("android:name") == "drillType") argument = el
        }
        assertNotNull("drillFragment must keep the drillType argument", argument)
        assertEquals("string", argument!!.getAttribute("app:argType"))
    }

    @Test
    fun `the drill screen and hub share the scrim frame`() {
        val drill = resolveFile("src/main/res/layout/fragment_drill.xml").readText()
        val hub = resolveFile("src/main/res/layout/fragment_drill_hub.xml").readText()

        assertTrue(drill.contains("?attr/todayScrimColor"))
        assertTrue(hub.contains("?attr/todayScrimColor"))
        assertTrue(hub.contains("@drawable/today_panel"))
    }
}
