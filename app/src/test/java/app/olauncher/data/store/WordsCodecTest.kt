package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsCodecTest {

    @Test
    fun `round trip single word with default fields`() {
        val words = listOf(
            Word(
                id = 1L,
                text = "Haus",
                dueDay = 19500L,
                addedAt = 1700000000000L
            )
        )
        val encoded = WordsCodec.encode(words)
        val decoded = WordsCodec.decode(encoded)
        assertEquals(words, decoded)
    }

    @Test
    fun `round trip single word with all fields populated`() {
        val words = listOf(
            Word(
                id = 42L,
                text = "schön",
                context = "Das Wetter ist heute sehr schön.",
                meaning = "beautiful, pleasant",
                meaningSource = "wiktionary",
                box = 3,
                dueDay = 19504L,
                addedAt = 1700000000000L,
                learnedAt = 19510L
            )
        )
        val encoded = WordsCodec.encode(words)
        val decoded = WordsCodec.decode(encoded)
        assertEquals(words, decoded)
    }

    @Test
    fun `round trip multiple words preserves order and fields`() {
        val words = listOf(
            Word(1L, "eins", "Eins zwei drei", "one", "user", 1, 100L, 1000L, null),
            Word(2L, "zwei", "", "two", "wiktionary", 2, 102L, 2000L, 105L),
            Word(3L, "drei", "Alle guten Dinge sind drei", "", "", 5, 116L, 3000L, null)
        )
        val encoded = WordsCodec.encode(words)
        val decoded = WordsCodec.decode(encoded)
        assertEquals(words, decoded)
    }

    @Test
    fun `round trip empty list`() {
        val words = emptyList<Word>()
        val encoded = WordsCodec.encode(words)
        val decoded = WordsCodec.decode(encoded)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding empty string returns empty list`() {
        val decoded = WordsCodec.decode("")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding blank string returns empty list`() {
        val decoded = WordsCodec.decode("   \n\t  ")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding null string returns empty list`() {
        val decoded = WordsCodec.decode(null)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding corrupt string returns empty list without crashing`() {
        val corruptSamples = listOf(
            "not json at all",
            "{ not valid json",
            "[ unclosed array",
            "12345",
            "true",
            "null"
        )
        for (sample in corruptSamples) {
            val decoded = WordsCodec.decode(sample)
            assertTrue("Expected empty list for '$sample'", decoded.isEmpty())
        }
    }

    @Test
    fun `decoding json object instead of array returns empty list without crashing`() {
        val decoded = WordsCodec.decode("""{"id": 1, "text": "Haus"}""")
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decoding array with malformed object skips corrupt item without crashing`() {
        val json = """
            [
                {"id": 1, "text": "Haus", "dueDay": 100, "addedAt": 1000},
                {"missing_required_keys": true},
                {"id": 2, "text": "Buch", "dueDay": 200, "addedAt": 2000}
            ]
        """.trimIndent()
        val decoded = WordsCodec.decode(json)
        assertEquals(2, decoded.size)
        assertEquals("Haus", decoded[0].text)
        assertEquals("Buch", decoded[1].text)
    }

    @Test
    fun `decoding uses default values when optional fields are omitted`() {
        val json = """
            [
                {
                    "id": 1,
                    "text": "Katze",
                    "dueDay": 150,
                    "addedAt": 500
                }
            ]
        """.trimIndent()
        val decoded = WordsCodec.decode(json)
        assertEquals(1, decoded.size)
        val word = decoded[0]
        assertEquals(1L, word.id)
        assertEquals("Katze", word.text)
        assertEquals("", word.context)
        assertEquals("", word.meaning)
        assertEquals("", word.meaningSource)
        assertEquals(1, word.box)
        assertEquals(150L, word.dueDay)
        assertEquals(500L, word.addedAt)
        assertNull(word.learnedAt)
    }

    @Test
    fun `encodeToJson and decode with JSONArray directly`() {
        val words = listOf(
            Word(10L, "Hund", "Der Hund bellt", "dog", "wiktionary", 1, 100L, 1000L, null)
        )
        val jsonArray = WordsCodec.encodeToJson(words)
        val decoded = WordsCodec.decode(jsonArray)
        assertEquals(words, decoded)
    }
}
