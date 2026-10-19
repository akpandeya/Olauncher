package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LivePreviewResourceTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder().parse(file)
    }

    private fun loadStrings(): Map<String, String> {
        val file = resolveFile("src/main/res/values/strings.xml")
        val doc = parseXml(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            var text = element.textContent
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length - 1)
            }
            result[element.getAttribute("name")] = text
        }
        return result
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

    @Test
    fun `strings define sample_text and apply`() {
        val strings = loadStrings()
        assertEquals("Phone · Messages · Maps", strings["sample_text"])
        assertEquals("Apply", strings["apply"])
    }

    @Test
    fun `dialog_text_size contains sample text line with TextLarge style and stepper buttons`() {
        val file = resolveFile("src/main/res/layout/dialog_text_size.xml")
        val doc = parseXml(file)

        var sampleElement: Element? = null
        var minusElement: Element? = null
        var currentElement: Element? = null
        var plusElement: Element? = null

        forEachElement(doc.documentElement) { element ->
            val id = element.getAttribute("android:id")
            when {
                id.endsWith("/textSizeSample") -> sampleElement = element
                id.endsWith("/textSizeMinus") -> minusElement = element
                id.endsWith("/textSizeCurrent") -> currentElement = element
                id.endsWith("/textSizePlus") -> plusElement = element
            }
        }

        assertNotNull("textSizeSample must be present in dialog_text_size.xml", sampleElement)
        assertNotNull("textSizeMinus must be present in dialog_text_size.xml", minusElement)
        assertNotNull("textSizeCurrent must be present in dialog_text_size.xml", currentElement)
        assertNotNull("textSizePlus must be present in dialog_text_size.xml", plusElement)

        val sample = sampleElement!!
        val styleAttr = sample.getAttribute("style")
        assertTrue(
            "textSizeSample must use @style/TextLarge, was: $styleAttr",
            styleAttr == "@style/TextLarge"
        )
        val textAttr = sample.getAttribute("android:text")
        assertTrue(
            "textSizeSample must use @string/sample_text, was: $textAttr",
            textAttr == "@string/sample_text"
        )
    }

    @Test
    fun `alignment menu contains left, center, and right options`() {
        val file = resolveFile("src/main/res/menu/alignment.xml")
        val doc = parseXml(file)
        val itemIds = mutableSetOf<String>()

        forEachElement(doc.documentElement) { element ->
            if (element.tagName == "item") {
                val id = element.getAttribute("android:id")
                if (id.isNotEmpty()) {
                    itemIds.add(id.substringAfterLast("/"))
                }
            }
        }

        assertTrue("alignment menu must contain alignmentLeft", itemIds.contains("alignmentLeft"))
        assertTrue("alignment menu must contain alignmentCenter", itemIds.contains("alignmentCenter"))
        assertTrue("alignment menu must contain alignmentRight", itemIds.contains("alignmentRight"))
    }
}
