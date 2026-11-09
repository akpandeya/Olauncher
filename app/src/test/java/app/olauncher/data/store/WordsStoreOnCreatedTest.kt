package app.olauncher.data.store

import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The [WordsStore.onWordsCreated] hook (#835, task 03): it fires with the
 * words a save actually created — never for an existing word or a context-only
 * update — and after the lock is released, so it can save again.
 */
class WordsStoreOnCreatedTest {

    private lateinit var tempDir: java.io.File
    private lateinit var store: WordsStore
    private val createdCalls = mutableListOf<List<Word>>()

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("words_created_test").toFile()
        store = WordsStore(tempDir, onWordsCreated = { createdCalls += it }).apply {
            epochDaySupplier = { 19500L }
        }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `add of a new word fires the hook once with that word`() {
        val word = store.add("Haus", "Das ist ein Haus.")

        assertEquals(listOf(listOf(word)), createdCalls)
    }

    @Test
    fun `re-adding the same text in any case does not fire`() {
        store.add("Haus", "Ein großes Haus")
        createdCalls.clear()

        // Blank context on an existing word: nothing created.
        store.add("haus", "   ")
        // A new captured context on an existing word: a context-only update.
        store.add("HAUS", "Ein schönes Haus am See")

        assertTrue(createdCalls.isEmpty())
        assertEquals(1, store.all().size)
    }

    @Test
    fun `addAll reports only the added words`() {
        store.add("Fernweh")
        createdCalls.clear()

        val result = store.addAll(listOf("fernweh", "Heimweh", "Sehnsucht"), "ctx")

        assertEquals(listOf("Heimweh", "Sehnsucht"), result.added.map { it.text })
        assertEquals(1, createdCalls.size)
        assertEquals(listOf("Heimweh", "Sehnsucht"), createdCalls.single().map { it.text })
    }

    @Test
    fun `addAll that adds nothing does not fire`() {
        store.add("Fernweh")
        createdCalls.clear()

        store.addAll(listOf("FERNWEH"))

        assertTrue(createdCalls.isEmpty())
    }

    @Test
    fun `the hook runs after the lock is released, so it can save again`() {
        lateinit var reentrant: WordsStore
        reentrant = WordsStore(
            tempDir,
            onWordsCreated = { words ->
                // The hook adding another word must not deadlock and must not
                // recurse forever: it fires once more for the new word only.
                if (words.single().text == "Eins") reentrant.add("Zwei")
            },
        ).apply { epochDaySupplier = { 19500L } }

        reentrant.add("Eins")

        assertEquals(setOf("Eins", "Zwei"), reentrant.all().map { it.text }.toSet())
        assertEquals(2, reentrant.all().size)
    }
}
