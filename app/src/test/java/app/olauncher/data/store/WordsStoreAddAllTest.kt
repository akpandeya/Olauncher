package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WordsStoreAddAllTest {

    private lateinit var tempDir: File
    private lateinit var store: WordsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("words_addall_test").toFile()
        store = WordsStore(tempDir)
        store.epochDaySupplier = { 19500L }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `mix of new and existing splits into added and alreadySaved`() {
        store.add("Fernweh")
        val result = store.addAll(listOf("fernweh", "Heimweh", "Sehnsucht"))
        assertEquals(listOf("Heimweh", "Sehnsucht"), result.added.map { it.text })
        assertEquals(listOf("Fernweh"), result.alreadySaved.map { it.text })
        assertEquals(3, store.all().size)
    }

    @Test
    fun `all existing adds nothing`() {
        store.add("Fernweh")
        store.add("Heimweh")
        val result = store.addAll(listOf("FERNWEH", "heimweh"))
        assertTrue(result.added.isEmpty())
        assertEquals(2, result.alreadySaved.size)
        assertEquals(2, store.all().size)
    }

    @Test
    fun `empty list is a no-op`() {
        val result = store.addAll(emptyList())
        assertTrue(result.added.isEmpty())
        assertTrue(result.alreadySaved.isEmpty())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `duplicates within the input are saved once with distinct ids`() {
        val result = store.addAll(listOf("Fernweh", "fernweh", "Heimweh"))
        assertEquals(2, result.added.size)
        assertEquals(2, result.added.map { it.id }.toSet().size)
        assertTrue(result.alreadySaved.isEmpty())
    }

    @Test
    fun `context is stored on new words only`() {
        store.add("Fernweh", "old")
        val result = store.addAll(listOf("Fernweh", "Heimweh"), "ctx")
        assertEquals("ctx", result.added.single().context)
        assertEquals("old", result.alreadySaved.single().context)
    }
}
