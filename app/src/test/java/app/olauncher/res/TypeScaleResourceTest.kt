package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class TypeScaleResourceTest {

    private val rawSpRegex = Regex("^[0-9]+(\\.[0-9]+)?sp$")

    private fun resolveDir(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isDirectory } ?: error("Directory $relative not found from ${File(".").absolutePath}")
    }

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

    private fun parseDimens(file: File): Map<String, String> {
        val doc = parseXml(file)
        val nodes = doc.getElementsByTagName("dimen")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            val name = element.getAttribute("name")
            val value = element.textContent.trim()
            result[name] = value
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
    fun `no layout under res layout sets textSize to raw sp and all use dimens or a style`() {
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
                            violations.add("${dir.name}/${file.name} ($viewId): raw sp value '$textSize'")
                        } else if (!textSize.trim().startsWith("@dimen/")) {
                            violations.add("${dir.name}/${file.name} ($viewId): textSize '$textSize' is not a dimen reference")
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

    private val textTokens = listOf(
        "text_display", "text_large", "text_body", "text_small", "text_weather",
        "text_drawer_action", "text_subtitle", "text_caption", "time_size", "date_size",
    )

    @Test
    fun `text tokens are defined once in default dimens and never in density folders`() {
        val resDir = resolveDir("src/main/res")
        val defaults = parseDimens(File(resDir, "values/dimens.xml"))
        for (token in textTokens) {
            assertTrue("values must contain $token", defaults.containsKey(token))
        }
        val densityDirs = resDir.listFiles { f -> f.isDirectory && f.name.matches(Regex("values-[a-z]*dpi")) }
            ?: emptyArray()
        for (dir in densityDirs) {
            val dimensFile = File(dir, "dimens.xml")
            if (!dimensFile.exists()) continue
            val dimens = parseDimens(dimensFile)
            for (token in textTokens) {
                assertFalse("${dir.name} must not override $token", dimens.containsKey(token))
            }
        }
    }

    @Test
    fun `no text token is below 14sp in any width folder`() {
        val resDir = resolveDir("src/main/res")
        val widthDirs = resDir.listFiles { f -> f.isDirectory && f.name.matches(Regex("values-sw[0-9]+dp")) }
            ?: emptyArray()
        val files = listOf(File(resDir, "values/dimens.xml")) + widthDirs.map { File(it, "dimens.xml") }
        for (file in files) {
            val dimens = parseDimens(file)
            for (token in textTokens) {
                val value = dimens[token] ?: continue
                assertTrue("$token in ${file.parentFile.name} must be sp, was $value", value.endsWith("sp"))
                assertTrue(
                    "$token in ${file.parentFile.name} is below 14sp: $value",
                    value.removeSuffix("sp").toFloat() >= 14f
                )
            }
        }
    }

    @Test
    fun `typical phone width keeps the original sizes`() {
        val resDir = resolveDir("src/main/res")
        val dimens = parseDimens(File(resDir, "values/dimens.xml")) +
            parseDimens(File(resDir, "values-sw360dp/dimens.xml"))

        fun spValue(key: String): Int {
            val str = dimens[key] ?: error("Missing $key")
            assertTrue("Expected $key to end with sp, was $str", str.endsWith("sp"))
            return str.removeSuffix("sp").toInt()
        }

        assertEquals(40, spValue("text_display"))
        assertEquals(30, spValue("text_large"))
        assertEquals(20, spValue("text_body"))
        assertEquals(18, spValue("text_small"))
        assertEquals(150, spValue("time_size"))
        assertEquals(18, spValue("date_size"))
        assertEquals(48, spValue("text_weather"))
        assertEquals(14, spValue("text_drawer_action"))
        assertEquals(14, spValue("text_subtitle"))
    }

    @Test
    fun `styles use scale dimens and have no raw sp textSize`() {
        val resDir = resolveDir("src/main/res")
        val styleFiles = listOf(
            File(resDir, "values/styles.xml"),
            File(resDir, "values/themes_base.xml"),
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
    fun `card_weather temp uses text_weather`() {
        val file = resolveFile("src/main/res/layout/card_weather.xml")
        val doc = parseXml(file)
        var tempTextSize: String? = null

        forEachElement(doc.documentElement) { element ->
            val id = element.getAttribute("android:id")
            if (id == "@+id/tvTemp" || id.endsWith("/tvTemp")) {
                tempTextSize = element.getAttribute("android:textSize")
            }
        }

        assertEquals("@dimen/text_weather", tempTextSize)
    }

    @Test
    fun `home clock and time words keep their sizes`() {
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

            assertEquals("${file.name} clock must use @dimen/time_size", "@dimen/time_size", clockTextSize)
            assertEquals("${file.name} clockWords must use @dimen/time_words_size", "@dimen/time_words_size", clockWordsTextSize)
        }
    }
}
