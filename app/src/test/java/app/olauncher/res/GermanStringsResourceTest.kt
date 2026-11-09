package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Checks the real strings.xml values used by the German nudges, pause screen and word reveal,
 * so a wording change in the resources is caught here (there is no second hardcoded copy).
 */
class GermanStringsResourceTest {

    private val strings: Map<String, String> by lazy { loadStrings() }

    private fun loadStrings(): Map<String, String> {
        val file = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        ).firstOrNull { it.isFile } ?: error("strings.xml not found from ${File(".").absolutePath}")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            var text = element.textContent
            // Android strips one pair of surrounding double quotes.
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length - 1)
            }
            result[element.getAttribute("name")] = text
        }
        assertTrue("no strings parsed", result.isNotEmpty())
        return result
    }

    private fun string(name: String): String =
        strings[name] ?: throw AssertionError("missing string resource: $name")

    private fun format(name: String, vararg args: Any): String = String.format(string(name), *args)

    @Test
    fun `german nudge notification strings`() {
        assertEquals("Zeit für ein Glas Wasser · heute 3", format("nudge_water_notification_de", 3))
        assertEquals("Steh auf und geh ein paar Minuten · heute 2", format("nudge_walk_notification_de", 2))
        assertEquals(
            "Heute 38 Min. auf Instagram, YouTube. Leg es weg?",
            format("nudge_social_notification_de", 38, "Instagram, YouTube")
        )
        assertEquals(
            "Heute 30 Min. auf ablenkenden Apps. Leg es weg?",
            format("nudge_social_notification_no_apps_de", 30)
        )
        assertEquals("Erledigt", string("done_de"))
    }

    @Test
    fun `german pause screen strings`() {
        assertEquals("Öffnen", string("pause_open_de"))
        assertEquals("Öffnen (5)", format("pause_open_countdown_de", 5))
        assertEquals("Jetzt nicht", string("not_now_de"))
    }

    @Test
    fun `word meaning line strings`() {
        // The Words card and its count/footer strings were removed (#835); the
        // meaning line of the pause-screen reveal still uses these two.
        assertEquals("No meaning yet", string("word_row_no_meaning_yet"))
        assertEquals(" · Wiktionary", string("words_wiktionary_suffix"))
    }
}
