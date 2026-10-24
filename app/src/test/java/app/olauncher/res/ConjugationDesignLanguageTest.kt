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

class ConjugationDesignLanguageTest {

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
    fun `fragment_conjugation views use expected Today styles and panels`() {
        val file = resolveFile("src/main/res/layout/fragment_conjugation.xml")
        val doc = parseXml(file)

        val expectedStyles = mapOf(
            "tvLemma" to "@style/TodayCardLabel",
            "tvProgress" to "@style/TodayCardBodyLight",
            "tvPrompt" to "@style/TodayCardBodyLight",
            "tvMeaning" to "@style/TodayCardBodyLight",
            "tvSummaryNote" to "@style/TodayCardBodyLight",
            "tvPronoun" to "@style/TodayCardStat",
            "tvScore" to "@style/TodayCardStat",
            "tvEmpty" to "@style/TodayCardBody",
            "btnPrimary" to "@style/TodayButton",
            "btnSecondary" to "@style/TodayCardBody",
            "licenseNote" to "@style/TodayCardBodyLight",
        )

        for ((id, style) in expectedStyles) {
            val el = findElementById(doc, id)
            assertNotNull("View '$id' must exist in fragment_conjugation.xml", el)
            assertEquals("View '$id' must use style '$style'", style, el!!.getAttribute("style"))
        }

        // Summary heading above tvScore
        var summaryHeading: Element? = null
        forEachElement(doc.documentElement) { el ->
            if (el.getAttribute("android:text") == "@string/conjugation_first_try") {
                summaryHeading = el
            }
        }
        assertNotNull("Summary heading with text @string/conjugation_first_try must exist", summaryHeading)
        assertEquals(
            "Summary heading above tvScore must use style @style/TodayCardLabel",
            "@style/TodayCardLabel",
            summaryHeading!!.getAttribute("style"),
        )

        // tvPronoun and tvScore must have their @dimen/text_display overrides removed
        val tvPronoun = findElementById(doc, "tvPronoun")!!
        assertFalse("tvPronoun must not specify textSize override", tvPronoun.hasAttribute("android:textSize"))

        val tvScore = findElementById(doc, "tvScore")!!
        assertFalse("tvScore must not specify textSize override", tvScore.hasAttribute("android:textSize"))

        // btnPrimary drops today_chip background and explicit textColor
        val btnPrimary = findElementById(doc, "btnPrimary")!!
        assertFalse(
            "btnPrimary must drop today_chip background",
            btnPrimary.getAttribute("android:background").contains("today_chip"),
        )
        assertFalse("btnPrimary must drop explicit textColor", btnPrimary.hasAttribute("android:textColor"))

        // btnSecondary keeps selectableItemBackground and 48dp minHeight
        val btnSecondary = findElementById(doc, "btnSecondary")!!
        assertEquals("?android:attr/selectableItemBackground", btnSecondary.getAttribute("android:background"))
        assertEquals("48dp", btnSecondary.getAttribute("android:minHeight"))

        // licenseNote uses @dimen/today_label_size in place of text_subtitle
        val licenseNote = findElementById(doc, "licenseNote")!!
        assertEquals("@dimen/today_label_size", licenseNote.getAttribute("android:textSize"))
        assertEquals("?attr/primaryColor", licenseNote.getAttribute("android:textColorLink"))

        // quizCard and questionCard stay on @drawable/today_panel
        val quizCard = findElementById(doc, "quizCard")!!
        assertEquals("@drawable/today_panel", quizCard.getAttribute("android:background"))

        val questionCard = findElementById(doc, "questionCard")!!
        assertEquals("@drawable/today_panel", questionCard.getAttribute("android:background"))
    }

    @Test
    fun `no old Text styles, raw sp, or text_display or text_subtitle left in fragment_conjugation`() {
        val file = resolveFile("src/main/res/layout/fragment_conjugation.xml")
        val doc = parseXml(file)
        val rawSpRegex = Regex(".*[0-9]+(\\.[0-9]+)?sp.*")

        forEachElement(doc.documentElement) { el ->
            val style = el.getAttribute("style")
            assertFalse(
                "Element <${el.tagName}> must not use old Text style '$style'",
                style.startsWith("@style/Text"),
            )

            val textSize = el.getAttribute("android:textSize")
            if (textSize.isNotEmpty()) {
                assertFalse(
                    "Element <${el.tagName}> must not use raw sp textSize '$textSize'",
                    rawSpRegex.matches(textSize),
                )
                assertFalse(
                    "Element <${el.tagName}> must not use @dimen/text_display",
                    textSize.contains("text_display"),
                )
                assertFalse(
                    "Element <${el.tagName}> must not use @dimen/text_subtitle",
                    textSize.contains("text_subtitle"),
                )
            }

            val attrs = el.attributes
            for (i in 0 until attrs.length) {
                val value = attrs.item(i).nodeValue
                assertFalse(
                    "Attribute '${attrs.item(i).nodeName}' must not reference text_display",
                    value.contains("text_display"),
                )
                assertFalse(
                    "Attribute '${attrs.item(i).nodeName}' must not reference text_subtitle",
                    value.contains("text_subtitle"),
                )
            }
        }

        val fileContent = file.readText()
        assertFalse("fragment_conjugation.xml must not contain @style/Text", fileContent.contains("@style/Text"))
        assertFalse("fragment_conjugation.xml must not contain text_display", fileContent.contains("text_display"))
        assertFalse("fragment_conjugation.xml must not contain text_subtitle", fileContent.contains("text_subtitle"))
    }

    @Test
    fun `quiz_choice and quiz_choice_wrong contain no primaryColorTrans50, no raw dp stroke widths, and reference todayTile and strokeThin`() {
        val choiceFiles = listOf(
            "src/main/res/drawable/quiz_choice.xml",
            "src/main/res/drawable/quiz_choice_wrong.xml",
        )
        val rawDpRegex = Regex("^[0-9]+(\\.[0-9]+)?dp$")

        for (relPath in choiceFiles) {
            val file = resolveFile(relPath)
            val content = file.readText()

            assertFalse(
                "${file.name} must not contain primaryColorTrans50",
                content.contains("primaryColorTrans50"),
            )
            assertTrue(
                "${file.name} must reference todayTile or today_tile",
                content.contains("todayTile") || content.contains("today_tile"),
            )
            assertTrue(
                "${file.name} must reference strokeThin",
                content.contains("strokeThin"),
            )

            val doc = parseXml(file)
            forEachElement(doc.documentElement) { el ->
                if (el.tagName == "stroke") {
                    val width = el.getAttribute("android:width")
                    if (width.isNotEmpty()) {
                        assertFalse(
                            "${file.name} stroke width '$width' must not be a raw dp value",
                            rawDpRegex.matches(width.trim()),
                        )
                        assertTrue(
                            "${file.name} stroke width '$width' must reference a dimen token",
                            width.startsWith("@dimen/"),
                        )
                    }
                }
                assertFalse(
                    "${file.name} must not contain state_selected item",
                    el.getAttribute("android:state_selected") == "true",
                )
            }
        }
    }

    @Test
    fun `four new quiz dimens exist in dp`() {
        val file = resolveFile("src/main/res/values/dimens.xml")
        val doc = parseXml(file)
        val dimens = mutableMapOf<String, String>()

        val nodes = doc.getElementsByTagName("dimen")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            dimens[el.getAttribute("name")] = el.textContent.trim()
        }

        val requiredQuizDimens = listOf(
            "quiz_cell_padding",
            "quiz_choice_min_height",
            "quiz_cell_margin",
            "quiz_segment_gap",
        )

        for (name in requiredQuizDimens) {
            val value = dimens[name]
            assertNotNull("dimens.xml must define $name", value)
            assertTrue("$name must be specified in dp, was $value", value!!.endsWith("dp"))
        }
    }
}
