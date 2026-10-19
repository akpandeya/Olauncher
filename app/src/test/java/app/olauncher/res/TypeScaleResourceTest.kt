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
 * Validates the one type scale architecture:
 * - Four text size dimens: text_display, text_large, text_body, text_small
 * - All layouts under res/layout* use either one of the four dimens or a style
 * - No layout sets android:textSize to a raw sp value
 * - Styles (TextLarge, TextMedium, TextSmall) point to the scale dimens
 * - xxhdpi dimensions stay within ±2sp of baseline values
 */
class TypeScaleResourceTest {

    private val allowedDimens = setOf(
        "@dimen/text_display",
        "@dimen/text_large",
        "@dimen/text_body",
        "@dimen/text_small",
    )

    private val rawSpRegex = Regex("""^\d+(\.\d+)?sp$""")

    private fun resolveDir(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isDirectory } ?: error("$relative not found from ${File(".").absolutePath}")
    }

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

    private fun parseDimens(file: File): Map<String, String> {
        val doc = parseXml(file)
        val nodes = doc.getElementsByTagName("dimen")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            result[element.getAttribute("name")] = element.textContent.trim()
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
    fun `no layout under res layout sets textSize to raw sp and all use the four dimens or a style`() {
        val resDir = resolveDir("src/main/res")
        val layoutDirs = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("layout") }
            ?: emptyArray()

        assertTrue("Expected layout directories under res", layoutDirs.isNotEmpty())

        val violations = mutableListOf<String>()
        var layoutsChecked = 0

        for (dir in layoutDirs) {
            val layoutFiles = dir.listFiles { f -> f.isFile && f.extension == "xml" } ?: emptyArray()
            for (file in layoutFiles) {
                layoutsChecked++
                val doc = parseXml(file)
                forEachElement(doc.documentElement) { element ->
                    val textSize = when {
                        element.hasAttributeNS("http://schemas.android.com/apk/res/android", "textSize") ->
                            element.getAttributeNS("http://schemas.android.com/apk/res/android", "textSize")
                        element.hasAttribute("android:textSize") ->
                            element.getAttribute("android:textSize")
                        else -> null
                    }

                    if (textSize != null) {
                        val viewId = element.getAttribute("android:id")
                        if (rawSpRegex.matches(textSize.trim())) {
                            violations.add("${file.parentFile?.name}/${file.name} ($viewId): raw sp value '$textSize'")
                        } else if (textSize !in allowedDimens) {
                            violations.add("${file.parentFile?.name}/${file.name} ($viewId): textSize '$textSize' not in four dimens $allowedDimens")
                        }
                    }
                }
            }
        }

        assertTrue("Expected to inspect layouts, checked $layoutsChecked", layoutsChecked > 10)
        assertTrue(
            "Found textSize violations in layouts:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun `default dimens and all qualifier dimens define the four text size dimens`() {
        val resDir = resolveDir("src/main/res")
        val dimenDirs = listOf(
            "values",
            "values-ldpi",
            "values-mdpi",
            "values-hdpi",
            "values-xhdpi",
            "values-xxhdpi",
            "values-xxxhdpi",
        )

        for (dirName in dimenDirs) {
            val dimensFile = File(resDir, "$dirName/dimens.xml")
            assertTrue("Expected $dimensFile to exist", dimensFile.exists())
            val dimens = parseDimens(dimensFile)

            assertTrue("$dirName must contain text_display", dimens.containsKey("text_display"))
            assertTrue("$dirName must contain text_large", dimens.containsKey("text_large"))
            assertTrue("$dirName must contain text_body", dimens.containsKey("text_body"))
            assertTrue("$dirName must contain text_small", dimens.containsKey("text_small"))

            assertFalse("$dirName must not contain legacy time_size", dimens.containsKey("time_size"))
            assertFalse("$dirName must not contain legacy time_words_size", dimens.containsKey("time_words_size"))
            assertFalse("$dirName must not contain legacy date_size", dimens.containsKey("date_size"))
        }
    }

    @Test
    fun `visual result on xxhdpi phone is within plus-minus 2sp of baseline`() {
        val resDir = resolveDir("src/main/res")
        val xxhdpiDimens = parseDimens(File(resDir, "values-xxhdpi/dimens.xml"))

        fun spValue(key: String): Int {
            val str = xxhdpiDimens[key] ?: error("Missing $key in values-xxhdpi/dimens.xml")
            assertTrue("Expected $key to end with sp, was $str", str.endsWith("sp"))
            return str.removeSuffix("sp").toInt()
        }

        val display = spValue("text_display")
        val large = spValue("text_large")
        val body = spValue("text_body")
        val small = spValue("text_small")

        // time_size was 66sp; display is 66sp -> diff 0
        assertEquals(66, display)
        // text_large exists at 30sp; time_words_size was 28sp -> diff +2sp (within ±2sp)
        assertEquals(30, large)
        // text_body was hardcoded 20sp in TextMedium -> diff 0
        assertEquals(20, body)
        // text_small exists at 18sp; date_size was 18sp -> diff 0
        assertEquals(18, small)
    }

    @Test
    fun `styles use scale dimens and have no raw sp textSize`() {
        val resDir = resolveDir("src/main/res")
        val styleFiles = listOf(
            File(resDir, "values/styles.xml"),
            File(resDir, "values-night/styles.xml"),
        )

        for (file in styleFiles) {
            val doc = parseXml(file)
            val styles = doc.getElementsByTagName("style")
            for (i in 0 until styles.length) {
                val styleEl = styles.item(i) as Element
                val styleName = styleEl.getAttribute("name")
                val items = styleEl.getElementsByTagName("item")
                for (j in 0 until items.length) {
                    val itemEl = items.item(j) as Element
                    if (itemEl.getAttribute("name") == "android:textSize") {
                        val value = itemEl.textContent.trim()
                        assertFalse(
                            "$styleName in ${file.name} has raw sp textSize: $value",
                            rawSpRegex.matches(value)
                        )
                        assertTrue(
                            "$styleName in ${file.name} textSize '$value' must be in $allowedDimens",
                            value in allowedDimens
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `fragment_today hero day uses text_display`() {
        val file = resolveFile("src/main/res/layout/fragment_today.xml")
        val doc = parseXml(file)
        var heroDayTextSize: String? = null

        forEachElement(doc.documentElement) { element ->
            val id = element.getAttribute("android:id")
            if (id == "@+id/tvHeroDay" || id.endsWith("/tvHeroDay")) {
                heroDayTextSize = element.getAttribute("android:textSize")
            }
        }

        assertEquals("@dimen/text_display", heroDayTextSize)
    }

    @Test
    fun `home clock and clock words map onto text_display and text_large`() {
        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_home.xml"),
            resolveFile("src/main/res/layout-land/fragment_home.xml"),
        )

        for (file in layoutFiles) {
            val doc = parseXml(file)
            var clockTextSize: String? = null
            var clockWordsTextSize: String? = null

            forEachElement(doc.documentElement) { element ->
                val id = element.getAttribute("android:id")
                if (id == "@+id/clock" || id.endsWith("/clock")) {
                    clockTextSize = element.getAttribute("android:textSize")
                } else if (id == "@+id/clockWords" || id.endsWith("/clockWords")) {
                    clockWordsTextSize = element.getAttribute("android:textSize")
                }
            }

            assertEquals("${file.name} clock must use text_display", "@dimen/text_display", clockTextSize)
            assertEquals("${file.name} clockWords must use text_large", "@dimen/text_large", clockWordsTextSize)
        }
    }
}
