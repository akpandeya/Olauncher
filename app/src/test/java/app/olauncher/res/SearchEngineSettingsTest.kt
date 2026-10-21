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

class SearchEngineSettingsTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File): Document {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun loadStrings(file: File): Map<String, Pair<String, String?>> {
        val doc = parseXml(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, Pair<String, String?>>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            var text = element.textContent
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length - 1)
            }
            val formatted = if (element.hasAttribute("formatted")) element.getAttribute("formatted") else null
            result[element.getAttribute("name")] = Pair(text, formatted)
        }
        return result
    }

    @Test
    fun `search engine strings are present and template strings have formatted false`() {
        val strings = loadStrings(resolveFile("src/main/res/values/strings.xml"))

        assertEquals("Search engine", strings["search_engine"]?.first)
        assertEquals("Phone default", strings["search_engine_phone_default"]?.first)
        assertEquals("Google", strings["search_engine_google"]?.first)
        assertEquals("DuckDuckGo", strings["search_engine_duckduckgo"]?.first)
        assertEquals("Bing", strings["search_engine_bing"]?.first)
        assertEquals("Custom", strings["search_engine_custom"]?.first)

        val hint = strings["search_engine_custom_hint"]
        assertNotNull(hint)
        assertEquals("https://example.com/search?q=%s", hint?.first)
        assertEquals("false", hint?.second)

        val invalid = strings["search_engine_custom_invalid"]
        assertNotNull(invalid)
        assertEquals("Enter an http(s) link with %s where the search goes", invalid?.first)
        assertEquals("false", invalid?.second)
    }

    @Test
    fun `searchEngineLayout sits directly after drawerBottomSearchLayout in portrait and landscape`() {
        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
            val doc = parseXml(file)
            val allFrameLayouts = doc.getElementsByTagName("FrameLayout")
            var drawerBottomSearchIdx = -1
            var searchEngineIdx = -1

            var frameLayoutCounter = 0
            for (i in 0 until allFrameLayouts.length) {
                val element = allFrameLayouts.item(i) as Element
                val idAttr = element.getAttribute("android:id")
                if (idAttr == "@+id/drawerBottomSearchLayout") {
                    drawerBottomSearchIdx = frameLayoutCounter
                } else if (idAttr == "@+id/searchEngineLayout") {
                    searchEngineIdx = frameLayoutCounter
                }
                frameLayoutCounter++
            }

            assertTrue("drawerBottomSearchLayout must exist in ${file.name}", drawerBottomSearchIdx >= 0)
            assertTrue("searchEngineLayout must exist in ${file.name}", searchEngineIdx >= 0)
            assertEquals(
                "searchEngineLayout must be the next FrameLayout after drawerBottomSearchLayout in ${file.name}",
                drawerBottomSearchIdx + 1,
                searchEngineIdx,
            )
        }
    }

    @Test
    fun `searchEngineLayout contains title and value textviews with expected styles and ids`() {
        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
            val doc = parseXml(file)
            val allElements = doc.getElementsByTagName("FrameLayout")
            var searchEngineFrameLayout: Element? = null
            for (i in 0 until allElements.length) {
                val el = allElements.item(i) as Element
                if (el.getAttribute("android:id") == "@+id/searchEngineLayout") {
                    searchEngineFrameLayout = el
                    break
                }
            }
            assertNotNull("searchEngineLayout must exist in ${file.name}", searchEngineFrameLayout)

            val textViews = searchEngineFrameLayout!!.getElementsByTagName("TextView")
            assertEquals("searchEngineLayout must have exactly 2 TextViews in ${file.name}", 2, textViews.length)

            val titleView = textViews.item(0) as Element
            assertEquals("@+id/searchEngineTitle", titleView.getAttribute("android:id"))
            assertEquals("@style/TextLabel", titleView.getAttribute("style"))
            assertEquals("@string/search_engine", titleView.getAttribute("android:text"))

            val valueView = textViews.item(1) as Element
            assertEquals("@+id/searchEngine", valueView.getAttribute("android:id"))
            assertEquals("@style/TextSettingsValue", valueView.getAttribute("style"))
        }
    }
}
