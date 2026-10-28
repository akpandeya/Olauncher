package app.olauncher.data.store

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class OpenAiCompatibleProviderTest {

    private val config = AiProviderConfig("melious", "https://api.melious.ai", "glm-5.3-flash")
    private val keySource = KeySource { SENTINEL_KEY }
    private val request = AiRequest(prompt = "Build practice items for word 1", maxTokens = 777)

    @Test
    fun `encodeRequest builds the OpenAI chat body`() {
        val body = JSONObject(OpenAiCompatibleProvider(config, keySource).encodeRequest(request))
        assertEquals("glm-5.3-flash", body.getString("model"))
        assertEquals(777, body.getInt("max_tokens"))
        val message = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", message.getString("role"))
        assertEquals("Build practice items for word 1", message.getString("content"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
    }

    @Test
    fun `encodeRequest omits response format when json mode is off`() {
        val provider = OpenAiCompatibleProvider(config, keySource)
        val body = JSONObject(provider.encodeRequest(request.copy(jsonMode = false)))
        assertTrue(!body.has("response_format"))
    }

    @Test
    fun `decodeResponse ok parses content and usage tokens`() {
        val result = decode(200, null, resource("ai/chat_ok.json"))
        result as AiResult.Ok
        assertTrue(result.text.startsWith("""{"items": [{"""))
        assertEquals(512, result.usage.inputTokens)
        assertEquals(128, result.usage.outputTokens)
    }

    @Test
    fun `decodeResponse maps 401 to AUTH`() {
        val result = decode(401, null, resource("ai/chat_error_401.json"))
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.AUTH, result.kind)
        assertEquals("Incorrect API key provided.", result.message)
    }

    @Test
    fun `decodeResponse maps 429 to RATE_LIMIT with the Retry-After header`() {
        val result = decode(429, 30, resource("ai/chat_error_429.json"))
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.RATE_LIMIT, result.kind)
        assertTrue("'${result.message}' mentions Retry-After", result.message.contains("Retry after 30s"))
    }

    @Test
    fun `decodeResponse maps the 429 billing error to BILLING`() {
        val result = decode(429, null, resource("ai/chat_error_429_billing.json"))
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.BILLING, result.kind)
        assertEquals("Insufficient credit balance; top up your account.", result.message)
    }

    @Test
    fun `decodeResponse maps 5xx to SERVER`() {
        val result = decode(503, null, resource("ai/chat_error_5xx.json"))
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.SERVER, result.kind)
        assertEquals("The server had an error while processing your request.", result.message)
    }

    @Test
    fun `decodeResponse maps a truncated 200 body to BAD_RESPONSE`() {
        val result = decode(200, null, resource("ai/chat_truncated.json"))
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.BAD_RESPONSE, result.kind)
    }

    @Test
    fun `decodeResponse maps a length-capped finish reason to BAD_RESPONSE`() {
        // HTTP 200, valid JSON envelope — but the model hit max_tokens, so the
        // content is cut off and must not parse as Ok (review R1 #1).
        val result = decode(200, null, resource("ai/chat_finish_reason_length.json"))
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.BAD_RESPONSE, result.kind)
    }

    @Test
    fun `a per-request read timeout overrides the default`() = runBlocking {
        val (provider, connection) = provider(200, resource("ai/chat_ok.json"))
        provider.complete(request.copy(readTimeoutMs = 120_000))
        assertEquals(120_000, connection.readTimeout)
    }

    @Test
    fun `a cancelled call interrupts the blocking exchange`() = runBlocking {
        val connection = FakeConnection(URL(config.baseUrl.trimEnd('/') + OpenAiCompatibleProvider.CHAT_PATH))
        connection.status = 200
        connection.body = resource("ai/chat_ok.json")
        connection.responseDelayMs = 20_000
        val provider = object : OpenAiCompatibleProvider(config, keySource) {
            override fun openConnection(url: URL): HttpURLConnection = connection
        }
        val startedAt = System.currentTimeMillis()
        val result = withTimeoutOrNull(200) { provider.complete(request) }
        val elapsed = System.currentTimeMillis() - startedAt
        // The blocking exchange was interrupted instead of waited out (review R1 #2).
        assertEquals(null, result)
        assertTrue("returned after ${elapsed} ms", elapsed < 10_000)
        assertEquals(1, connection.disconnects)
    }

    @Test
    fun `complete posts to the chat path with the bearer key and timeouts`() = runBlocking {
        val (provider, connection) = provider(200, resource("ai/chat_ok.json"))
        val result = provider.complete(request)
        result as AiResult.Ok
        assertEquals(512, result.usage.inputTokens)
        assertEquals("POST", connection.requestMethod)
        assertEquals(15_000, connection.connectTimeout)
        assertEquals(60_000, connection.readTimeout)
        assertEquals("Bearer $SENTINEL_KEY", connection.getRequestProperty("Authorization"))
        assertTrue(
            "request body carries the model",
            connection.writtenBody().contains("\"model\":\"glm-5.3-flash\""),
        )
        assertEquals(1, connection.disconnects)
    }

    @Test
    fun `complete maps canned errors end to end`() = runBlocking {
        listOf(
            401 to resource("ai/chat_error_401.json") to AiResult.ErrorKind.AUTH,
            429 to resource("ai/chat_error_429.json") to AiResult.ErrorKind.RATE_LIMIT,
            429 to resource("ai/chat_error_429_billing.json") to AiResult.ErrorKind.BILLING,
            503 to resource("ai/chat_error_5xx.json") to AiResult.ErrorKind.SERVER,
            200 to resource("ai/chat_truncated.json") to AiResult.ErrorKind.BAD_RESPONSE,
        ).forEach { (exchange, expectedKind) ->
            val (provider, connection) = provider(exchange.first, exchange.second)
            val result = provider.complete(request)
            result as AiResult.Error
            assertEquals("HTTP ${exchange.first}", expectedKind, result.kind)
            assertFalse(
                "HTTP ${exchange.first}: the sentinel key must never leak",
                result.message.contains(SENTINEL_KEY),
            )
            assertEquals(1, connection.disconnects)
        }
    }

    @Test
    fun `an error message echoing the key is redacted`() = runBlocking {
        val echoing = """
            {"error": {"message": "Invalid key $SENTINEL_KEY supplied.", "type": "authentication_error",
             "code": "invalid_api_key"}}
        """.trimIndent()
        val (provider, _) = provider(401, echoing)
        val result = provider.complete(request)
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.AUTH, result.kind)
        assertFalse(result.message.contains(SENTINEL_KEY))
        assertTrue(result.message.contains("[redacted]"))
    }

    @Test
    fun `a missing key gives AUTH before any network call`() = runBlocking {
        var connections = 0
        val provider = object : OpenAiCompatibleProvider(config, KeySource { null }) {
            override fun openConnection(url: URL): HttpURLConnection {
                connections++
                return FakeConnection(url)
            }
        }
        val result = provider.complete(request)
        result as AiResult.Error
        assertEquals(AiResult.ErrorKind.AUTH, result.kind)
        assertEquals(0, connections)
    }

    @Test
    fun `the melious provider id resolves to the OpenAI-compatible adapter`() {
        val provider = AiProviders.create(config, keySource)
        assertTrue(provider is OpenAiCompatibleProvider)
    }

    private fun decode(status: Int, retryAfter: Int?, body: String): AiResult =
        OpenAiCompatibleProvider(config, keySource).decodeResponse(status, retryAfter, body)

    private fun provider(
        status: Int,
        body: String,
        retryAfter: String? = null,
    ): Pair<OpenAiCompatibleProvider, FakeConnection> {
        val connection = FakeConnection(URL(config.baseUrl.trimEnd('/') + OpenAiCompatibleProvider.CHAT_PATH))
        connection.status = status
        connection.body = body
        connection.retryAfter = retryAfter
        val provider = object : OpenAiCompatibleProvider(config, keySource) {
            override fun openConnection(url: URL): HttpURLConnection = connection
        }
        return provider to connection
    }

    private fun resource(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: javaClass.getResourceAsStream("/$name")
        return stream?.bufferedReader()?.readText() ?: error("Test resource '$name' not found")
    }

    companion object {
        private const val SENTINEL_KEY = "sk-test-sentinel"
    }
}

/** Canned exchange standing in for the network: no sockets in unit tests. */
private class FakeConnection(url: URL) : HttpURLConnection(url) {

    var status: Int = 200
    var body: String = ""
    var retryAfter: String? = null

    /** Blocks [getResponseCode] like a slow socket; interruptible via Thread.sleep. */
    var responseDelayMs: Long = 0
    var disconnects: Int = 0
        private set

    private val out = ByteArrayOutputStream()

    override fun connect() {}

    override fun disconnect() {
        disconnects++
    }

    override fun usingProxy(): Boolean = false

    override fun getOutputStream(): OutputStream = out

    override fun getResponseCode(): Int {
        if (responseDelayMs > 0) {
            try {
                Thread.sleep(responseDelayMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("interrupted", e)
            }
        }
        return status
    }

    override fun getHeaderField(name: String): String? =
        if (name.equals("Retry-After", ignoreCase = true)) retryAfter else null

    override fun getInputStream(): InputStream {
        if (status in 200..299) return ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        throw IOException("HTTP $status")
    }

    override fun getErrorStream(): InputStream? =
        if (status in 200..299) null else ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))

    fun writtenBody(): String = out.toString("UTF-8")
}
