package app.olauncher.wellbeing

import androidx.work.ListenableWorker.Result
import app.olauncher.data.store.WiktionaryApi
import app.olauncher.data.store.WordsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class WordLookupWorkerTest {

    private lateinit var tempDir: File
    private lateinit var store: WordsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("worker_test").toFile()
        store = WordsStore(tempDir)
        store.epochDaySupplier = { 19500L }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `unknown wordId returns success and does not call fetch`() {
        var fetchCalled = false
        val result = WordLookupWorker.lookupWord(
            wordId = 999999L,
            store = store,
            runAttemptCount = 0
        ) {
            fetchCalled = true
            WiktionaryApi.FetchOutcome.NotFound
        }

        assertEquals(Result.success(), result)
        assertTrue(!fetchCalled)
    }

    @Test
    fun `word with user meaning is never overwritten and fetch is skipped`() {
        val word = store.add("Fernweh")
        store.update(word.copy(meaning = "Meine Definition", meaningSource = "user"))

        var fetchCalled = false
        val result = WordLookupWorker.lookupWord(
            wordId = word.id,
            store = store,
            runAttemptCount = 0
        ) {
            fetchCalled = true
            WiktionaryApi.FetchOutcome.Success("Wiktionary Definition")
        }

        assertEquals(Result.success(), result)
        assertTrue(!fetchCalled)
        val after = store.get(word.id)
        assertEquals("Meine Definition", after?.meaning)
        assertEquals("user", after?.meaningSource)
    }

    @Test
    fun `tries candidates in order and saves meaning with meaningSource = wiktionary on first success`() {
        val word = store.add("fernweh") // Candidates: ["fernweh", "Fernweh"]
        val attempted = mutableListOf<String>()

        val result = WordLookupWorker.lookupWord(
            wordId = word.id,
            store = store,
            runAttemptCount = 0
        ) { candidate ->
            attempted.add(candidate)
            if (candidate == "Fernweh") {
                WiktionaryApi.FetchOutcome.Success("wanderlust (desire to travel)")
            } else {
                WiktionaryApi.FetchOutcome.NotFound
            }
        }

        assertEquals(Result.success(), result)
        assertEquals(listOf("fernweh", "Fernweh"), attempted)

        val updated = store.get(word.id)
        assertEquals("wanderlust (desire to travel)", updated?.meaning)
        assertEquals("wiktionary", updated?.meaningSource)
    }

    @Test
    fun `404 for all candidates results in success with no meaning saved`() {
        val word = store.add("fantasiewortxyz")
        val attempted = mutableListOf<String>()

        val result = WordLookupWorker.lookupWord(
            wordId = word.id,
            store = store,
            runAttemptCount = 0
        ) { candidate ->
            attempted.add(candidate)
            WiktionaryApi.FetchOutcome.NotFound
        }

        assertEquals(Result.success(), result)
        assertTrue(attempted.isNotEmpty())

        val updated = store.get(word.id)
        assertEquals("", updated?.meaning)
        assertEquals("", updated?.meaningSource)
    }

    @Test
    fun `IO error on attempts 0 and 1 returns retry`() {
        val word = store.add("Haus")

        val attempt0 = WordLookupWorker.lookupWord(
            wordId = word.id,
            store = store,
            runAttemptCount = 0
        ) {
            WiktionaryApi.FetchOutcome.IoError(IOException("Connection timed out"))
        }
        assertEquals(Result.retry(), attempt0)

        val attempt1 = WordLookupWorker.lookupWord(
            wordId = word.id,
            store = store,
            runAttemptCount = 1
        ) {
            WiktionaryApi.FetchOutcome.IoError(IOException("Connection reset"))
        }
        assertEquals(Result.retry(), attempt1)
    }

    @Test
    fun `IO error on attempt 2 returns failure after 3 attempts`() {
        val word = store.add("Haus")

        val attempt2 = WordLookupWorker.lookupWord(
            wordId = word.id,
            store = store,
            runAttemptCount = 2
        ) {
            WiktionaryApi.FetchOutcome.IoError(IOException("Host unreachable"))
        }
        assertEquals(Result.failure(), attempt2)
    }
}
