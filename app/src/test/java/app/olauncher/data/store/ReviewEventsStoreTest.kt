package app.olauncher.data.store

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ReviewEventsStoreTest {

    private lateinit var tempDir: File
    private lateinit var store: ReviewEventsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("review_events_test").toFile()
        store = ReviewEventsStore(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `empty store returns empty list`() {
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `append assigns increasing ids and keeps insertion order`() {
        val first = store.append(wordId = 10, result = ReviewEvent.Result.KNEW, boxBefore = 1, boxAfter = 2, reviewedAt = 100L)
        val second = store.append(wordId = 11, result = ReviewEvent.Result.AGAIN, boxBefore = 3, boxAfter = 1, reviewedAt = 200L)

        assertEquals(1L, first.id)
        assertEquals(2L, second.id)
        assertEquals(listOf(first, second), store.all())
    }

    @Test
    fun `events persist across store instances`() {
        store.append(wordId = 10, result = ReviewEvent.Result.ALMOST, boxBefore = 2, boxAfter = 2, reviewedAt = 100L)

        val reopened = ReviewEventsStore(tempDir)
        val events = reopened.all()
        assertEquals(1, events.size)
        assertEquals(ReviewEvent(1L, 10L, 100L, "almost", 2, 2), events[0])
    }

    @Test
    fun `decode skips corrupt and unknown-result entries`() {
        val raw = """
            [
                {"id": 1, "wordId": 5, "reviewedAt": 10, "result": "knew", "boxBefore": 1, "boxAfter": 2},
                {"id": 2, "wordId": 6, "reviewedAt": 20, "result": "bogus", "boxBefore": 1, "boxAfter": 1},
                {"id": 3, "wordId": 7, "reviewedAt": 30, "result": "again", "boxBefore": 4, "boxAfter": 1},
                {"nonsense": true},
                {"id": 4, "wordId": 8, "reviewedAt": 40, "result": "almost", "boxBefore": 2, "boxAfter": 2}
            ]
        """.trimIndent()
        val events = ReviewEventsCodec.decode(raw)
        assertEquals(
            listOf(
                ReviewEvent(1L, 5L, 10L, "knew", 1, 2),
                ReviewEvent(3L, 7L, 30L, "again", 4, 1),
                ReviewEvent(4L, 8L, 40L, "almost", 2, 2),
            ),
            events,
        )
    }

    @Test
    fun `decode of garbage returns empty and the store keeps appending`() {
        tempDir.resolve("review_events.json").writeText("not json at all")
        assertTrue(store.all().isEmpty())
        val appended = store.append(wordId = 1, result = ReviewEvent.Result.KNEW, boxBefore = 1, boxAfter = 2, reviewedAt = 5L)
        assertEquals(listOf(appended), store.all())
    }

    @Test
    fun `old JSON without source decodes with null source`() {
        val oldJson = """
            [
                {"id": 1, "wordId": 10, "reviewedAt": 100, "result": "knew", "boxBefore": 1, "boxAfter": 2},
                {"id": 2, "wordId": 11, "reviewedAt": 200, "result": "again", "boxBefore": 3, "boxAfter": 1}
            ]
        """.trimIndent()
        val decoded = ReviewEventsCodec.decode(oldJson)
        assertEquals(2, decoded.size)
        assertEquals(ReviewEvent(1L, 10L, 100L, "knew", 1, 2, null), decoded[0])
        assertEquals(ReviewEvent(2L, 11L, 200L, "again", 3, 1, null), decoded[1])
    }

    @Test
    fun `new JSON with source round-trips`() {
        val events = listOf(
            ReviewEvent(1L, 10L, 100L, "knew", 1, 2, "drill:prepositions"),
            ReviewEvent(2L, 11L, 200L, "again", 3, 1, "drill:conjugation"),
            ReviewEvent(3L, 12L, 300L, "almost", 2, 2, null),
        )
        val json = ReviewEventsCodec.encode(events)
        val decoded = ReviewEventsCodec.decode(json)
        assertEquals(events, decoded)
    }

    @Test
    fun `append with source persists and survives reload`() {
        val event = store.append(
            wordId = 42,
            result = ReviewEvent.Result.KNEW,
            boxBefore = 2,
            boxAfter = 3,
            reviewedAt = 500L,
            source = "drill:pronouns",
        )
        assertEquals("drill:pronouns", event.source)

        val reloaded = ReviewEventsStore(tempDir).all()
        assertEquals(1, reloaded.size)
        assertEquals(event, reloaded[0])
    }
}
