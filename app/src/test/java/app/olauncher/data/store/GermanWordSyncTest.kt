package app.olauncher.data.store

import app.olauncher.helper.GermanWordSync
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The bridge from a local capture to a queued web write (#835, task 03): the
 * `POST /words` body fields (a distinguishing capture day, meaning/meaningSource
 * only when non-blank, never listIds) and the session gating — signed in and
 * lapsed queue, signed out and German-off do not.
 */
class GermanWordSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var outbox: GermanWebOutbox

    @Before
    fun setUp() {
        dir = tempFolder.newFolder()
        outbox = GermanWebOutbox(dir)
    }

    private fun word(
        text: String,
        captureDay: Long,
        meaning: String = "",
        meaningSource: String = "",
        contextSource: String = ContextSource.CAPTURED,
        listIds: List<String> = emptyList(),
    ) = Word(
        id = text.hashCode().toLong(),
        text = text,
        context = "Satz mit $text.",
        meaning = meaning,
        meaningSource = meaningSource,
        box = 1,
        dueDay = captureDay,
        addedAt = 1_000L,
        listIds = listIds,
        contextSource = contextSource,
    )

    @Test
    fun `the capture body carries the capture day and only the fields the server accepts`() {
        val body = JSONObject(
            GermanWordSync.captureBody(
                word(
                    "Fernweh",
                    captureDay = 20345,
                    contextSource = ContextSource.CAPTURED,
                    listIds = listOf("local-list-9"),
                )
            )
        )

        assertEquals("Fernweh", body.getString("text"))
        assertEquals("Satz mit Fernweh.", body.getString("context"))
        assertEquals(ContextSource.CAPTURED, body.getString("contextSource"))
        // The distinguishing capture day, not the day of a later replay.
        assertEquals(20345L, body.getLong("today"))
        // Optional fields stay out when blank, and local list ids never reach the server.
        assertFalse(body.has("meaning"))
        assertFalse(body.has("meaningSource"))
        assertFalse(body.has("listIds"))
        // Exactly the fields above, nothing more: an unknown field is a 400 on the server.
        assertEquals(4, body.length())
    }

    @Test
    fun `meaning and meaningSource go in only when the meaning is non-blank`() {
        val withMeaning = JSONObject(
            GermanWordSync.captureBody(word("Heimweh", 20346, meaning = "wanderlust", meaningSource = "wiktionary"))
        )
        assertEquals("wanderlust", withMeaning.getString("meaning"))
        assertEquals("wiktionary", withMeaning.getString("meaningSource"))

        val blankMeaning = JSONObject(
            GermanWordSync.captureBody(word("Sehnsucht", 20347, meaning = "   ", meaningSource = "wiktionary"))
        )
        assertFalse(blankMeaning.has("meaning"))
        assertFalse(blankMeaning.has("meaningSource"))
    }

    @Test
    fun `queueCaptures queues each new capture while signed in, in order`() {
        val words = listOf(word("Haus", 20340), word("Tor", 20341))

        val queued = GermanWordSync.queueCaptures(
            outbox,
            germanEnabled = true,
            state = GermanWebSessionState.SignedIn(username = "anna-hh", deviceId = "dev-1"),
            words = words,
        )

        assertEquals(2, queued.size)
        assertEquals(2, outbox.pendingCount())
        val entries = outbox.entries()
        // Wiring checked with distinguishing values: each entry's body is its own word.
        assertEquals(listOf("Haus", "Tor"), entries.map { JSONObject(it.bodyJson).getString("text") })
        assertEquals(listOf(20340L, 20341L), entries.map { JSONObject(it.bodyJson).getLong("today") })
        entries.forEach {
            assertEquals("POST", it.method)
            assertEquals(GermanWordSync.WORDS_PATH, it.path)
        }
    }

    @Test
    fun `queueCaptures queues while lapsed so captures sync after re-pairing`() {
        val queued = GermanWordSync.queueCaptures(
            outbox,
            germanEnabled = true,
            state = GermanWebSessionState.Lapsed,
            words = listOf(word("Haus", 20340)),
        )

        assertEquals(1, queued.size)
        assertEquals(1, outbox.pendingCount())
    }

    @Test
    fun `signed out does not enqueue - local only`() {
        val queued = GermanWordSync.queueCaptures(
            outbox,
            germanEnabled = true,
            state = GermanWebSessionState.SignedOut,
            words = listOf(word("Haus", 20340)),
        )

        assertTrue(queued.isEmpty())
        assertEquals(0, outbox.pendingCount())
    }

    @Test
    fun `German disabled does not enqueue`() {
        val queued = GermanWordSync.queueCaptures(
            outbox,
            germanEnabled = false,
            state = GermanWebSessionState.SignedIn(username = "anna-hh", deviceId = "dev-1"),
            words = listOf(word("Haus", 20340)),
        )

        assertTrue(queued.isEmpty())
        assertEquals(0, outbox.pendingCount())
    }

    @Test
    fun `shouldQueue is german enabled and not signed out`() {
        assertTrue(GermanWordSync.shouldQueue(true, GermanWebSessionState.SignedIn("u", "d")))
        assertTrue(GermanWordSync.shouldQueue(true, GermanWebSessionState.Lapsed))
        assertFalse(GermanWordSync.shouldQueue(true, GermanWebSessionState.SignedOut))
        assertFalse(GermanWordSync.shouldQueue(false, GermanWebSessionState.SignedIn("u", "d")))
        assertFalse(GermanWordSync.shouldQueue(false, GermanWebSessionState.Lapsed))
    }
}
