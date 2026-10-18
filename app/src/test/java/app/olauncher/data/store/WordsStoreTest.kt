package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WordsStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: WordsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("words_test").toFile()
        store = WordsStore(tempDir)
        store.epochDaySupplier = { 19500L }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `empty store returns empty lists and zero stats`() {
        assertTrue(store.all().isEmpty())
        assertTrue(store.due(19500L).isEmpty())
        val stats = store.stats(19500L)
        assertEquals(0, stats.total)
        assertEquals(0, stats.due)
        assertEquals(0, stats.learned)
    }

    @Test
    fun `add creates new word due today`() {
        val word = store.add("Haus", "Das ist ein Haus.")
        assertEquals("Haus", word.text)
        assertEquals("Das ist ein Haus.", word.context)
        assertEquals(1, word.box)
        assertEquals(19500L, word.dueDay)
        assertNull(word.learnedAt)
        assertTrue(word.id > 0L)

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(word, all[0])
    }

    @Test
    fun `store de-dupe keeps existing word and replaces context only if non-blank`() {
        val original = store.add("Haus", "Ein großes Haus")
        assertEquals(1, store.all().size)
        assertEquals("Haus", original.text)
        assertEquals("Ein großes Haus", original.context)

        // Adding "haus" (lower case) with blank context keeps existing word and original context
        val sameWithBlank = store.add("haus", "   ")
        assertEquals(1, store.all().size)
        assertEquals(original.id, sameWithBlank.id)
        assertEquals("Haus", sameWithBlank.text)
        assertEquals("Ein großes Haus", sameWithBlank.context)

        // Adding "HAUS" (uppercase) with new non-blank context replaces context, keeps word
        val sameWithNewContext = store.add("HAUS", "Ein schönes Haus am See")
        assertEquals(1, store.all().size)
        assertEquals(original.id, sameWithNewContext.id)
        assertEquals("Haus", sameWithNewContext.text)
        assertEquals("Ein schönes Haus am See", sameWithNewContext.context)

        // Verify across newly loaded list
        val all = store.all()
        assertEquals(1, all.size)
        assertEquals("Haus", all[0].text)
        assertEquals("Ein schönes Haus am See", all[0].context)
    }

    @Test
    fun `update modifies word in store`() {
        val word = store.add("Buch", "Ein Buch lesen")
        val modified = word.copy(
            box = 3,
            dueDay = 19504L,
            meaning = "book",
            meaningSource = "wiktionary"
        )
        store.update(modified)

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(modified, all[0])
    }

    @Test
    fun `delete removes word permanently`() {
        val word1 = store.add("Katze")
        val word2 = store.add("Hund")
        assertEquals(2, store.all().size)

        store.delete(word1.id)
        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(word2.id, all[0].id)
    }

    @Test
    fun `stats calculates total, due, and learned accurately`() {
        val today = 19500L

        // word 1: due today, box 1
        val w1 = store.add("eins")
        // word 2: due in future (today + 4)
        val w2 = store.add("zwei")
        store.update(w2.copy(dueDay = today + 4))
        // word 3: learned
        val w3 = store.add("drei")
        store.update(w3.copy(box = 5, learnedAt = today - 1))

        val stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(1, stats.due) // only w1
        assertEquals(1, stats.learned) // only w3
    }

    @Test
    fun `persists across store instances atomically`() {
        store.add("Fenster", "Das Fenster öffnen")

        val newStoreInstance = WordsStore(tempDir)
        val words = newStoreInstance.all()

        assertEquals(1, words.size)
        assertEquals("Fenster", words[0].text)
        assertEquals("Das Fenster öffnen", words[0].context)
    }
}
