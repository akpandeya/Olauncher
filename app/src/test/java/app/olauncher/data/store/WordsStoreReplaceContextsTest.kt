package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WordsStoreReplaceContextsTest {

    private lateinit var tempDir: File
    private lateinit var store: WordsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("words_replace_test").toFile()
        store = WordsStore(tempDir)
        store.epochDaySupplier = { 19500L }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun wordsFile(): File = File(tempDir, "words.json")

    private fun storedWord(id: Long): Word = store.get(id)!!

    @Test
    fun `codec round-trips contextSource together with other non-default fields`() {
        val original = Word(
            id = 7L,
            text = "Regierung",
            context = "Die Regierung hat neue Regeln beschlossen.",
            contextSource = ContextSource.USER,
            meaning = "government",
            meaningSource = "user",
            box = 3,
            dueDay = 20_270_123L,
            addedAt = 555L,
            learnedAt = 999L,
            listIds = listOf("politik")
        )
        val decoded = WordsCodec.decode(WordsCodec.encodeToJson(listOf(original)).toString()).words
        assertEquals(listOf(original), decoded)
    }

    @Test
    fun `legacy json without contextSource decodes every word as captured`() {
        val raw = """
            [
                {"id": 1, "text": "a", "context": "Ein Satz.", "dueDay": 1, "addedAt": 1},
                {"id": 2, "text": "b", "dueDay": 2, "addedAt": 2}
            ]
        """.trimIndent()
        val words = WordsCodec.decode(raw).words
        assertEquals(2, words.size)
        assertTrue(words.all { it.contextSource == ContextSource.CAPTURED })
    }

    @Test
    fun `blank and non-string contextSource values decode as captured`() {
        val raw = """
            [
                {"id": 1, "text": "a", "contextSource": "  ", "dueDay": 1, "addedAt": 1},
                {"id": 2, "text": "b", "contextSource": null, "dueDay": 2, "addedAt": 2},
                {"id": 3, "text": "c", "contextSource": 5, "dueDay": 3, "addedAt": 3}
            ]
        """.trimIndent()
        val words = WordsCodec.decode(raw).words
        assertTrue(words.all { it.contextSource == ContextSource.CAPTURED })
    }

    @Test
    fun `existing words file without the key keeps its bytes and decodes as captured`() {
        // The migration is decode-only: no rewrite of a legacy file is needed.
        val legacy = """
            [
                {"id": 1, "text": "Regierung", "context": "die neue Regierung", "dueDay": 1, "addedAt": 1}
            ]
        """.trimIndent()
        wordsFile().writeText(legacy)
        val store2 = WordsStore(tempDir)
        val word = store2.get(1L)!!
        assertEquals("die neue Regierung", word.context)
        assertEquals(ContextSource.CAPTURED, word.contextSource)
        assertEquals(legacy, wordsFile().readText())
    }

    @Test
    fun `replaces a weak captured context and marks it auto`() {
        val weak = store.add("Regierung", "die neue Regierung")

        val replaced = store.replaceWeakContexts(
            mapOf(weak.id to ContextReplacement("die neue Regierung", "Die Regierung hat am Montag neue Regeln beschlossen."))
        )

        assertEquals(setOf(weak.id), replaced)
        val after = storedWord(weak.id)
        assertEquals("Die Regierung hat am Montag neue Regeln beschlossen.", after.context)
        assertEquals(ContextSource.AUTO, after.contextSource)
    }

    @Test
    fun `skips when the current context changed since planning`() {
        val word = store.add("Regierung", "die neue Regierung")
        store.update(word.copy(context = "Der Nutzer tippte das.", contextSource = ContextSource.USER))

        val replaced = store.replaceWeakContexts(
            mapOf(word.id to ContextReplacement("die neue Regierung", "Die Regierung hat am Montag neue Regeln beschlossen."))
        )

        assertTrue(replaced.isEmpty())
        assertEquals("Der Nutzer tippte das.", storedWord(word.id).context)
    }

    @Test
    fun `skips a user-marked context even when it matches the expectation`() {
        val word = store.add("Regierung", "die neue Regierung")
        store.update(word.copy(contextSource = ContextSource.USER))

        val replaced = store.replaceWeakContexts(
            mapOf(word.id to ContextReplacement("die neue Regierung", "Die Regierung hat am Montag neue Regeln beschlossen."))
        )

        assertTrue(replaced.isEmpty())
        assertEquals(ContextSource.USER, storedWord(word.id).contextSource)
    }

    @Test
    fun `skips a context that is not weak anymore`() {
        val strong = "Die Regierung hat am Montag neue Regeln beschlossen."
        val word = store.add("Regierung", strong)

        val replaced = store.replaceWeakContexts(
            mapOf(word.id to ContextReplacement(strong, "Die Regierung plant neue Regeln für das Jahr."))
        )

        assertTrue(replaced.isEmpty())
        assertEquals(strong, storedWord(word.id).context)
    }

    @Test
    fun `skips a replacement that would itself be weak`() {
        val word = store.add("Regierung", "die neue Regierung")

        val replaced = store.replaceWeakContexts(
            mapOf(word.id to ContextReplacement("die neue Regierung", "Regierung"))
        )

        assertTrue(replaced.isEmpty())
        assertEquals("die neue Regierung", storedWord(word.id).context)
    }

    @Test
    fun `keeps box and dueDay changed between planning and apply`() {
        val word = store.add("Regierung", "die neue Regierung")
        // A review after the caller planned the rewrite moved the word on.
        store.update(word.copy(box = 4, dueDay = 20_270_123L))

        val replaced = store.replaceWeakContexts(
            mapOf(word.id to ContextReplacement("die neue Regierung", "Die Regierung hat am Montag neue Regeln beschlossen."))
        )

        assertEquals(setOf(word.id), replaced)
        val after = storedWord(word.id)
        assertEquals("Die Regierung hat am Montag neue Regeln beschlossen.", after.context)
        assertEquals(ContextSource.AUTO, after.contextSource)
        assertEquals(4, after.box)
        assertEquals(20_270_123L, after.dueDay)
    }

    @Test
    fun `returns exactly the ids it replaced`() {
        val replacedWord = store.add("Regierung", "die neue Regierung")
        val editedWord = store.add("Haus", "die neue haus")
        store.update(editedWord.copy(contextSource = ContextSource.USER))
        val strongWord = store.add("Katze", "Die Katze schläft auf dem Sofa.")

        val result = store.replaceWeakContexts(
            mapOf(
                replacedWord.id to ContextReplacement("die neue Regierung", "Die Regierung hat am Montag neue Regeln beschlossen."),
                editedWord.id to ContextReplacement("die neue haus", "Das Haus steht am See."),
                strongWord.id to ContextReplacement("Die Katze schläft auf dem Sofa.", "Eine Katze sitzt auf dem Dach."),
            )
        )

        assertEquals(setOf(replacedWord.id), result)
        assertNotEquals("Eine Katze sitzt auf dem Dach.", storedWord(strongWord.id).context)
    }

    @Test
    fun `empty replacements map writes nothing`() {
        assertTrue(store.replaceWeakContexts(emptyMap()).isEmpty())
        assertFalse(wordsFile().exists())
    }

    @Test
    fun `unknown ids are ignored`() {
        val replaced = store.replaceWeakContexts(
            mapOf(42L to ContextReplacement("egal", "Die Regierung hat am Montag neue Regeln beschlossen."))
        )
        assertTrue(replaced.isEmpty())
        assertFalse(wordsFile().exists())
    }

    @Test
    fun `add with a new context resets a user-marked word to captured`() {
        val word = store.add("Regierung", "die neue Regierung")
        store.update(word.copy(contextSource = ContextSource.USER))

        val updated = store.add("Regierung", "Die Regierung hat am Montag neue Regeln beschlossen.")

        assertEquals(word.id, updated.id)
        assertEquals("Die Regierung hat am Montag neue Regeln beschlossen.", updated.context)
        assertEquals(ContextSource.CAPTURED, updated.contextSource)
        assertEquals(ContextSource.CAPTURED, storedWord(word.id).contextSource)
    }
}
