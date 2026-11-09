package app.olauncher.data.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The offline outbox for the German web app (#835, task 03): enqueue shape,
 * FIFO order, retry deferral, reload from disk, corrupt-entry tolerance and the
 * capped failed list. Everything runs in a fresh temporary folder per test.
 */
class GermanWebOutboxTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var outbox: GermanWebOutbox
    private var now: Long = 100_000L

    @Before
    fun setUp() {
        dir = tempFolder.newFolder()
        outbox = GermanWebOutbox(dir).apply { clockSupplier = { now } }
    }

    @Test
    fun `enqueue stores the request as captured and mints an ola idempotency key`() {
        val entry = outbox.enqueue("POST", "/api/german/words", """{"text":"Haus"}""")

        assertTrue(entry.idempotencyKey.startsWith("ola-"))
        // The server rejects keys outside [A-Za-z0-9._:-]{1,128}.
        assertTrue("key too long: ${entry.idempotencyKey}", entry.idempotencyKey.length <= 128)
        assertTrue(
            "key outside the server alphabet: ${entry.idempotencyKey}",
            entry.idempotencyKey.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._:-" }
        )
        assertEquals("POST", entry.method)
        assertEquals("/api/german/words", entry.path)
        // The body exactly as captured, not re-encoded.
        assertEquals("""{"text":"Haus"}""", entry.bodyJson)
        assertEquals(now, entry.createdAt)
        assertEquals(0, entry.attempts)
        // A fresh entry is due at once.
        assertEquals(now, entry.nextAttemptAt)
        assertEquals(listOf(entry), outbox.due())
    }

    @Test
    fun `two enqueues mint distinct keys`() {
        val first = outbox.enqueue("POST", "/api/german/words", null)
        val second = outbox.enqueue("POST", "/api/german/words", null)
        assertNotEquals(first.idempotencyKey, second.idempotencyKey)
    }

    @Test
    fun `entries keep FIFO order across three entries`() {
        val first = outbox.enqueue("POST", "/a", null)
        val second = outbox.enqueue("POST", "/b", null)
        val third = outbox.enqueue("POST", "/c", null)

        assertEquals(listOf(first, second, third), outbox.entries())
        assertEquals(listOf(first, second, third), outbox.due())
        assertEquals(3, outbox.pendingCount())
    }

    @Test
    fun `the outbox survives a reload from disk`() {
        val first = outbox.enqueue("POST", "/api/german/words", """{"text":"Haus"}""")
        now += 5_000
        val second = outbox.enqueue("POST", "/api/german/words", """{"text":"Tor"}""")

        val reloaded = GermanWebOutbox(dir).apply { clockSupplier = { now } }

        assertEquals(listOf(first, second), reloaded.entries())
        assertEquals(2, reloaded.pendingCount())
        assertEquals(listOf(first, second), reloaded.due())
    }

    @Test
    fun `retryIn defers the entry with one more attempt and the same key and body`() {
        val entry = outbox.enqueue("POST", "/api/german/words", """{"text":"Haus"}""")
        now += 10_000

        assertTrue(outbox.retryIn(entry.idempotencyKey, 30))

        val deferred = outbox.entries().single()
        assertEquals(entry.idempotencyKey, deferred.idempotencyKey)
        assertEquals(entry.bodyJson, deferred.bodyJson)
        assertEquals(1, deferred.attempts)
        assertEquals(now + 30_000, deferred.nextAttemptAt)
        // Not due before the backoff elapses, due the moment it does.
        assertTrue(outbox.due(now + 30_000 - 1).isEmpty())
        assertEquals(listOf(entry.idempotencyKey), outbox.due(now + 30_000).map { it.idempotencyKey })
        // An unknown key is not deferred and the queue is unchanged.
        assertTrue(!outbox.retryIn("ola-nonexistent", 30))
        assertEquals(1, outbox.pendingCount())
    }

    @Test
    fun `remove drops a synced entry and ignores an unknown key`() {
        val entry = outbox.enqueue("POST", "/api/german/words", null)

        assertTrue(outbox.remove(entry.idempotencyKey))
        assertEquals(0, outbox.pendingCount())
        assertTrue(!outbox.remove(entry.idempotencyKey))
    }

    @Test
    fun `dropToFailed records the entry instead of silently losing it`() {
        val entry = outbox.enqueue("POST", "/api/german/words", null)
        now += 7_000

        val failed = outbox.dropToFailed(entry.idempotencyKey, "400", "Fernweh")

        assertEquals(GermanOutboxFailedEntry(text = "Fernweh", status = "400", time = now), failed)
        assertEquals(0, outbox.pendingCount())
        assertEquals(1, outbox.failedCount())
        assertEquals(listOf(failed), outbox.failed())
        // Recorded on disk too, not just in memory.
        assertEquals(listOf(failed), GermanWebOutbox(dir).failed())
    }

    @Test
    fun `a corrupt entry is skipped and recorded while the others are kept`() {
        val raw = """
            {"version":1,"entries":[
              {"idempotencyKey":"ola-1","method":"POST","path":"/api/german/words","bodyJson":"{}","createdAt":1,"attempts":0,"nextAttemptAt":1},
              {"idempotencyKey":"ola-2","method":"POST"},
              {"idempotencyKey":"ola-3","method":"POST","path":"/api/german/review/9","bodyJson":"{}","createdAt":2,"attempts":0,"nextAttemptAt":2}
            ],"failed":[]}
        """.trimIndent()
        File(dir, GermanWebOutbox.FILE_NAME).writeText(raw)

        assertEquals(listOf("ola-1", "ola-3"), outbox.entries().map { it.idempotencyKey })
        val failed = outbox.failed()
        assertEquals(1, failed.size)
        assertEquals(GermanWebOutbox.STATUS_CORRUPT, failed[0].status)
        assertTrue(failed[0].text.contains("ola-2"))
        // The clean-up is persisted: a reload no longer re-drops the corrupt line.
        assertEquals(listOf("ola-1", "ola-3"), GermanWebOutbox(dir).entries().map { it.idempotencyKey })
        assertEquals(1, GermanWebOutbox(dir).failedCount())
    }

    @Test
    fun `an entry whose key breaks the server alphabet is corrupt too`() {
        val raw = """
            {"version":1,"entries":[
              {"idempotencyKey":"bad key with spaces","method":"POST","path":"/x","bodyJson":"{}","createdAt":1,"attempts":0,"nextAttemptAt":1}
            ],"failed":[]}
        """.trimIndent()
        File(dir, GermanWebOutbox.FILE_NAME).writeText(raw)

        assertEquals(0, outbox.pendingCount())
        assertEquals(GermanWebOutbox.STATUS_CORRUPT, outbox.failed().single().status)
    }

    @Test
    fun `a file that is not a JSON object is dropped as a whole and recorded`() {
        File(dir, GermanWebOutbox.FILE_NAME).writeText("[1,2,3]")

        assertEquals(0, outbox.pendingCount())
        assertEquals(0, outbox.entries().size)
        assertEquals(1, outbox.failedCount())
        assertEquals(GermanWebOutbox.STATUS_CORRUPT, outbox.failed().single().status)
    }

    @Test
    fun `a missing file is an empty queue, not an error`() {
        assertEquals(0, outbox.pendingCount())
        assertEquals(0, outbox.failedCount())
        assertTrue(outbox.entries().isEmpty())
    }

    @Test
    fun `the failed list keeps the newest 50`() {
        val keys = (0 until 60).map { index ->
            outbox.enqueue("POST", "/p$index", null).idempotencyKey
        }
        now = 1_000_000
        keys.forEachIndexed { index, key ->
            outbox.dropToFailed(key, "400", "w$index", now + index)
        }

        val failed = outbox.failed()
        assertEquals(GermanWebOutbox.MAX_FAILED, failed.size)
        // The 10 oldest are gone, the newest 50 (w10..w59) are kept in order.
        assertEquals("w10", failed.first().text)
        assertEquals("w59", failed.last().text)
        assertTrue(failed.none { it.text in setOf("w0", "w9") })
    }
}
