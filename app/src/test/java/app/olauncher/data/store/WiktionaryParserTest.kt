package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WiktionaryParserTest {

    private fun loadFixture(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: javaClass.getResourceAsStream("/$name")
            ?: File("app/src/test/resources/$name").takeIf { it.exists() }?.inputStream()
            ?: File("src/test/resources/$name").takeIf { it.exists() }?.inputStream()
        assertNotNull("Fixture $name must be readable", stream)
        return stream!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun `parse fernweh fixture returns wanderlust noun gloss`() {
        val json = loadFixture("wiktionary_fernweh.json")
        val parsed = WiktionaryParser.parse(json)
        assertEquals("wanderlust (desire to travel, a longing for far-off places)", parsed)
    }

    @Test
    fun `parse ging fixture returns inflection gloss of gehen`() {
        val json = loadFixture("wiktionary_ging.json")
        val parsed = WiktionaryParser.parse(json)
        assertEquals("first/third-person singular preterite of gehen", parsed)
    }

    @Test
    fun `parse returns null for invalid json or empty input`() {
        assertNull(WiktionaryParser.parse(""))
        assertNull(WiktionaryParser.parse("   "))
        assertNull(WiktionaryParser.parse("{not valid json"))
        assertNull(WiktionaryParser.parse("[]"))
    }

    @Test
    fun `parse returns null when de key is missing or not an array`() {
        assertNull(WiktionaryParser.parse("""{"en": [{"definitions": [{"definition": "hello"}]}]}"""))
        assertNull(WiktionaryParser.parse("""{"de": null}"""))
        assertNull(WiktionaryParser.parse("""{"de": "invalid"}"""))
        assertNull(WiktionaryParser.parse("""{"de": []}"""))
    }

    @Test
    fun `parse takes first two definitions across entries and joins with semicolon`() {
        val json = """
        {
          "de": [
            {
              "partOfSpeech": "Noun",
              "definitions": [
                { "definition": "Erste Definition." }
              ]
            },
            {
              "partOfSpeech": "Verb",
              "definitions": [
                { "definition": "Zweite Definition." },
                { "definition": "Dritte Definition." }
              ]
            }
          ]
        }
        """.trimIndent()

        val parsed = WiktionaryParser.parse(json)
        assertEquals("Erste Definition.; Zweite Definition.", parsed)
    }

    @Test
    fun `parse strips html tags and decodes entities and collapses whitespace`() {
        val json = """
        {
          "de": [
            {
              "definitions": [
                { "definition": "  <span>Ein &quot;großes&quot; &amp; schönes Haus&#33;</span> \n\n <style>.foo{}</style> " },
                { "definition": "   " },
                { "definition": "Zweite &lt;gute&gt; Definition mit&nbsp;Leerzeichen." }
              ]
            }
          ]
        }
        """.trimIndent()

        val parsed = WiktionaryParser.parse(json)
        assertEquals("Ein \"großes\" & schönes Haus!; Zweite <gute> Definition mit Leerzeichen.", parsed)
    }

    @Test
    fun `parse drops empty definitions and returns null if none valid`() {
        val json = """
        {
          "de": [
            {
              "definitions": [
                { "definition": "" },
                { "definition": "   " },
                { "definition": "<style>.a{}</style>" }
              ]
            }
          ]
        }
        """.trimIndent()

        assertNull(WiktionaryParser.parse(json))
    }

    @Test
    fun `parse truncates to max 160 chars cutting on word boundary with ellipsis`() {
        // Build two long definitions
        val def1 = "Dies ist die allererste sehr ausführliche und lange Definition eines deutschen Wortes"
        val def2 = "und dies ist die zweite Definition die noch viel länger ist und hoffentlich die Grenze von einhundertsechzig Zeichen deutlich überschreitet."
        val json = """
        {
          "de": [
            {
              "definitions": [
                { "definition": "$def1" },
                { "definition": "$def2" }
              ]
            }
          ]
        }
        """.trimIndent()

        val parsed = WiktionaryParser.parse(json)
        assertNotNull(parsed)
        assertTrue("Parsed length ${parsed!!.length} must be <= 160", parsed.length <= 160)
        assertTrue("Must end with ellipsis", parsed.endsWith("…"))
        // Check that it didn't cut mid-word
        val beforeEllipsis = parsed.dropLast(1)
        assertTrue("Cut should not end with whitespace", !beforeEllipsis.endsWith(" "))
    }

    @Test
    fun `candidates returns distinct text, capitalised, lowercased for single word`() {
        assertEquals(listOf("Fernweh", "fernweh"), WiktionaryParser.candidates("Fernweh"))
        assertEquals(listOf("fernweh", "Fernweh"), WiktionaryParser.candidates("fernweh"))
        assertEquals(listOf("ging", "Ging"), WiktionaryParser.candidates("ging"))
        assertEquals(listOf("Ging", "ging"), WiktionaryParser.candidates("Ging"))
        assertEquals(listOf("äpfel", "Äpfel"), WiktionaryParser.candidates("äpfel"))
        assertEquals(listOf("Äpfel", "äpfel"), WiktionaryParser.candidates("Äpfel"))
    }

    @Test
    fun `candidates returns only original text for phrase`() {
        assertEquals(listOf("sich freuen auf"), WiktionaryParser.candidates("sich freuen auf"))
        assertEquals(listOf("auf Wiedersehen"), WiktionaryParser.candidates("auf Wiedersehen"))
        assertEquals(listOf("guten Tag"), WiktionaryParser.candidates("guten Tag"))
    }

    @Test
    fun `candidates adds phrase without leading article`() {
        assertEquals(listOf("der Hund", "Hund"), WiktionaryParser.candidates("der Hund"))
        assertEquals(listOf("Die Katze", "Katze"), WiktionaryParser.candidates("Die Katze"))
        assertEquals(listOf("eine schöne Frau", "schöne Frau"), WiktionaryParser.candidates("eine schöne Frau"))
    }

    @Test
    fun `candidates unchanged for single word and lone article`() {
        assertEquals(listOf("Fernweh", "fernweh"), WiktionaryParser.candidates("Fernweh"))
        assertEquals(listOf("das", "Das"), WiktionaryParser.candidates("das"))
    }
}
