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

class WordsDesignLanguageTest {

    private val oldTextStyles = setOf(
        "@style/TextSmall",
        "@style/TextSmallBold",
        "@style/TextSmallLight",
        "@style/TextMedium",
    )

    private val rawSpRegex = Regex("^[0-9]+(\\.[0-9]+)?sp$")

    private val wordsLayouts = listOf(
        "src/main/res/layout/dialog_edit_word.xml",
        "src/main/res/layout/fragment_word_list.xml",
        "src/main/res/layout/item_word.xml",
    )

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
        val cleanId = id.removePrefix("@+id/").removePrefix("@id/")
        var found: Element? = null
        forEachElement(doc.documentElement) { element ->
            val attrId = element.getAttribute("android:id")
            if (attrId == id || attrId == "@+id/$cleanId" || attrId == "@id/$cleanId") {
                found = element
            }
        }
        return found
    }

    @Test
    fun `dialog_edit_word views have expected Today styles`() {
        val file = resolveFile("src/main/res/layout/dialog_edit_word.xml")
        val doc = parseXml(file)

        val expectedStyles = mapOf(
            "@+id/etWord" to "@style/TodayCardBodyBold",
            "@+id/etMeaning" to "@style/TodayCardBody",
            "@+id/tvSource" to "@style/TodayCardBodyLight",
            "@+id/etExample" to "@style/TodayCardBody",
            "@+id/tvProgress" to "@style/TodayCardBodyLight",
            "@+id/btnDeleteWord" to "@style/TodayButton.Danger",
        )

        for ((id, expectedStyle) in expectedStyles) {
            val element = findElementById(doc, id)
            assertNotNull("View $id must exist in ${file.name}", element)
            assertEquals("View $id in ${file.name} must have style $expectedStyle", expectedStyle, element!!.getAttribute("style"))
        }

        val expectedLabelTexts = setOf(
            "@string/words_word_hint",
            "@string/words_meaning_hint",
            "@string/words_example_hint",
            "@string/words_edit_lists_heading",
        )
        val foundLabels = mutableSetOf<String>()
        forEachElement(doc.documentElement) { element ->
            val text = element.getAttribute("android:text")
            if (text in expectedLabelTexts) {
                foundLabels.add(text)
                assertEquals(
                    "Field label with text $text in ${file.name} must have style @style/TodayCardLabel",
                    "@style/TodayCardLabel",
                    element.getAttribute("style"),
                )
            }
        }
        assertEquals("All 4 field labels must be present in ${file.name}", expectedLabelTexts, foundLabels)
    }

    @Test
    fun `fragment_word_list views have expected Today styles`() {
        val file = resolveFile("src/main/res/layout/fragment_word_list.xml")
        val doc = parseXml(file)

        val expectedStyles = mapOf(
            "@+id/tvEmpty" to "@style/TodayCardBody",
            "@+id/tvTitle" to "@style/TextLarge",
            "@+id/btnAddWords" to "@style/TodayCardBodyBold",
        )

        for ((id, expectedStyle) in expectedStyles) {
            val element = findElementById(doc, id)
            assertNotNull("View $id must exist in ${file.name}", element)
            assertEquals("View $id in ${file.name} must have style $expectedStyle", expectedStyle, element!!.getAttribute("style"))
        }

        val btnAddWords = findElementById(doc, "@+id/btnAddWords")!!
        assertEquals("?attr/accent", btnAddWords.getAttribute("android:textColor"))

        val wordsContainer = findElementById(doc, "@+id/wordsContainer")
        assertNotNull("wordsContainer must exist in ${file.name}", wordsContainer)
        assertEquals("@drawable/today_panel", wordsContainer!!.getAttribute("android:background"))
    }

    @Test
    fun `item_word views have expected Today styles and chip drawable`() {
        val file = resolveFile("src/main/res/layout/item_word.xml")
        val doc = parseXml(file)

        val expectedStyles = mapOf(
            "@+id/tvWord" to "@style/TodayCardBodyBold",
            "@+id/tvMeaning" to "@style/TodayCardBodyLight",
            "@+id/tvStatus" to "@style/TodayChip.Small",
        )

        for ((id, expectedStyle) in expectedStyles) {
            val element = findElementById(doc, id)
            assertNotNull("View $id must exist in ${file.name}", element)
            assertEquals("View $id in ${file.name} must have style $expectedStyle", expectedStyle, element!!.getAttribute("style"))
        }

        val tvStatus = findElementById(doc, "@+id/tvStatus")!!
        assertEquals("@drawable/today_chip", tvStatus.getAttribute("android:background"))
        assertEquals("tnum", tvStatus.getAttribute("android:fontFeatureSettings"))
        assertFalse("tvStatus must not specify textSize override", tvStatus.hasAttribute("android:textSize"))
        assertFalse("tvStatus must not specify shadowRadius override", tvStatus.hasAttribute("android:shadowRadius"))
    }

    @Test
    fun `btnDeleteWord uses TodayButton Danger style`() {
        val file = resolveFile("src/main/res/layout/dialog_edit_word.xml")
        val doc = parseXml(file)
        val btnDelete = findElementById(doc, "@+id/btnDeleteWord")
        assertNotNull("btnDeleteWord must exist in ${file.name}", btnDelete)
        assertEquals("@style/TodayButton.Danger", btnDelete!!.getAttribute("style"))
    }

    @Test
    fun `none of the three layouts use old Text styles, raw sp, bg_pill, or words_button_outline`() {
        for (relPath in wordsLayouts) {
            val file = resolveFile(relPath)
            val doc = parseXml(file)

            forEachElement(doc.documentElement) { element ->
                val styleAttr = element.getAttribute("style")
                assertFalse(
                    "${file.name} view has old text style '$styleAttr'",
                    styleAttr in oldTextStyles,
                )

                val textSize = element.getAttribute("android:textSize")
                if (textSize.isNotEmpty()) {
                    assertFalse(
                        "${file.name} textSize '$textSize' must not be raw sp",
                        rawSpRegex.matches(textSize.trim()),
                    )
                }

                val background = element.getAttribute("android:background")
                assertFalse(
                    "${file.name} must not use bg_pill",
                    background.contains("bg_pill"),
                )
                assertFalse(
                    "${file.name} must not use words_button_outline",
                    background.contains("words_button_outline"),
                )
            }

            val fileContent = file.readText()
            assertFalse("${file.name} file must not reference bg_pill", fileContent.contains("bg_pill"))
            assertFalse("${file.name} file must not reference words_button_outline", fileContent.contains("words_button_outline"))
            for (oldStyle in oldTextStyles) {
                assertFalse("${file.name} file must not reference $oldStyle", fileContent.contains(oldStyle))
            }
        }
    }

    @Test
    fun `WordDialogs source references TodayChips newSelectableChip and no longer declares fun newChip`() {
        val file = resolveFile("src/main/java/app/olauncher/ui/words/WordDialogs.kt")
        val source = file.readText()

        assertTrue(
            "WordDialogs.kt must reference TodayChips.newSelectableChip",
            source.contains("TodayChips.newSelectableChip"),
        )
        assertFalse(
            "WordDialogs.kt must no longer declare fun newChip",
            source.contains("fun newChip"),
        )
    }
}
