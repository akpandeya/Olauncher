package app.olauncher.data.store

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The grading-to-outbox mapping for pause-screen web words (epic #835,
 * task 04): "knew" stays `knew`, "again" becomes the API's `no`, with a
 * distinguishing word id and day so a swapped mapping cannot pass. No
 * network, no Context — the outbox lives in a fresh temporary folder.
 */
class GermanWebReviewTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var outbox: GermanWebOutbox

    @Before
    fun setUp() {
        dir = tempFolder.newFolder()
        outbox = GermanWebOutbox(dir)
    }

    @Test
    fun `knew and again map to their API results with the tapped day`() {
        val knew = GermanWebReview.enqueueRating(outbox, wordId = "w-9f3", knewIt = true, today = 20401L)
        val again = GermanWebReview.enqueueRating(outbox, wordId = "w-9f3", knewIt = false, today = 20401L)

        assertEquals("POST", knew.method)
        assertEquals("POST", again.method)
        assertEquals("/api/german/review/w-9f3", knew.path)
        assertEquals("/api/german/review/w-9f3", again.path)

        val knewBody = JSONObject(knew.bodyJson)
        assertEquals("knew", knewBody.getString("result"))
        assertEquals(20401L, knewBody.getLong("today"))
        assertEquals(2, knewBody.length())

        val againBody = JSONObject(again.bodyJson)
        assertEquals("no", againBody.getString("result"))
        assertEquals(20401L, againBody.getLong("today"))
        assertEquals(2, againBody.length())

        // One idempotency key per tap, both in the server's alphabet.
        assertNotEquals(knew.idempotencyKey, again.idempotencyKey)
        assertTrue(knew.idempotencyKey, knew.idempotencyKey.startsWith("ola-"))
        assertTrue(again.idempotencyKey, again.idempotencyKey.startsWith("ola-"))
    }

    @Test
    fun `the queue persists both writes in tap order`() {
        GermanWebReview.enqueueRating(outbox, wordId = "w-9f3", knewIt = true, today = 20400L)
        GermanWebReview.enqueueRating(outbox, wordId = "w-7b2", knewIt = false, today = 20401L)

        val entries = GermanWebOutbox(dir).entries()
        assertEquals(2, entries.size)
        assertEquals("/api/german/review/w-9f3", entries[0].path)
        assertEquals("knew", JSONObject(entries[0].bodyJson).getString("result"))
        assertEquals("/api/german/review/w-7b2", entries[1].path)
        assertEquals("no", JSONObject(entries[1].bodyJson).getString("result"))
        assertEquals(20401L, JSONObject(entries[1].bodyJson).getLong("today"))
    }
}
