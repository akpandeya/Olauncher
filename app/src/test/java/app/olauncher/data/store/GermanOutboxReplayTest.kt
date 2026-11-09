package app.olauncher.data.store

import android.content.SharedPreferences
import app.olauncher.data.GermanPrefsStore
import app.olauncher.data.Prefs
import app.olauncher.helper.GermanWordSync
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The replay policy over a fake [GermanWebApi] (#835, task 03): 2xx removes,
 * 400 drops to failed, 503/network keep with backoff, 429 honours Retry-After,
 * 401 keeps every entry and lapses the session, the 15-per-run cap, FIFO order
 * and the byte-identical retry (same key, same `today`). No network: every
 * connection is a canned fake.
 */
class GermanOutboxReplayTest {

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

    /** A queued word capture with a distinguishing text and capture day. */
    private fun enqueueWord(text: String, captureDay: Long): GermanOutboxEntry {
        val word = Word(
            id = text.hashCode().toLong(),
            text = text,
            context = "Satz mit $text.",
            box = 1,
            dueDay = captureDay,
            addedAt = now,
        )
        return outbox.enqueue("POST", GermanWordSync.WORDS_PATH, GermanWordSync.captureBody(word))
    }

    private fun run(api: GermanWebApi, onUnauthorized: () -> Unit = {}): GermanOutboxRunResult =
        runBlocking {
            GermanOutboxReplay.replayAll(outbox, api, TEST_TOKEN, onUnauthorized) { now }
        }

    @Test
    fun `a 2xx removes the entry`() {
        enqueueWord("Haus", 20345)
        val api = fakeApi(Canned(201, """{"id":"srv-1"}"""))

        val outcome = run(api)

        assertEquals(1, outcome.handledCount)
        assertEquals(null, outcome.stopReason)
        assertEquals(0, outbox.pendingCount())
        assertEquals(0, outbox.failedCount())
    }

    @Test
    fun `a 400 drops the entry to failed with the word text`() {
        enqueueWord("Fernweh", 20345)
        val api = fakeApi(Canned(400, """{"error":"today out of range"}"""))

        val outcome = run(api)

        assertEquals(1, outcome.handledCount)
        assertEquals(0, outbox.pendingCount())
        val failed = outbox.failed().single()
        assertEquals("400", failed.status)
        assertEquals("Fernweh", failed.text)
        // The word itself stays local: only the web sync was given up.
        assertEquals(now, failed.time)
    }

    @Test
    fun `a 503 keeps the entry with exponential backoff and the same key on both sends`() {
        val entry = enqueueWord("Haus", 20345)
        val api = fakeApi(Canned(503), Canned(503))

        val first = run(api)
        assertEquals(GermanReplayStopReason.SERVER_ERROR, first.stopReason)
        val deferredOnce = outbox.entries().single()
        assertEquals(entry.idempotencyKey, deferredOnce.idempotencyKey)
        assertEquals(1, deferredOnce.attempts)
        assertEquals(now + 30_000, deferredOnce.nextAttemptAt)

        now += 30_000 // the first backoff elapses; the retry "today" differs from capture day
        val second = run(api)
        assertEquals(GermanReplayStopReason.SERVER_ERROR, second.stopReason)
        val deferredTwice = outbox.entries().single()
        assertEquals(2, deferredTwice.attempts)
        assertEquals(now + 60_000, deferredTwice.nextAttemptAt)

        // Never a new key: both sends carried the key minted at enqueue time.
        assertEquals(2, api.connections.size)
        assertEquals(
            listOf(entry.idempotencyKey, entry.idempotencyKey),
            api.connections.map { it.getRequestProperty("Idempotency-Key") },
        )
        // And never a rewritten body: the capture day is replayed unchanged.
        assertEquals(
            listOf(20345L, 20345L),
            api.connections.map { JSONObject(it.writtenBody()).getLong("today") },
        )
    }

    @Test
    fun `a network failure keeps the entry with backoff like a 5xx`() {
        enqueueWord("Haus", 20345)
        val api = fakeApi(Canned(failResponseCodeWith = IOException("unreachable")))

        val outcome = run(api)

        assertEquals(GermanReplayStopReason.SERVER_ERROR, outcome.stopReason)
        val deferred = outbox.entries().single()
        assertEquals(1, deferred.attempts)
        assertEquals(now + 30_000, deferred.nextAttemptAt)
    }

    @Test
    fun `a 429 honours Retry-After over the backoff`() {
        enqueueWord("Haus", 20345)
        val api = fakeApi(Canned(429, retryAfter = "120"))

        val outcome = run(api)

        assertEquals(GermanReplayStopReason.RATE_LIMITED, outcome.stopReason)
        val deferred = outbox.entries().single()
        assertEquals(1, deferred.attempts)
        assertEquals(now + 120_000, deferred.nextAttemptAt)
    }

    @Test
    fun `a 429 without Retry-After falls back to the backoff`() {
        enqueueWord("Haus", 20345)
        val api = fakeApi(Canned(429))

        val outcome = run(api)

        assertEquals(GermanReplayStopReason.RATE_LIMITED, outcome.stopReason)
        assertEquals(now + 30_000, outbox.entries().single().nextAttemptAt)
    }

    @Test
    fun `a 401 keeps every entry and moves the session to Lapsed`() {
        val storage = FakeCredentialStorage()
        val (session, map) = session(storage)
        enqueueWord("Haus", 20345)
        enqueueWord("Tor", 20345)
        enqueueWord("Brett", 20345)
        val api = fakeApi(Canned(401, "Invalid token"))

        val outcome = run(api, onUnauthorized = { session.onUnauthorized() })

        assertEquals(GermanReplayStopReason.UNAUTHORIZED, outcome.stopReason)
        // Stopped at once: one send, every entry kept for after re-pairing.
        assertEquals(1, api.connections.size)
        assertEquals(3, outbox.pendingCount())
        assertEquals(GermanWebSessionState.Lapsed, session.state())
        assertEquals(true, map[Prefs.GERMAN_WEB_LAPSED])
        // The revoked credential was dropped exactly once.
        assertEquals(1, storage.clearCalls)
        assertEquals(null, storage.stored)
    }

    @Test
    fun `one run sends at most 15 entries`() {
        (1..20).forEach { index -> enqueueWord("w$index", 20345) }
        val api = fakeApi() // every send succeeds once the canned list is empty

        val outcome = run(api)

        assertEquals(GermanOutboxReplay.MAX_ENTRIES_PER_RUN, outcome.handledCount)
        assertEquals(15, api.connections.size)
        assertEquals(5, outbox.pendingCount())
    }

    @Test
    fun `entries are sent in FIFO order`() {
        enqueueWord("Haus", 20345)
        enqueueWord("Tor", 20345)
        enqueueWord("Brett", 20345)
        val api = fakeApi()

        run(api)

        assertEquals(
            listOf("Haus", "Tor", "Brett"),
            api.connections.map { JSONObject(it.writtenBody()).getString("text") },
        )
    }

    @Test
    fun `an entry deferred by an earlier run is not due before its backoff elapses`() {
        val entry = enqueueWord("Haus", 20345)
        outbox.retryIn(entry.idempotencyKey, 60)
        val api = fakeApi()

        now += 59_999
        val outcome = run(api)

        assertEquals(0, outcome.handledCount)
        assertEquals(0, api.connections.size)
        assertEquals(1, outbox.pendingCount())
    }

    @Test
    fun `decide maps every response family`() {
        assertEquals(GermanReplayDecision.RemoveEntry, GermanOutboxReplay.decide(response(201)))
        assertEquals(
            GermanReplayDecision.DropToFailed(400),
            GermanOutboxReplay.decide(response(400)),
        )
        assertEquals(
            GermanReplayDecision.DropToFailed(404),
            GermanOutboxReplay.decide(response(404)),
        )
        assertEquals(
            GermanReplayDecision.DropToFailed(409),
            GermanOutboxReplay.decide(response(409)),
        )
        assertEquals(
            GermanReplayDecision.StopRateLimited(17L),
            GermanOutboxReplay.decide(response(429, retryAfterSeconds = 17)),
        )
        assertEquals(
            GermanReplayDecision.StopRateLimited(null),
            GermanOutboxReplay.decide(response(429)),
        )
        assertEquals(GermanReplayDecision.StopUnauthorized, GermanOutboxReplay.decide(response(401)))
        assertEquals(GermanReplayDecision.StopServerError, GermanOutboxReplay.decide(response(500)))
        assertEquals(GermanReplayDecision.StopServerError, GermanOutboxReplay.decide(response(503)))
        assertEquals(
            GermanReplayDecision.StopServerError,
            GermanOutboxReplay.decide(GermanWebResponse.NetworkFailure("IOException")),
        )
    }

    @Test
    fun `backoff doubles from 30 seconds and caps at one hour`() {
        assertEquals(30L, GermanOutboxReplay.backoffSeconds(1))
        assertEquals(60L, GermanOutboxReplay.backoffSeconds(2))
        assertEquals(120L, GermanOutboxReplay.backoffSeconds(3))
        assertEquals(1_920L, GermanOutboxReplay.backoffSeconds(7))
        assertEquals(3_600L, GermanOutboxReplay.backoffSeconds(8))
        assertEquals(3_600L, GermanOutboxReplay.backoffSeconds(9))
        assertEquals(3_600L, GermanOutboxReplay.backoffSeconds(100))
    }

    /** One canned response; the fake API serves them in order, then 200s. */
    private data class Canned(
        val status: Int = 200,
        val body: String = "{}",
        val retryAfter: String? = null,
        val failResponseCodeWith: IOException? = null,
    )

    private fun response(status: Int, retryAfterSeconds: Int? = null): GermanWebResponse =
        GermanWebResponse.Http(status, "", retryAfterSeconds)

    private inner class FakeApi(vararg responses: Canned) : GermanWebApi(BASE) {
        val connections = mutableListOf<FakeOutboxConnection>()
        private val pending = responses.toMutableList()

        override fun openConnection(url: URL): HttpURLConnection =
            FakeOutboxConnection(url).also { connection ->
                val next = if (pending.isEmpty()) Canned() else pending.removeAt(0)
                connection.status = next.status
                connection.body = next.body
                connection.retryAfter = next.retryAfter
                connection.failResponseCodeWith = next.failResponseCodeWith
                connections += connection
            }
    }

    private fun fakeApi(vararg responses: Canned): FakeApi = FakeApi(*responses)

    private fun session(storage: FakeCredentialStorage): Pair<GermanWebSession, MutableMap<String, Any?>> {
        val map = mutableMapOf<String, Any?>()
        storage.stored = GermanDeviceCredential(TEST_TOKEN, DEVICE_ID, USERNAME)
        return GermanWebSession(storage, GermanPrefsStore(FakeSharedPreferences(map))) to map
    }

    /** In-memory stand-in for the Keystore-backed store; counts only clears. */
    private class FakeCredentialStorage : GermanDeviceCredentialStorage {
        var stored: GermanDeviceCredential? = null
        var clearCalls: Int = 0
            private set

        override fun save(credential: GermanDeviceCredential) {
            stored = credential
        }

        override fun read(): GermanDeviceCredential? = stored

        override fun isSet(): Boolean = stored != null

        override fun clear() {
            clearCalls++
            stored = null
        }
    }

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            removed.forEach { map.remove(it) }
            map.putAll(pending)
            removed.clear()
            pending.clear()
        }
    }

    private class FakeSharedPreferences(private val map: MutableMap<String, Any?>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    companion object {
        private const val BASE = "https://sync.example.com"

        /** Obviously synthetic (never a real device token), with the required prefix. */
        private const val TEST_TOKEN = "gfd_test_token_0001"
        private const val DEVICE_ID = "dev-42"
        private const val USERNAME = "ola-test-user-7"
    }
}

/** Canned exchange standing in for the network: no sockets in unit tests. */
private class FakeOutboxConnection(url: URL) : HttpURLConnection(url) {

    var status: Int = 200
    var body: String = "{}"
    var retryAfter: String? = null
    var failResponseCodeWith: IOException? = null

    private val out = ByteArrayOutputStream()

    override fun connect() {}

    /** The JDK HttpURLConnection rejects PATCH; Android's runtime and the server contract both use it. */
    private var fakeMethod: String = "GET"

    override fun setRequestMethod(method: String?) {
        fakeMethod = method ?: "GET"
    }

    override fun getRequestMethod(): String = fakeMethod

    override fun disconnect() {}

    override fun usingProxy(): Boolean = false

    override fun getOutputStream(): OutputStream = out

    override fun getResponseCode(): Int {
        failResponseCodeWith?.let { throw it }
        return status
    }

    override fun getHeaderField(name: String?): String? =
        if (name.equals("Retry-After", ignoreCase = true)) retryAfter else null

    override fun getInputStream(): InputStream {
        if (status !in 200..399) throw IOException("HTTP $status")
        return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    override fun getErrorStream(): InputStream? =
        if (status in 200..399) null else ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

    fun writtenBody(): String = out.toString("UTF-8")
}
