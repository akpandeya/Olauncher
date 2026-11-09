package app.olauncher.data.store

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.json.JSONObject

/**
 * The due-set refresh (epic #835, task 04): the API response parsers
 * (complete-or-nothing) and the refresh flow over a fake connection — no
 * network, a fresh temporary folder per test, an obviously synthetic token.
 */
class GermanDueRefreshTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dir: File
    private lateinit var cache: GermanDueCache
    private lateinit var outbox: GermanWebOutbox

    @Before
    fun setUp() {
        dir = tempFolder.newFolder()
        cache = GermanDueCache(dir)
        outbox = GermanWebOutbox(dir)
    }

    private fun dueBody(vararg words: JSONObject): String =
        JSONObject().put("words", org.json.JSONArray().also { array -> words.forEach { array.put(it) } }).toString()

    private fun dueWord(id: String, box: Int = 3, dueDay: Long = 20400L): JSONObject =
        JSONObject()
            .put("id", id)
            .put("text", "Wort $id")
            .put("context", "Satz mit $id")
            .put("meaning", "Bedeutung $id")
            .put("box", box)
            .put("dueDay", dueDay)

    // --- response parsing ---------------------------------------------------

    @Test
    fun `the due parser accepts a complete response`() {
        val words = GermanDueApi.decodeDueWords(dueBody(dueWord("w-9f3"), dueWord("w-9f4", box = 7, dueDay = 20402L)))
        assertEquals(listOf("w-9f3", "w-9f4"), words?.map { it.id })
        assertEquals(7, words?.get(1)?.box)
        assertEquals(20402L, words?.get(1)?.dueDay)
        assertEquals("Satz mit w-9f3", words?.get(0)?.context)
    }

    @Test
    fun `the due parser rejects every partial variant`() {
        val valid = dueWord("w-9f3")
        val rejected = listOf(
            // Missing id.
            JSONObject(valid.toString()).also { it.remove("id") },
            // Blank id and blank text.
            dueWord(" "),
            dueWord("w-1").put("text", " "),
            // Box outside 1..7.
            dueWord("w-1", box = 0),
            dueWord("w-1", box = 8),
            // Non-integer dueDay.
            dueWord("w-1").put("dueDay", "20401"),
            dueWord("w-1").put("dueDay", 20401.5),
        )
        rejected.forEach { body ->
            assertNull("body '$body'", GermanDueApi.decodeDueWords(dueBody(body)))
        }

        // words must be an array; the envelope must be JSON.
        assertNull(GermanDueApi.decodeDueWords("""{"words":{"id":"w-1"}}"""))
        assertNull(GermanDueApi.decodeDueWords("""{"words":5}"""))
        assertNull(GermanDueApi.decodeDueWords("""{"dueCount":3}"""))
        assertNull(GermanDueApi.decodeDueWords("not json"))
        assertNull(GermanDueApi.decodeDueWords(null))
    }

    @Test
    fun `the summary parser reads only a non-negative integer dueCount`() {
        assertEquals(7, GermanDueApi.decodeDueCount("""{"dueCount":7,"streakDays":2,"learnedCount":9}"""))
        assertNull(GermanDueApi.decodeDueCount("""{"dueCount":-1}"""))
        assertNull(GermanDueApi.decodeDueCount("""{"dueCount":"7"}"""))
        assertNull(GermanDueApi.decodeDueCount("""{"dueCount":7.5}"""))
        assertNull(GermanDueApi.decodeDueCount("""{"streakDays":2}"""))
        assertNull(GermanDueApi.decodeDueCount("not json"))
        assertNull(GermanDueApi.decodeDueCount(null))
    }

    // --- refresh flow -------------------------------------------------------

    @Test
    fun `a successful refresh replaces the cache and stores the summary due count`() = runBlocking {
        cache.save(USERNAME, 1_000L, 20000L, 1, listOf(GermanDueWord("w-old", "Alt", box = 2, dueDay = 20000L)))
        val api = queuedApi(
            status = 200 to dueBody(dueWord("w-9f3"), dueWord("w-9f4", dueDay = 20405L)),
            summary = 200 to """{"dueCount":11,"streakDays":4}""",
        )

        val outcome = GermanDueRefresh.refresh(
            cache = cache,
            outbox = outbox,
            api = api,
            token = TEST_TOKEN,
            username = USERNAME,
            today = 20401L,
            nowMillis = 987_654L,
            onUnauthorized = { fail("no 401 in this exchange") },
        )

        assertEquals(GermanDueRefreshOutcome.Refreshed, outcome)
        val state = cache.read(USERNAME)!!
        assertEquals(987_654L, state.fetchedAt)
        assertEquals(20401L, state.fetchedDay)
        assertEquals(11, state.dueCount)
        assertEquals(listOf("w-9f3", "w-9f4"), state.words.map { it.id })

        // Both reads are authenticated GETs with the contract paths.
        assertEquals(2, api.connections.size)
        api.connections[0].also {
            assertEquals("GET", it.requestMethod)
            assertEquals("$BASE/api/german/review/due?today=20401&limit=200", it.url.toString())
            assertEquals("Bearer $TEST_TOKEN", it.getRequestProperty("Authorization"))
        }
        api.connections[1].also {
            assertEquals("GET", it.requestMethod)
            assertEquals("$BASE/api/german/summary?today=20401", it.url.toString())
        }
        assertEquals(1, api.connections[0].disconnects)
    }

    @Test
    fun `a 401 during refresh clears the cache and lapses the session`() = runBlocking {
        cache.save(USERNAME, 1_000L, 20401L, 1, listOf(GermanDueWord("w-9f3", "Fernweh", box = 3, dueDay = 20400L)))
        val api = queuedApi(status = 401 to """{"error":"unauthorized"}""")

        var unauthorized = 0
        val outcome = GermanDueRefresh.refresh(
            cache = cache,
            outbox = outbox,
            api = api,
            token = TEST_TOKEN,
            username = USERNAME,
            today = 20401L,
            nowMillis = 2_000L,
            onUnauthorized = { unauthorized++ },
        )

        assertEquals(GermanDueRefreshOutcome.Unauthorized, outcome)
        assertEquals(1, unauthorized)
        assertNull(cache.read(USERNAME))
        assertTrue(cache.dueWords(USERNAME, 20500L).isEmpty())
    }

    @Test
    fun `a network failure keeps the old cache`() = runBlocking {
        cache.save(USERNAME, 1_000L, 20401L, 1, listOf(GermanDueWord("w-9f3", "Fernweh", box = 3, dueDay = 20400L)))
        val api = queuedApi(status = 200 to "", networkFailure = true)

        var unauthorized = 0
        val outcome = GermanDueRefresh.refresh(
            cache = cache,
            outbox = outbox,
            api = api,
            token = TEST_TOKEN,
            username = USERNAME,
            today = 20401L,
            nowMillis = 2_000L,
            onUnauthorized = { unauthorized++ },
        )

        assertEquals(GermanDueRefreshOutcome.KeptOldCache, outcome)
        assertEquals(0, unauthorized)
        assertEquals(listOf("w-9f3"), cache.dueWords(USERNAME, 20500L).map { it.id })
        assertEquals(1_000L, cache.fetchedAt(USERNAME))
    }

    @Test
    fun `a 5xx keeps the old cache`() = runBlocking {
        cache.save(USERNAME, 1_000L, 20401L, 1, listOf(GermanDueWord("w-9f3", "Fernweh", box = 3, dueDay = 20400L)))
        val api = queuedApi(status = 503 to """{"error":"unavailable"}""")

        val outcome = GermanDueRefresh.refresh(
            cache = cache,
            outbox = outbox,
            api = api,
            token = TEST_TOKEN,
            username = USERNAME,
            today = 20401L,
            nowMillis = 2_000L,
            onUnauthorized = {},
        )

        assertEquals(GermanDueRefreshOutcome.KeptOldCache, outcome)
        assertEquals(listOf("w-9f3"), cache.dueWords(USERNAME, 20500L).map { it.id })
    }

    @Test
    fun `a 2xx due body that does not parse keeps the previous cache`() = runBlocking {
        cache.save(USERNAME, 1_000L, 20401L, 1, listOf(GermanDueWord("w-9f3", "Fernweh", box = 3, dueDay = 20400L)))
        // One element without an id: the whole response is rejected.
        val partial = JSONObject().put("id", "w-1").put("text", "Ohne Box").put("dueDay", 20401L)
        val api = queuedApi(
            status = 200 to dueBody(JSONObject(dueWord("w-9f3").toString()), partial),
            summary = 200 to """{"dueCount":5}""",
        )

        val outcome = GermanDueRefresh.refresh(
            cache = cache,
            outbox = outbox,
            api = api,
            token = TEST_TOKEN,
            username = USERNAME,
            today = 20401L,
            nowMillis = 2_000L,
            onUnauthorized = {},
        )

        assertEquals(GermanDueRefreshOutcome.KeptOldCache, outcome)
        assertEquals(1_000L, cache.fetchedAt(USERNAME))
        assertEquals(listOf("w-9f3"), cache.dueWords(USERNAME, 20500L).map { it.id })
    }

    @Test
    fun `a failed summary keeps the fresh due set with its own size as the count`() = runBlocking {
        val api = queuedApi(
            status = 200 to dueBody(dueWord("w-9f3"), dueWord("w-9f4")),
            summary = 200 to "Service Unavailable",
        )

        val outcome = GermanDueRefresh.refresh(
            cache = cache,
            outbox = outbox,
            api = api,
            token = TEST_TOKEN,
            username = USERNAME,
            today = 20401L,
            nowMillis = 2_000L,
            onUnauthorized = {},
        )

        assertEquals(GermanDueRefreshOutcome.Refreshed, outcome)
        assertEquals(2, cache.read(USERNAME)?.dueCount)
    }

    @Test
    fun `the outbox is replayed before the due set is read`() = runBlocking {
        outbox.enqueue("POST", "/api/german/words", """{"text":"Fernweh","today":20401}""")
        val api = queuedApi(
            // The queued capture goes out first.
            replay = 200 to """{"id":"w-1","text":"Fernweh"}""",
            status = 200 to dueBody(dueWord("w-9f3")),
            summary = 200 to """{"dueCount":1}""",
        )

        val outcome = GermanDueRefresh.refresh(
            cache = cache,
            outbox = outbox,
            api = api,
            token = TEST_TOKEN,
            username = USERNAME,
            today = 20401L,
            nowMillis = 2_000L,
            onUnauthorized = {},
        )

        assertEquals(GermanDueRefreshOutcome.Refreshed, outcome)
        assertEquals(0, outbox.pendingCount())
        assertEquals(3, api.connections.size)
        assertEquals("POST", api.connections[0].requestMethod)
        assertEquals("$BASE/api/german/words", api.connections[0].url.toString())
        assertEquals("GET", api.connections[1].requestMethod)
    }

    // --- fake exchange ------------------------------------------------------

    private class Canned(val status: Int, val body: String, val failWith: IOException?)

    /**
     * Serves canned responses in order — one connection per `send` — the
     * `GermanWebApiTest` fake-exchange pattern. Queue order for a refresh
     * with a non-empty outbox: the replay writes, then the due GET, then the
     * summary GET.
     */
    private inner class QueuedApi : GermanWebApi(BASE) {
        val connections = mutableListOf<FakeDueConnection>()
        private val queue = ArrayDeque<Canned>()

        fun enqueue(canned: Canned) {
            queue.add(canned)
        }

        override fun openConnection(url: URL): HttpURLConnection =
            FakeDueConnection(url).also { connection ->
                connections += connection
                val next = queue.removeFirstOrNull() ?: Canned(200, "", null)
                connection.status = next.status
                connection.body = next.body
                connection.failResponseCodeWith = next.failWith
            }
    }

    private fun queuedApi(
        replay: Pair<Int, String>? = null,
        status: Pair<Int, String>,
        summary: Pair<Int, String>? = null,
        networkFailure: Boolean = false,
    ): QueuedApi {
        val api = QueuedApi()
        replay?.let { api.enqueue(Canned(it.first, it.second, null)) }
        api.enqueue(Canned(status.first, status.second, if (networkFailure) IOException("unreachable") else null))
        summary?.let { api.enqueue(Canned(it.first, it.second, null)) }
        return api
    }

    private fun fail(message: String): Nothing = throw AssertionError(message)

    companion object {
        private const val BASE = "https://due.example.com"

        /** Obviously synthetic (never a real device token), with the required prefix. */
        private const val TEST_TOKEN = "gfd_test_token_0001"
        private const val USERNAME = "ola-test-user-7"
    }
}

/** Canned exchange standing in for the network: no sockets in unit tests. */
private class FakeDueConnection(url: URL) : HttpURLConnection(url) {

    var status: Int = 200
    var body: String = ""
    var failResponseCodeWith: IOException? = null

    var disconnects: Int = 0
        private set

    private val out = ByteArrayOutputStream()

    override fun connect() {}

    private var fakeMethod: String = "GET"

    override fun setRequestMethod(method: String?) {
        fakeMethod = method ?: "GET"
    }

    override fun getRequestMethod(): String = fakeMethod

    override fun disconnect() {
        disconnects++
    }

    override fun usingProxy(): Boolean = false

    override fun getOutputStream(): OutputStream = out

    override fun getResponseCode(): Int {
        failResponseCodeWith?.let { throw it }
        return status
    }

    override fun getInputStream(): InputStream {
        if (status !in 200..399) throw IOException("HTTP $status")
        return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
    }

    override fun getErrorStream(): InputStream? =
        if (status in 200..399) null else ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

    fun writtenBody(): String = out.toString("UTF-8")
}
