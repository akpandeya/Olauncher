package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PracticeSentencesStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: PracticeSentencesStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("practice_sentences_test").toFile()
        store = PracticeSentencesStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun sentence(
        wordId: Long = 7_123L,
        sourceSentenceId: String = "a1b2c3d4e5f60718",
        text: String = "Der Hund schläft im Garten.",
        originalContext: String = "Der Hund schläft im [Garten].",
        model: String = "gemma-4-31b",
        createdAt: Long = 1_712_345_678_901L
    ) = PracticeSentence(
        wordId = wordId,
        sourceSentenceId = sourceSentenceId,
        text = text,
        originalContext = originalContext,
        model = model,
        createdAt = createdAt
    )

    @Test
    fun `encode and decode round-trip keeps every field with non-default values`() {
        val original = sentence(
            wordId = 7_123L,
            sourceSentenceId = "a1b2c3d4e5f60718",
            text = "Der Hund schläft im Garten.",
            originalContext = "Der Hund schläft im [Garten].",
            model = "gemma-4-31b",
            createdAt = 1_712_345_678_901L
        )

        val encoded = PracticeSentencesCodec.encode(listOf(original))
        val decoded = PracticeSentencesCodec.decode(encoded)

        assertEquals(1, decoded.size)
        assertEquals(original, decoded[0])
    }

    @Test
    fun `decode of null, empty, brackets, and garbage gives empty list`() {
        assertEquals(emptyList<PracticeSentence>(), PracticeSentencesCodec.decode(null))
        assertEquals(emptyList<PracticeSentence>(), PracticeSentencesCodec.decode(""))
        assertEquals(emptyList<PracticeSentence>(), PracticeSentencesCodec.decode("   "))
        assertEquals(emptyList<PracticeSentence>(), PracticeSentencesCodec.decode("[]"))
        assertEquals(emptyList<PracticeSentence>(), PracticeSentencesCodec.decode("garbage"))
        assertEquals(emptyList<PracticeSentence>(), PracticeSentencesCodec.decode("{not-valid-json"))
    }

    @Test
    fun `corrupt entries are skipped while neighbour decodes and defaults apply`() {
        val raw = """
            {
              "version": 1,
              "sentences": [
                {
                  "wordId": "not-a-number",
                  "sourceSentenceId": "s1",
                  "text": "String wordId wird übersprungen.",
                  "createdAt": 100
                },
                {
                  "wordId": 42,
                  "sourceSentenceId": "s2",
                  "text": "Gültiger Satz ohne model und context.",
                  "createdAt": 200
                },
                {
                  "wordId": 43,
                  "sourceSentenceId": 99,
                  "text": "SourceSentenceId ist keine Zeichenkette.",
                  "createdAt": 300
                },
                {
                  "wordId": 44,
                  "sourceSentenceId": "s4",
                  "text": 12345,
                  "createdAt": 400
                },
                {
                  "wordId": 45,
                  "sourceSentenceId": "s5",
                  "text": "   ",
                  "createdAt": 500
                },
                {
                  "wordId": 46,
                  "sourceSentenceId": "s6",
                  "text": "createdAt fehlt komplett."
                },
                {
                  "wordId": 47,
                  "sourceSentenceId": "s7",
                  "text": "createdAt ist keine Zahl.",
                  "createdAt": "morgen"
                }
              ]
            }
        """.trimIndent()

        val decoded = PracticeSentencesCodec.decode(raw)

        assertEquals(1, decoded.size)
        assertEquals(42L, decoded[0].wordId)
        assertEquals("s2", decoded[0].sourceSentenceId)
        assertEquals("Gültiger Satz ohne model und context.", decoded[0].text)
        assertEquals("", decoded[0].originalContext)
        assertEquals("", decoded[0].model)
        assertEquals(200L, decoded[0].createdAt)
    }

    @Test
    fun `add dedupes same word and normalized text without rewriting the file`() {
        val first = sentence(text = "Der Hund schläft im Garten.")
        assertTrue(store.add(first))

        val fileBefore = File(tempDir, PracticeSentencesStore.FILE_NAME)
        val contentsBefore = fileBefore.readText()
        val lastModifiedBefore = fileBefore.lastModified()

        // Same word, whitespace differences only → duplicate.
        assertFalse(store.add(first.copy(text = "  Der   Hund schläft im Garten. ")))
        assertEquals(1, store.all().size)
        assertFalse(store.add(sentence(text = first.text, model = "other-model")))
        assertEquals(1, store.all().size)

        assertEquals(contentsBefore, fileBefore.readText())
        assertEquals(lastModifiedBefore, fileBefore.lastModified())

        // Different word → not a duplicate.
        assertTrue(store.add(first.copy(wordId = 8_999L)))
        assertEquals(2, store.all().size)
    }

    @Test
    fun `forWord filters by word and orders newest createdAt first`() {
        store.add(sentence(wordId = 10L, text = "Satz eins.", createdAt = 1000L))
        store.add(sentence(wordId = 10L, text = "Satz zwei.", createdAt = 3000L))
        store.add(sentence(wordId = 10L, text = "Satz drei.", createdAt = 2000L))
        store.add(sentence(wordId = 20L, text = "Anderes Wort.", createdAt = 4000L))

        val forWord10 = store.forWord(10L)
        assertEquals(3, forWord10.size)
        assertEquals("Satz zwei.", forWord10[0].text)
        assertEquals("Satz drei.", forWord10[1].text)
        assertEquals("Satz eins.", forWord10[2].text)

        val forWord20 = store.forWord(20L)
        assertEquals(1, forWord20.size)
        assertEquals("Anderes Wort.", forWord20[0].text)

        assertTrue(store.forWord(999L).isEmpty())
    }

    @Test
    fun `cap drops oldest entries by createdAt when exceeding the cap`() {
        val smallStore = PracticeSentencesStore(tempDir, maxEntries = 3)
        smallStore.add(sentence(text = "Ältester Satz.", createdAt = 1000L))
        smallStore.add(sentence(text = "Mittlerer Satz.", createdAt = 2000L))
        smallStore.add(sentence(text = "Jüngerer Satz.", createdAt = 3000L))
        smallStore.add(sentence(text = "Neuester Satz.", createdAt = 4000L))

        val all = smallStore.all()
        assertEquals(3, all.size)
        val texts = all.map { it.text }.toSet()
        assertFalse(texts.contains("Ältester Satz."))
        assertTrue(texts.contains("Mittlerer Satz."))
        assertTrue(texts.contains("Jüngerer Satz."))
        assertTrue(texts.contains("Neuester Satz."))
    }

    @Test
    fun `second store instance on the same directory reads what the first wrote`() {
        val first = sentence()
        assertTrue(store.add(first))

        val reopened = PracticeSentencesStore(tempDir)
        assertEquals(listOf(first), reopened.all())
        assertEquals(listOf(first), reopened.forWord(first.wordId))
    }
}
