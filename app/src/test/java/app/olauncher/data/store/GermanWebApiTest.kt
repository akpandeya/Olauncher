package app.olauncher.data.store

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class GermanWebApiTest {

    @Test
    fun `exchange pairs with a complete 200 body and sends the normalised code and label`() = runBlocking {
        val exchange = exchange(status = 200, body = pairingBody(TEST_TOKEN, DEVICE_ID, USERNAME))
        val result = exchange.api.exchangePairingCode(" abcdefgh ", "Thumbline phone")
        result as PairingResult.Paired
        assertEquals(TEST_TOKEN, result.token)
        assertEquals(DEVICE_ID, result.deviceId)
        assertEquals(USERNAME, result.username)
        val connection = exchange.connections.single()
        assertEquals("$BASE/api/auth/devices/exchange", connection.url.toString())
        assertEquals("POST", connection.requestMethod)
        assertNull(connection.getRequestProperty("Authorization"))
        assertEquals("application/json", connection.getRequestProperty("Content-Type"))
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(20_000, connection.readTimeout)
        val sent = JSONObject(connection.writtenBody())
        assertEquals("ABCDEFGH", sent.getString("code"))
        assertEquals("Thumbline phone", sent.getString("label"))
        assertEquals(1, connection.disconnects)
    }

    @Test
    fun `a code that is not 8 characters is rejected locally without a request`() = runBlocking {
        listOf("", "abcdefghi", "abc").forEach { code ->
            val exchange = exchange()
            val result = exchange.api.exchangePairingCode(code, "Thumbline phone")
            assertEquals("code '$code'", PairingResult.InvalidCode, result)
            assertTrue("code '$code' must not open a connection", exchange.connections.isEmpty())
        }
    }

    @Test
    fun `canned statuses map to InvalidCode TooManyAttempts and ServerError`() = runBlocking {
        listOf(
            400 to PairingResult.InvalidCode,
            429 to PairingResult.TooManyAttempts,
            503 to PairingResult.ServerError(503),
            500 to PairingResult.ServerError(500),
        ).forEach { (status, expected) ->
            val exchange = exchange(status = status, body = """{"error":"code rejected"}""")
            val result = exchange.api.exchangePairingCode("abcdefgh", "Thumbline phone")
            assertEquals("HTTP $status", expected, result)
            assertEquals(1, exchange.connections.single().disconnects)
        }
    }

    @Test
    fun `an IOException gives NetworkError with the exception class only`() = runBlocking {
        val exchange = exchange(failResponseCodeWith = IOException("connection reset"))
        val result = exchange.api.exchangePairingCode("abcdefgh", "Thumbline phone")
        result as PairingResult.NetworkError
        assertEquals("IOException", result.exceptionClass)
        assertTrue(result.toString(), !result.toString().contains(TEST_TOKEN))
    }

    @Test
    fun `a 200 body that is not a complete pairing gives ServerError`() = runBlocking {
        val incompleteBodies = listOf(
            // Token without the gfd_ prefix.
            pairingBody("plain_token", DEVICE_ID, USERNAME),
            // Missing deviceId.
            pairingBody(TEST_TOKEN, null, USERNAME),
            // Missing username.
            pairingBody(TEST_TOKEN, DEVICE_ID, null),
            // Blank fields.
            pairingBody(TEST_TOKEN, " ", USERNAME),
            // Not JSON at all.
            "Service Unavailable",
            // Empty body.
            "",
        )
        incompleteBodies.forEach { body ->
            val exchange = exchange(status = 200, body = body)
            val result = exchange.api.exchangePairingCode("abcdefgh", "Thumbline phone")
            assertEquals("body '$body'", PairingResult.ServerError(200), result)
        }
    }

    @Test
    fun `a longer device label is truncated to 60 characters`() = runBlocking {
        val exchange = exchange(status = 200, body = pairingBody(TEST_TOKEN, DEVICE_ID, USERNAME))
        exchange.api.exchangePairingCode("abcdefgh", "L".repeat(70))
        val sent = JSONObject(exchange.connections.single().writtenBody())
        assertEquals(60, sent.getString("label").length)
    }

    @Test
    fun `send passes the request through with auth and idempotency headers and parses the error body`() = runBlocking {
        val exchange = exchange(status = 409, body = "conflict marker", retryAfter = "17")
        val request = GermanWebRequest(
            method = "PATCH",
            path = "/api/german/words/42",
            bodyJson = """{"box":2}""",
            idempotencyKey = "outbox-7",
        )
        val response = exchange.api.send(request, TEST_TOKEN)
        response as GermanWebResponse.Http
        assertEquals(409, response.status)
        assertEquals("conflict marker", response.body)
        assertEquals(17, response.retryAfterSeconds)
        val connection = exchange.connections.single()
        assertEquals("PATCH", connection.requestMethod)
        assertEquals("$BASE/api/german/words/42", connection.url.toString())
        assertEquals("Bearer $TEST_TOKEN", connection.getRequestProperty("Authorization"))
        assertEquals("outbox-7", connection.getRequestProperty("Idempotency-Key"))
        assertEquals("""{"box":2}""", connection.writtenBody())
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(20_000, connection.readTimeout)
        assertEquals(1, connection.disconnects)
    }

    @Test
    fun `send reads a 2xx body and omits an absent idempotency key`() = runBlocking {
        val exchange = exchange(status = 200, body = """{"words":[]}""")
        val response = exchange.api.send(
            GermanWebRequest(method = "GET", path = "/api/german/words?due=1"),
            TEST_TOKEN,
        )
        response as GermanWebResponse.Http
        assertEquals(200, response.status)
        assertEquals("""{"words":[]}""", response.body)
        assertNull(response.retryAfterSeconds)
        val connection = exchange.connections.single()
        assertEquals("GET", connection.requestMethod)
        assertNull(connection.getRequestProperty("Idempotency-Key"))
        assertEquals("", connection.writtenBody())
    }

    @Test
    fun `send maps an IOException to NetworkFailure with the exception class only`() = runBlocking {
        val exchange = exchange(failResponseCodeWith = IOException("unreachable"))
        val response = exchange.api.send(
            GermanWebRequest(method = "GET", path = "/api/german/words"),
            TEST_TOKEN,
        )
        response as GermanWebResponse.NetworkFailure
        assertEquals("IOException", response.exceptionClass)
        assertTrue(response.toString(), !response.toString().contains(TEST_TOKEN))
    }

    @Test
    fun `the token never appears in a result toString or a body echoing it`() = runBlocking {
        val paired = exchange(status = 200, body = pairingBody(TEST_TOKEN, DEVICE_ID, USERNAME))
            .api
            .exchangePairingCode("abcdefgh", "Thumbline phone") as PairingResult.Paired
        assertTrue(paired.toString(), !paired.toString().contains(TEST_TOKEN))
        assertTrue(paired.toString(), paired.toString().contains("[redacted]"))

        // A response body echoing the token is scrubbed before it leaves send().
        val echoing = exchange(status = 401, body = "Invalid token $TEST_TOKEN revoked")
        val response = echoing.api.send(GermanWebRequest("GET", "/api/german/words"), TEST_TOKEN)
        response as GermanWebResponse.Http
        val body = response.body ?: ""
        assertTrue(body, !body.contains(TEST_TOKEN))
        assertTrue(body, body.contains("[redacted]"))

        // No error result carries it either.
        listOf<PairingResult>(
            PairingResult.InvalidCode,
            PairingResult.TooManyAttempts,
            PairingResult.ServerError(503),
            PairingResult.NetworkError("IOException"),
        ).forEach { error ->
            assertTrue(error.toString(), !error.toString().contains(TEST_TOKEN))
        }
    }

    @Test
    fun `base URLs are normalised and invalid ones are rejected`() {
        assertEquals("https://pair.example.com", GermanWebBaseUrl.normalize("pair.example.com"))
        assertEquals("https://app.example.com", GermanWebBaseUrl.normalize("https://app.example.com/de/login?x=1#top"))
        assertEquals("https://app.example.com", GermanWebBaseUrl.normalize("https://App.Example.COM/"))
        assertEquals("https://pair.example.com:8443", GermanWebBaseUrl.normalize("pair.example.com:8443/x"))
        assertNull(GermanWebBaseUrl.normalize("   "))
        assertNull(GermanWebBaseUrl.normalize(""))
        assertNull(GermanWebBaseUrl.normalize("http://app.example.com"))
        assertNull(GermanWebBaseUrl.normalize("https://localhost"))
        assertNull(GermanWebBaseUrl.normalize("localhost:8443"))
        assertThrows(IllegalArgumentException::class.java) { GermanWebApi("http://app.example.com") }
        assertThrows(IllegalArgumentException::class.java) { GermanWebApi("not a url at all") }
    }

    /** One canned exchange: every connection opened by the API is recorded, no network. */
    private inner class Exchange(configure: FakeExchangeConnection.() -> Unit) {
        val connections = mutableListOf<FakeExchangeConnection>()
        val api = object : GermanWebApi(BASE) {
            override fun openConnection(url: URL): HttpURLConnection =
                FakeExchangeConnection(url).also { connection ->
                    connections += connection
                    connection.configure()
                }
        }
    }

    private fun exchange(
        status: Int = 200,
        body: String = "",
        retryAfter: String? = null,
        failResponseCodeWith: IOException? = null,
    ): Exchange = Exchange({
        this.status = status
        this.body = body
        this.retryAfter = retryAfter
        this.failResponseCodeWith = failResponseCodeWith
    })

    private fun pairingBody(token: String, deviceId: String?, username: String?): String {
        val json = JSONObject().put("token", token)
        deviceId?.let { json.put("deviceId", it) }
        username?.let { json.put("username", it) }
        return json.toString()
    }

    companion object {
        private const val BASE = "https://pair.example.com"

        /** Obviously synthetic (never a real device token), with the required prefix. */
        private const val TEST_TOKEN = "gfd_test_token_0001"
        private const val DEVICE_ID = "dev-42"
        private const val USERNAME = "ola-test-user-7"
    }
}

/** Canned exchange standing in for the network: no sockets in unit tests. */
private class FakeExchangeConnection(url: URL) : HttpURLConnection(url) {

    var status: Int = 200
    var body: String = ""
    var retryAfter: String? = null

    /** Makes [getResponseCode] fail like an unreachable server. */
    var failResponseCodeWith: IOException? = null

    var disconnects: Int = 0
        private set

    private val out = ByteArrayOutputStream()

    override fun connect() {}

    /** The JDK HttpURLConnection rejects PATCH; Android's runtime and the server contract both use it. */
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
