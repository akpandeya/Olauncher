package app.olauncher.ui.words

import app.olauncher.R
import app.olauncher.data.store.ReviewEvent
import app.olauncher.helper.ReadableScrim
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

class ReviewButtonsToneTest {

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
            val attrId = element.getAttribute("android:id").removePrefix("@+id/").removePrefix("@id/")
            if (attrId == cleanId) {
                found = element
            }
        }
        return found
    }

    @Test
    fun `ReviewButtonTones maps results to accent colors`() {
        assertEquals(R.color.accentRose, ReviewButtonTones.colorResForResult(ReviewEvent.Result.AGAIN))
        assertEquals(R.color.accentSand, ReviewButtonTones.colorResForResult(ReviewEvent.Result.ALMOST))
        assertEquals(R.color.accentSage, ReviewButtonTones.colorResForResult(ReviewEvent.Result.KNEW))
    }

    @Test
    fun `fragment_review button labels use accent rose, sand and sage`() {
        val file = resolveFile("src/main/res/layout/fragment_review.xml")
        val doc = parseXml(file)

        val btnNo = findElementById(doc, "btnNo")
        assertNotNull("btnNo must exist", btnNo)
        val tvNoLabel = findElementById(doc, "tvNoLabel")
        assertNotNull("tvNoLabel must exist", tvNoLabel)
        assertEquals("@color/accentRose", tvNoLabel!!.getAttribute("android:textColor"))
        assertEquals("@string/words_grade_no", tvNoLabel.getAttribute("android:text"))

        val btnAlmost = findElementById(doc, "btnAlmost")
        assertNotNull("btnAlmost must exist", btnAlmost)
        val tvAlmostLabel = findElementById(doc, "tvAlmostLabel")
        assertNotNull("tvAlmostLabel must exist", tvAlmostLabel)
        assertEquals("@color/accentSand", tvAlmostLabel!!.getAttribute("android:textColor"))
        assertEquals("@string/words_almost", tvAlmostLabel.getAttribute("android:text"))

        val btnKnew = findElementById(doc, "btnKnew")
        assertNotNull("btnKnew must exist", btnKnew)
        val tvKnewLabel = findElementById(doc, "tvKnewLabel")
        assertNotNull("tvKnewLabel must exist", tvKnewLabel)
        assertEquals("@color/accentSage", tvKnewLabel!!.getAttribute("android:textColor"))
        assertEquals("@string/words_grade_knew", tvKnewLabel.getAttribute("android:text"))
    }

    @Test
    fun `interval lines remain unchanged in fragment_review`() {
        val file = resolveFile("src/main/res/layout/fragment_review.xml")
        val doc = parseXml(file)

        val intervalIds = listOf("tvNoInterval", "tvAlmostInterval", "tvKnewInterval")
        for (id in intervalIds) {
            val element = findElementById(doc, id)
            assertNotNull("$id must exist", element)
            assertEquals("$id must use TextSmallLight style", "@style/TextSmallLight", element!!.getAttribute("style"))
            assertFalse("$id must not override textColor", element.hasAttribute("android:textColor"))
        }
    }

    @Test
    fun `colors xml defines accent rose, sand and sage in light and dark`() {
        val lightXml = resolveFile("src/main/res/values/colors.xml").readText()
        val darkXml = resolveFile("src/main/res/values-night/colors.xml").readText()

        assertTrue(lightXml.contains("""<color name="accentRose">@color/accentRoseLight</color>"""))
        assertTrue(lightXml.contains("""<color name="accentSand">@color/accentSandLight</color>"""))
        assertTrue(lightXml.contains("""<color name="accentSage">@color/accentSageLight</color>"""))

        assertTrue(darkXml.contains("""<color name="accentRose">@color/accentRoseDark</color>"""))
        assertTrue(darkXml.contains("""<color name="accentSand">@color/accentSandDark</color>"""))
        assertTrue(darkXml.contains("""<color name="accentSage">@color/accentSageDark</color>"""))
    }

    @Test
    fun `text contrast meets 4_5 to 1 on each button for light and dark`() {
        // Light theme accent colors on white button background
        val lightColors = mapOf(
            "accentRoseLight" to 0xFF82444E.toInt(),
            "accentSandLight" to 0xFF765B3B.toInt(),
            "accentSageLight" to 0xFF4D6653.toInt(),
        )
        val white = 0xFFFFFFFF.toInt()
        for ((name, color) in lightColors) {
            val c = ReadableScrim.contrast(color, white)
            assertTrue("$name on light background contrast $c must be >= 4.5", c >= 4.5)
        }

        // Dark theme accent colors on black button background
        val darkColors = mapOf(
            "accentRoseDark" to 0xFFDE9FA7.toInt(),
            "accentSandDark" to 0xFFD4C4A8.toInt(),
            "accentSageDark" to 0xFF9CAF88.toInt(),
        )
        val black = 0xFF000000.toInt()
        for ((name, color) in darkColors) {
            val c = ReadableScrim.contrast(color, black)
            assertTrue("$name on dark background contrast $c must be >= 4.5", c >= 4.5)
        }
    }
}
