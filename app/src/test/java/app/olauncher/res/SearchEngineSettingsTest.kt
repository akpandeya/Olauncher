package app.olauncher.res

import app.olauncher.R
import app.olauncher.ui.settings.SettingsCatalog
import app.olauncher.ui.settings.SettingsRowShape
import app.olauncher.ui.settings.SettingsRowType
import app.olauncher.ui.settings.SettingsSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
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
    fun `searchEngine row sits directly after drawerBottomSearch in the Drawer list`() {
        val drawerRows = SettingsCatalog.rowsFor(SettingsSections.KEY_DRAWER)
        val bottomSearchAt = drawerRows.indexOfFirst { it.primaryId == R.id.drawerBottomSearch }
        val searchEngineAt = drawerRows.indexOfFirst { it.primaryId == R.id.searchEngine }

        assertTrue("drawerBottomSearch row must exist", bottomSearchAt >= 0)
        assertTrue("searchEngine row must exist", searchEngineAt >= 0)
        assertEquals(
            "searchEngine must be the next row after drawerBottomSearch",
            bottomSearchAt + 1,
            searchEngineAt,
        )
    }

    @Test
    fun `searchEngine row has the expected title, value and layout ids`() {
        val row = SettingsCatalog.rowByViewId(R.id.searchEngine)
        assertNotNull("searchEngine row must exist", row)
        row!!
        assertEquals(R.string.search_engine, row.titleRes)
        assertEquals(R.id.searchEngineLayout, row.layoutId)
        assertEquals(R.id.searchEngineTitle, row.titleId)
        assertEquals(R.id.searchEngine, row.valueId)
        assertEquals(SettingsRowType.CHOICE, row.type)
        assertEquals(SettingsRowShape.VALUE, row.shape)
        assertEquals(SettingsSections.KEY_DRAWER, row.sectionKey)
    }
}
