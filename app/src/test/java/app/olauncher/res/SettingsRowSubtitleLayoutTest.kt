package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * #813: the subtitle settings row must lay trailing chips out on their own line
 * below the row instead of stacking them over the subtitle and value inside a
 * FrameLayout.
 */
class SettingsRowSubtitleLayoutTest {

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
    fun `item_settings_row_subtitle root is a vertical LinearLayout`() {
        val root = parseXml(resolveFile("src/main/res/layout/item_settings_row_subtitle.xml")).documentElement

        assertEquals("LinearLayout", root.tagName)
        assertEquals("vertical", root.getAttribute("android:orientation"))
        assertEquals("@+id/settingsRowRoot", root.getAttribute("android:id"))
    }

    @Test
    fun `settingsRowTrailingActions is not a sibling of settingsRowValue in one frame`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_settings_row_subtitle.xml"))
        val value = findElementById(doc, "settingsRowValue")
        val trailingActions = findElementById(doc, "settingsRowTrailingActions")

        assertNotNull("settingsRowValue must exist", value)
        assertNotNull("settingsRowTrailingActions must exist", trailingActions)

        val valueParent = value!!.parentNode as Element
        val trailingParent = trailingActions!!.parentNode as Element

        // The chips line sits below the row, not inside the same frame as the value.
        assertNotSame(
            "settingsRowTrailingActions must not share a parent with settingsRowValue",
            valueParent,
            trailingParent,
        )
        assertEquals(
            "settingsRowTrailingActions must be a direct child of the vertical root",
            doc.documentElement,
            trailingParent,
        )
        assertEquals(
            "settingsRowTrailingActions must align to the end of its line",
            "end",
            trailingActions.getAttribute("android:layout_gravity"),
        )
    }

    @Test
    fun `item_settings_row_subtitle keeps every original view id`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_settings_row_subtitle.xml"))

        val requiredIds = listOf(
            "settingsRowRoot",
            "settingsRowTitle",
            "settingsRowSubtitle",
            "settingsRowActions",
            "settingsRowActionFirst",
            "settingsRowActionSecond",
            "settingsRowValue",
            "settingsRowTrailingActions",
            "settingsRowTrailingActionFirst",
            "settingsRowTrailingActionSecond",
        )
        for (id in requiredIds) {
            assertNotNull("item_settings_row_subtitle.xml must keep view '$id'", findElementById(doc, id))
        }
    }

    @Test
    fun `the value sits at the end of the first line next to a weighted left column`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_settings_row_subtitle.xml"))
        val value = findElementById(doc, "settingsRowValue")!!
        val firstLine = value.parentNode as Element

        assertEquals("The value shares a horizontal line with the left column", "horizontal", firstLine.getAttribute("android:orientation"))
        assertTrue("The left column must flex with layout_weight=1", forEachChild(firstLine).any {
            it.getAttribute("android:layout_weight") == "1"
        })
    }

    private fun forEachChild(parent: Element): List<Element> {
        val children = parent.childNodes
        val result = mutableListOf<Element>()
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                result.add(node as Element)
            }
        }
        return result
    }
}
