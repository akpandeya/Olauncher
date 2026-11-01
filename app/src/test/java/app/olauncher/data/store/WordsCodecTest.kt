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
        val encoded = WordsCodec.encode(words, emptyList())
        val decoded = WordsCodec.decode(encoded)
        assertEquals(words, decoded.words)
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
        val encoded = WordsCodec.encode(words, emptyList())
        val decoded = WordsCodec.decode(encoded)
        assertEquals(words, decoded.words)
    }

    @Test
    fun `round trip multiple words preserves order and fields`() {
        val words = listOf(
            Word(1L, "eins", "Eins zwei drei", "one", "user", 1, 100L, 1000L, null),
            Word(2L, "zwei", "", "two", "wiktionary", 2, 102L, 2000L, 105L),
            Word(3L, "drei", "Alle guten Dinge sind drei", "", "", 5, 116L, 3000L, null)
        )
        val encoded = WordsCodec.encode(words, emptyList())
        val decoded = WordsCodec.decode(encoded)
        assertEquals(words, decoded.words)
    }

    @Test
    fun `round trip empty list`() {
        val words = emptyList<Word>()
        val encoded = WordsCodec.encode(words, emptyList())
        val decoded = WordsCodec.decode(encoded)
        assertTrue(decoded.words.isEmpty())
        assertTrue(decoded.lists.isEmpty())
    }

    @Test
    fun `decoding empty string returns empty list`() {
        val decoded = WordsCodec.decode("")
        assertTrue(decoded.words.isEmpty())
        assertTrue(decoded.lists.isEmpty())
    }

    @Test
    fun `decoding blank string returns empty list`() {
        val decoded = WordsCodec.decode("   \n\t  ")
        assertTrue(decoded.words.isEmpty())
        assertTrue(decoded.lists.isEmpty())
    }

    @Test
    fun `decoding null string returns empty list`() {
        val decoded = WordsCodec.decode(null)
        assertTrue(decoded.words.isEmpty())
        assertTrue(decoded.lists.isEmpty())
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
            assertTrue("Expected empty list for '$sample'", decoded.words.isEmpty())
        }
    }

    @Test
    fun `decoding json object without envelope keys returns empty words without crashing`() {
        val decoded = WordsCodec.decode("""{"id": 1, "text": "Haus"}""")
        assertTrue(decoded.words.isEmpty())
        assertTrue(decoded.lists.isEmpty())
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
        assertEquals(2, decoded.words.size)
        assertEquals("Haus", decoded.words[0].text)
        assertEquals("Buch", decoded.words[1].text)
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
        assertEquals(1, decoded.words.size)
        val word = decoded.words[0]
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

    @Test
    fun `legacy v1 bare array decodes with empty lists and empty listIds`() {
        val v1 = """
            [
                {"id": 1, "text": "Haus", "dueDay": 100, "addedAt": 1000},
                {"id": 2, "text": "Baum", "context": "Ein alter Baum", "meaning": "tree",
                 "meaningSource": "wiktionary", "box": 3, "dueDay": 104, "addedAt": 2000, "learnedAt": 110}
            ]
        """.trimIndent()
        val decoded = WordsCodec.decode(v1)
        assertTrue(decoded.lists.isEmpty())
        assertEquals(2, decoded.words.size)
        assertEquals("Haus", decoded.words[0].text)
        assertEquals("", decoded.words[0].context)
        assertTrue(decoded.words[0].listIds.isEmpty())
        assertEquals("Baum", decoded.words[1].text)
        assertEquals(110L, decoded.words[1].learnedAt)
        assertTrue(decoded.words[1].listIds.isEmpty())
    }

    @Test
    fun `round trip words with listIds and lists through the envelope`() {
        val lists = listOf(
            WordList("kitchen", "Kitchen"),
            WordList("travel-2", "Travel!")
        )
        val words = listOf(
            Word(1L, "Gabel", "", "fork", "wiktionary", 1, 100L, 1000L, null, listOf("kitchen")),
            Word(2L, "Zug", "", "train", "user", 2, 102L, 2000L, 105L, listOf("kitchen", "travel-2"), contextSource = ContextSource.USER),
            Word(3L, "Haus", "", "", "", 1, 100L, 3000L, null)
        )
        val encoded = WordsCodec.encode(words, lists)
        val decoded = WordsCodec.decode(encoded)
        assertEquals(words, decoded.words)
        assertEquals(lists, decoded.lists)
    }

    @Test
    fun `envelope with future or missing version still decodes`() {
        val future = """{"version": 99, "lists": [{"id": "a", "name": "A"}],
            "words": [{"id": 1, "text": "Haus", "dueDay": 100, "addedAt": 1000}]}"""
        val decodedFuture = WordsCodec.decode(future)
        assertEquals(1, decodedFuture.words.size)
        assertEquals(listOf(WordList("a", "A")), decodedFuture.lists)

        val noVersion = """{"lists": [{"id": "b", "name": "B"}],
            "words": [{"id": 2, "text": "Baum", "dueDay": 100, "addedAt": 1000}]}"""
        val decodedNoVersion = WordsCodec.decode(noVersion)
        assertEquals(1, decodedNoVersion.words.size)
        assertEquals("Baum", decodedNoVersion.words[0].text)
        assertEquals(listOf(WordList("b", "B")), decodedNoVersion.lists)
    }

    @Test
    fun `envelope with corrupt word and list entries skips them`() {
        val json = """
            {
                "version": 2,
                "lists": [
                    {"id": "kitchen", "name": "Kitchen"},
                    "not an object",
                    {"id": 42, "name": "number id"},
                    {"name": "missing id"},
                    {"id": "   ", "name": "blank id"},
                    {"id": "travel", "name": "Travel"}
                ],
                "words": [
                    {"id": 1, "text": "Haus", "dueDay": 100, "addedAt": 1000},
                    {"missing_required_keys": true},
                    {"id": 2, "text": "Baum", "dueDay": 100, "addedAt": 1000}
                ]
            }
        """.trimIndent()
        val decoded = WordsCodec.decode(json)
        assertEquals(listOf(WordList("kitchen", "Kitchen"), WordList("travel", "Travel")), decoded.lists)
        assertEquals(2, decoded.words.size)
        assertEquals("Haus", decoded.words[0].text)
        assertEquals("Baum", decoded.words[1].text)
    }

    @Test
    fun `duplicate list ids keep the first entry`() {
        val json = """
            {
                "version": 2,
                "lists": [
                    {"id": "kitchen", "name": "Kitchen"},
                    {"id": "kitchen", "name": "Duplicate Kitchen"}
                ],
                "words": []
            }
        """.trimIndent()
        val decoded = WordsCodec.decode(json)
        assertEquals(listOf(WordList("kitchen", "Kitchen")), decoded.lists)
    }

    @Test
    fun `non-string and blank listIds entries are dropped`() {
        val json = """
            {
                "version": 2,
                "lists": [],
                "words": [
                    {"id": 1, "text": "Haus", "dueDay": 100, "addedAt": 1000,
                     "listIds": ["kitchen", 42, null, true, "  ", "travel"]},
                    {"id": 2, "text": "Baum", "dueDay": 100, "addedAt": 1000, "listIds": "not-an-array"},
                    {"id": 3, "text": "Zug", "dueDay": 100, "addedAt": 1000, "listIds": null}
                ]
            }
        """.trimIndent()
        val decoded = WordsCodec.decode(json)
        assertEquals(listOf("kitchen", "travel"), decoded.words[0].listIds)
        assertTrue(decoded.words[1].listIds.isEmpty())
        assertTrue(decoded.words[2].listIds.isEmpty())
    }

    @Test
    fun `envelope missing words or lists keys decodes that side as empty`() {
        val noWords = """{"version": 2, "lists": [{"id": "a", "name": "A"}]}"""
        val decodedNoWords = WordsCodec.decode(noWords)
        assertTrue(decodedNoWords.words.isEmpty())
        assertEquals(listOf(WordList("a", "A")), decodedNoWords.lists)

        val noLists = """{"version": 2, "words": [{"id": 1, "text": "Haus", "dueDay": 100, "addedAt": 1000}]}"""
        val decodedNoLists = WordsCodec.decode(noLists)
        assertEquals(1, decodedNoLists.words.size)
        assertTrue(decodedNoLists.lists.isEmpty())
    }
}
