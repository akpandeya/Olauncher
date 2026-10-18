package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class PauseLayoutResourceTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File): Document {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
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

    private fun extractAllIds(doc: Document): Set<String> {
        val ids = mutableSetOf<String>()
        val allNodes = doc.getElementsByTagName("*")
        for (i in 0 until allNodes.length) {
            val element = allNodes.item(i) as Element
            val idAttr = element.getAttribute("android:id")
            if (idAttr.isNotEmpty()) {
                ids.add(idAttr.substringAfterLast("/"))
            }
        }
        return ids
    }

    @Test
    fun `strings contain pause ask why and choices strings matching acceptance criteria`() {
        val strings = loadStrings()
        assertEquals("Ask why before opening", strings["pause_ask_why"])
        assertEquals("Reply to someone", strings["pause_why_reply"])
        assertEquals("Look something up", strings["pause_why_lookup"])
        assertEquals("Just bored", strings["pause_why_bored"])
        assertEquals("Not now · go do something else", strings["pause_not_now_bored"])
    }

    @Test
    fun `fragment_pause contains whyChoicesContainer and the three choices above Open`() {
        val file = resolveFile("src/main/res/layout/fragment_pause.xml")
        val doc = parseXml(file)
        val ids = extractAllIds(doc)

        assertTrue(ids.contains("whyChoicesContainer"))
        assertTrue(ids.contains("choiceReply"))
        assertTrue(ids.contains("choiceLookup"))
        assertTrue(ids.contains("choiceBored"))
        assertTrue(ids.contains("btnOpen"))
        assertTrue(ids.contains("btnNotNow"))
    }
}
