package app.olauncher.helper

import app.olauncher.data.store.ContextSource
import app.olauncher.data.store.GermanWordIdMap
import app.olauncher.data.store.GermanWebOutbox
import app.olauncher.data.store.WordsStore
import app.olauncher.data.store.Word
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The second half of the capture sync (the meaning that resolves after the
 * word was created): the PATCH body and path shape, and the gating — a blank
 * meaning or a word whose server row is unknown queues nothing, and the
 * `onWordCreated` catch-up pushes a meaning that resolved while the capture
 * POST was still unsent. Context-free core, the GermanWordSyncTest pattern.
 */
class GermanMeaningSyncTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var outbox: GermanWebOutbox
    private lateinit var idMap: GermanWordIdMap
    private lateinit var store: WordsStore

    private val serverUuid = "9f1c2b3a-1111-4222-8333-444455556666"

    @Before
    fun setUp() {
        dir = tempFolder.newFolder()
        outbox = GermanWebOutbox(dir)
        idMap = GermanWordIdMap(dir)
        store = WordsStore(dir).apply { epochDaySupplier = { 19500L } }
    }

    private fun word(
        text: String = "Fernweh",
        meaning: String = "wanderlust",
        meaningSource: String = "wiktionary",
    ) = Word(
        id = text.hashCode().toLong(),
        text = text,
        context = "Satz mit $text.",
        meaning = meaning,
        meaningSource = meaningSource,
        box = 1,
        dueDay = 20345,
        addedAt = 1_000L,
        contextSource = ContextSource.CAPTURED,
    )

    @Test
    fun `the patch body carries meaning and meaningSource and nothing else`() {
        val body = JSONObject(GermanMeaningSync.patchBody(word()))

        assertEquals("wanderlust", body.getString("meaning"))
        assertEquals("wiktionary", body.getString("meaningSource"))
        // Exactly the two fields the server's word edit accepts here: an unknown field is a 400.
        assertEquals(2, body.length())
    }

    @Test
    fun `a blank meaningSource stays out of the patch body`() {
        val body = JSONObject(GermanMeaningSync.patchBody(word(meaningSource = "  ")))

        assertEquals("wanderlust", body.getString("meaning"))
        assertFalse(body.has("meaningSource"))
        assertEquals(1, body.length())
    }

    @Test
    fun `fields are truncated to the server's caps so the write can never 400`() {
        val longMeaning = "x".repeat(GermanMeaningSync.MEANING_MAX + 500)
        val longSource = "y".repeat(GermanMeaningSync.SOURCE_MAX + 500)

        val body = JSONObject(GermanMeaningSync.patchBody(word(meaning = longMeaning, meaningSource = longSource)))

        assertEquals(GermanMeaningSync.MEANING_MAX, body.getString("meaning").length)
        assertEquals(GermanMeaningSync.SOURCE_MAX, body.getString("meaningSource").length)
    }

    @Test
    fun `the patch path is the words path plus the server id`() {
        assertEquals("/api/german/words/$serverUuid", GermanMeaningSync.patchPath(serverUuid))
    }

    @Test
    fun `push queues one PATCH when the word's server row is known`() {
        val w = word()
        idMap.put(w.id, serverUuid)

        assertTrue(GermanMeaningSync.push(idMap, outbox, w))

        val entry = outbox.entries().single()
        assertEquals("PATCH", entry.method)
        assertEquals(GermanMeaningSync.patchPath(serverUuid), entry.path)
        assertEquals(GermanMeaningSync.patchBody(w), entry.bodyJson)
    }

    @Test
    fun `push queues nothing when the meaning is blank`() {
        idMap.put(word().id, serverUuid)

        assertFalse(GermanMeaningSync.push(idMap, outbox, word(meaning = "   ")))
        assertEquals(0, outbox.pendingCount())
    }

    @Test
    fun `push queues nothing when the capture POST has not succeeded yet`() {
        val w = word()

        assertFalse(GermanMeaningSync.push(idMap, outbox, w))

        assertEquals(0, outbox.pendingCount())
        assertNull(idMap.serverIdFor(w.id))
    }

    @Test
    fun `onWordCreated records the mapping and catches up on a meaning that resolved early`() {
        val w = store.add("Fernweh")
        store.update(w.copy(meaning = "wanderlust", meaningSource = "wiktionary"))

        assertTrue(GermanMeaningSync.onWordCreated(idMap, outbox, store, w.id, serverUuid))

        assertEquals(serverUuid, idMap.serverIdFor(w.id))
        val entry = outbox.entries().single()
        assertEquals("PATCH", entry.method)
        assertEquals(GermanMeaningSync.patchPath(serverUuid), entry.path)
        assertEquals("wanderlust", JSONObject(entry.bodyJson).getString("meaning"))
    }

    @Test
    fun `onWordCreated records the mapping without queueing when the meaning is still blank`() {
        val w = store.add("Sehnsucht")

        assertFalse(GermanMeaningSync.onWordCreated(idMap, outbox, store, w.id, serverUuid))

        // The mapping is kept — the later WordLookupWorker push needs it.
        assertEquals(serverUuid, idMap.serverIdFor(w.id))
        assertEquals(0, outbox.pendingCount())
    }

    @Test
    fun `onWordCreated ignores a local id the store does not know`() {
        assertFalse(GermanMeaningSync.onWordCreated(idMap, outbox, store, 999_999L, serverUuid))

        assertEquals(0, outbox.pendingCount())
    }
}
