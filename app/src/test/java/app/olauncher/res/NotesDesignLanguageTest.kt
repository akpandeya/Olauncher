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

class NotesDesignLanguageTest {

    private val noteLayouts = listOf(
        "src/main/res/layout/fragment_notes.xml",
        "src/main/res/layout/item_note.xml",
        "src/main/res/layout/item_note_checkbox.xml",
        "src/main/res/layout/dialog_add_note.xml",
    )

    private val forbiddenStyles = setOf(
        "@style/TextSmall",
        "@style/TextSmallBold",
        "@style/TextSmallLight",
        "@style/TextMedium",
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
    fun `fragment_notes views use specified styles and today buttons`() {
        val doc = parseXml(resolveFile("src/main/res/layout/fragment_notes.xml"))

        val expected = mapOf(
            "tvTitle" to "@style/TextLarge",
            "tvCounts" to "@style/TodayCardBodyLight",
            "etSearch" to "@style/TodayCardBody",
            "tvPinnedHeading" to "@style/TodayCardLabel",
            "tvOpenHeading" to "@style/TodayCardLabel",
            "tvEmpty" to "@style/TodayCardBody",
            "btnNewNote" to "@style/TodayButton",
            "btnNewNotePlus" to "@style/TodayButton",
        )

        for ((id, style) in expected) {
            val el = findElementById(doc, id)
            assertNotNull("View with id '$id' must exist in fragment_notes.xml", el)
            assertEquals("View '$id' must use style '$style'", style, el!!.getAttribute("style"))
        }

        // Search background stays @null
        val search = findElementById(doc, "etSearch")!!
        assertEquals("@null", search.getAttribute("android:background"))

        // Buttons do not use words_button_outline
        val btnNew = findElementById(doc, "btnNewNote")!!
        val btnPlus = findElementById(doc, "btnNewNotePlus")!!
        assertFalse(btnNew.getAttribute("android:background").contains("words_button_outline"))
        assertFalse(btnPlus.getAttribute("android:background").contains("words_button_outline"))
    }

    @Test
    fun `item_note views use Today typography and chip styles`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_note.xml"))

        val expected = mapOf(
            "tvNoteTitle" to "@style/TodayCardBodyBold",
            "tvNoteCount" to "@style/TodayCardBodyLight",
            "tvNoteBody" to "@style/TodayCardBody",
            "tvNoteMeta" to "@style/TodayCardBodyLight",
            "tvDue" to "@style/TodayChip.Small",
        )

        for ((id, style) in expected) {
            val el = findElementById(doc, id)
            assertNotNull("View with id '$id' must exist in item_note.xml", el)
            assertEquals("View '$id' must use style '$style'", style, el!!.getAttribute("style"))
        }

        val tvDue = findElementById(doc, "tvDue")!!
        assertEquals("@drawable/today_chip", tvDue.getAttribute("android:background"))
    }

    @Test
    fun `item_note_checkbox uses TodayCardBody`() {
        val doc = parseXml(resolveFile("src/main/res/layout/item_note_checkbox.xml"))
        val root = doc.documentElement
        assertEquals("CheckBox", root.tagName)
        assertEquals("@style/TodayCardBody", root.getAttribute("style"))
    }

    @Test
    fun `dialog_add_note views use Today typography, panel group, and accent save button`() {
        val doc = parseXml(resolveFile("src/main/res/layout/dialog_add_note.xml"))

        val expected = mapOf(
            "etNoteTitle" to "@style/TodayCardBodyBold",
            "etNoteBody" to "@style/TodayCardBody",
            "tvRemindDate" to "@style/TodayChip",
            "tvListsHeading" to "@style/TodayCardLabel",
            "btnNoteCancel" to "@style/TodayCardBody",
            "btnNoteSave" to "@style/TodayCardBodyBold",
        )

        for ((id, style) in expected) {
            val el = findElementById(doc, id)
            assertNotNull("View with id '$id' must exist in dialog_add_note.xml", el)
            assertEquals("View '$id' must use style '$style'", style, el!!.getAttribute("style"))
        }

        val tvRemindDate = findElementById(doc, "tvRemindDate")!!
        assertEquals("@drawable/today_chip", tvRemindDate.getAttribute("android:background"))

        val btnNoteSave = findElementById(doc, "btnNoteSave")!!
        assertEquals("?attr/accent", btnNoteSave.getAttribute("android:textColor"))

        // Option rows sit together on a @drawable/today_panel with 16dp horizontal padding
        val switchChecklist = findElementById(doc, "switchChecklist")!!
        val rowRemind = findElementById(doc, "rowRemind")!!
        val switchPin = findElementById(doc, "switchPin")!!

        // Find common ancestor panel with today_panel background
        fun findPanelAncestor(node: Node?): Element? {
            var curr = node?.parentNode
            while (curr != null && curr.nodeType == Node.ELEMENT_NODE) {
                val el = curr as Element
                if (el.getAttribute("android:background") == "@drawable/today_panel") {
                    return el
                }
                curr = curr.parentNode
            }
            return null
        }

        val panelChecklist = findPanelAncestor(switchChecklist)
        val panelRemind = findPanelAncestor(rowRemind)
        val panelPin = findPanelAncestor(switchPin)

        assertNotNull("Option rows must have a parent with @drawable/today_panel", panelChecklist)
        assertEquals("Checklist, remind, and pin rows must sit in the same panel", panelChecklist, panelRemind)
        assertEquals("Checklist, remind, and pin rows must sit in the same panel", panelChecklist, panelPin)
        assertEquals("16dp", panelChecklist!!.getAttribute("android:paddingHorizontal"))
    }

    @Test
    fun `none of the four layouts uses old Text styles, raw sp, or words_button_outline`() {
        val rawSpRegex = Regex(".*[0-9]+(\\.[0-9]+)?sp.*")
        val hexColorRegex = Regex("#[0-9a-fA-F]{3,8}")

        for (relPath in noteLayouts) {
            val file = resolveFile(relPath)
            val doc = parseXml(file)

            forEachElement(doc.documentElement) { el ->
                val style = el.getAttribute("style")
                assertFalse(
                    "${file.name} element <${el.tagName}> uses forbidden style '$style'",
                    forbiddenStyles.contains(style),
                )

                val textSize = el.getAttribute("android:textSize")
                assertTrue(
                    "${file.name} element <${el.tagName}> has raw sp textSize '$textSize'",
                    textSize.isEmpty() || !rawSpRegex.matches(textSize),
                )

                val background = el.getAttribute("android:background")
                assertFalse(
                    "${file.name} element <${el.tagName}> uses words_button_outline",
                    background.contains("words_button_outline"),
                )

                val attrs = el.attributes
                for (i in 0 until attrs.length) {
                    val value = attrs.item(i).nodeValue
                    assertFalse(
                        "${file.name} attribute '${attrs.item(i).nodeName}' contains words_button_outline",
                        value.contains("words_button_outline"),
                    )
                    assertFalse(
                        "${file.name} attribute '${attrs.item(i).nodeName}' has raw hex color '$value'",
                        hexColorRegex.matches(value),
                    )
                }
            }
        }
    }

    @Test
    fun `AddNoteSheet references TodayChips newSelectableChip and no longer declares fun newChip`() {
        val file = resolveFile("src/main/java/app/olauncher/ui/notes/AddNoteSheet.kt")
        val text = file.readText()

        assertTrue(
            "AddNoteSheet.kt must reference TodayChips.newSelectableChip",
            text.contains("TodayChips.newSelectableChip"),
        )
        assertFalse(
            "AddNoteSheet.kt must no longer declare fun newChip",
            text.contains("fun newChip"),
        )
        assertFalse(
            "AddNoteSheet.kt must not use TypedValue for chip styling",
            text.contains("TypedValue"),
        )
        assertFalse(
            "AddNoteSheet.kt must not use dpToPx for chip styling",
            text.contains("dpToPx"),
        )
    }
}
