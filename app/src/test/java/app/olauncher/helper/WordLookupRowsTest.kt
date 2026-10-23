package app.olauncher.helper

import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.store.OfflineGermanEntry
import app.olauncher.data.store.WiktionaryApi
import app.olauncher.ui.AppDrawerAdapter
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class WordLookupRowsTest {
    private val meaning = OfflineGermanEntry(
        "gehen", "verb", "", "", "to go, to walk; to move from one place to another on foot",
        "geht", "ging", "gegangen"
    ).meaning("plural")

    private val offlineHit = WiktionaryApi.LookupResult.Found("der Hund — dog", "Hund")

    @Test
    fun `found and saved meanings keep full text in the multiline view type`() {
        val found = WordLookupRows.found("ging", meaning) { word, definition -> "$word — $definition" }
        assertEquals("ging — $meaning", found.text)
        assertEquals(AppModel.Result.Action.SaveWords(listOf("ging"), meaning), found.action)
        assertTrue(found.multiline)
        assertEquals(R.layout.adapter_word_lookup_result, AppDrawerAdapter.resultViewType(found))

        val savedMeaning = "die Studentin (noun) · plural: Studentinnen — female university student"
        val saved = WordLookupRows.saved("Studentin", savedMeaning) { word, definition -> "$word: $definition" }
        assertEquals("Studentin: $savedMeaning", saved.text)
        assertEquals(AppModel.Result.Action.OpenDictionary("Studentin", openOnTap = false), saved.action)
        assertEquals(R.layout.adapter_word_lookup_result, AppDrawerAdapter.resultViewType(saved))

        val unrelated = AppModel.Result("Copy result", AppModel.Result.Action.Copy("copied value"))
        assertFalse(unrelated.multiline)
        assertEquals(R.layout.adapter_result, AppDrawerAdapter.resultViewType(unrelated))
    }

    @Test
    fun `lookup layout wraps full definitions at body size without clipping limits`() {
        val row = WordLookupRows.found("ging", meaning) { word, definition -> "$word — $definition" }
        val layoutId = AppDrawerAdapter.resultViewType(row)
        val layoutName = R.layout::class.java.fields.first { it.getInt(null) == layoutId }.name
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(resource("layout/$layoutName.xml"))
        val root = document.documentElement
        val text = root.getElementsByTagName("TextView").item(0) as Element
        assertEquals("wrap_content", root.getAttribute("android:layout_height"))
        assertEquals("wrap_content", text.getAttribute("android:layout_height"))
        assertEquals("match_parent", text.getAttribute("android:layout_width"))
        assertEquals("false", text.getAttribute("android:singleLine"))
        assertEquals("false", text.getAttribute("android:scrollHorizontally"))
        for (limit in listOf("maxLines", "lines", "maxHeight", "height", "ellipsize")) {
            assertFalse("Lookup text must not set $limit", text.hasAttribute("android:$limit"))
        }
        assertEquals("@style/TextMedium", text.getAttribute("style"))
        val styles = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(resource("values/styles.xml")).getElementsByTagName("style")
        val body = (0 until styles.length).map { styles.item(it) as Element }
            .first { it.getAttribute("name") == "TextMedium" }
        val items = body.getElementsByTagName("item")
        val size = (0 until items.length).map { items.item(it) as Element }
            .first { it.getAttribute("name") == "android:textSize" }
        assertEquals("@dimen/text_body", size.textContent)
    }

    @Test
    fun `result layout keeps command rows single-line at the large text style`() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(resource("layout/adapter_result.xml"))
        val root = document.documentElement
        assertEquals("48dp", root.getAttribute("android:minHeight"))
        val text = root.getElementsByTagName("TextView").item(0) as Element
        assertEquals("appTitle", text.getAttribute("android:id").substringAfterLast("/"))
        assertEquals("1", text.getAttribute("android:maxLines"))
        assertEquals("@style/TextLarge", text.getAttribute("style"))
    }

    @Test
    fun offlineOnlyMeaningShowsOnlyAnOfflineHit() {
        assertEquals("der Hund — dog", WordLookupRows.offlineOnlyMeaning(offlineHit))
        assertNull(WordLookupRows.offlineOnlyMeaning(WiktionaryApi.LookupResult.NotFound))
        assertNull(WordLookupRows.offlineOnlyMeaning(WiktionaryApi.LookupResult.Failed))
        assertNull(WordLookupRows.offlineOnlyMeaning(null))
    }

    @Test
    fun savedMeaningPrefersTheStoredOne() {
        assertEquals("own note", WordLookupRows.savedMeaning("own note", offlineHit, "no meaning yet"))
    }

    @Test
    fun savedMeaningFallsBackToTheOfflineHitWhenBlank() {
        assertEquals("der Hund — dog", WordLookupRows.savedMeaning("", offlineHit, "no meaning yet"))
    }

    @Test
    fun savedMeaningFallsBackToTheTemplateWithoutAnOfflineHit() {
        assertEquals(
            "no meaning yet",
            WordLookupRows.savedMeaning("", WiktionaryApi.LookupResult.NotFound, "no meaning yet"),
        )
        assertEquals("no meaning yet", WordLookupRows.savedMeaning("", null, "no meaning yet"))
    }

    private fun resource(relative: String): File =
        listOf(File("src/main/res/$relative"), File("app/src/main/res/$relative")).first { it.isFile }
}
