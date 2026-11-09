package app.olauncher.data.store

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONException
import org.json.JSONObject

/**
 * Normalises the gambitflow server address the user types (epic #835, task 02:
 * there is no built-in address — the launcher pairs with whatever the user
 * entered). Context-free and unit-tested:
 * trims, adds `https://` when no scheme is given, lowercases the host, keeps a
 * non-default port and strips any trailing slash, path, query or fragment.
 * Returns null for blank input, a non-`https` scheme or a host without a dot.
 */
object GermanWebBaseUrl {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val url = try {
            URL(withScheme)
        } catch (e: Exception) {
            return null
        }
        if (!url.protocol.equals("https", ignoreCase = true)) return null
        val host = url.host.trim().lowercase()
        if (host.isBlank() || !host.contains('.')) return null
        val port = if (url.port == -1 || url.port == 443) "" else ":${url.port}"
        return "https://$host$port"
    }
}

/**
 * Thin HTTP client for the gambitflow web app (docs/design/german-api.md,
 * "Device tokens"): the one-time pairing-code exchange plus one generic
 * authenticated call ([send]) that the outbox (task 03) and due-word refresh
 * (task 04) build on. Platform APIs only (`HttpURLConnection`, `org.json`);
 * every blocking call runs on `Dispatchers.IO` through `runInterruptible`.
 *
 * The base URL is the user's server address; anything [GermanWebBaseUrl]
 * rejects is rejected here too, before any request is made.
 *
 * The device token never appears in an error, a result `toString()` or a log:
 * [PairingResult.Paired.toString] redacts it, every error carries only the
 * HTTP status or the exception class name, and response bodies are scrubbed
 * of the token as a last line of defense (as `OpenAiCompatibleProvider` does).
 *
 * Open so tests can override [openConnection] with canned exchanges, no network.
 */
open class GermanWebApi(baseUrl: String) {

    private val base: String = GermanWebBaseUrl.normalize(baseUrl)
        ?: throw IllegalArgumentException("German web base URL must be an https server address")

    /**
     * Exchanges an 8-character one-time pairing code for a device token
     * (`POST {base}/api/auth/devices/exchange`, no auth; 10 failed attempts
     * per 15 minutes on the server). The code is trimmed and upper-cased the
     * way the server normalises it; a code that is not 8 characters is
     * rejected locally, without a request. The label names the device in the
     * web app's device list (1..60 chars, a longer device name is truncated).
     */
    suspend fun exchangePairingCode(code: String, label: String): PairingResult {
        val normalizedCode = code.trim().uppercase()
        if (normalizedCode.length != PAIRING_CODE_LENGTH) return PairingResult.InvalidCode
        val normalizedLabel = label.trim().take(MAX_LABEL_LENGTH)
        val body = JSONObject().put("code", normalizedCode)
        if (normalizedLabel.isNotEmpty()) body.put("label", normalizedLabel)
        return try {
            runInterruptible(Dispatchers.IO) { exchangePairingCodeBlocking(body.toString()) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Exception text can embed connection details; report the type only.
            PairingResult.NetworkError(e.javaClass.simpleName)
        }
    }

    private fun exchangePairingCodeBlocking(bodyJson: String): PairingResult {
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection(URL(base + EXCHANGE_PATH))
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            return decodePairingResponse(connection.responseCode, readBody(connection))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * One authenticated call under `/api/german`: [GermanWebRequest.method] and
     * [GermanWebRequest.path] pass straight through, the token rides in
     * `Authorization: Bearer`, and a write's [GermanWebRequest.idempotencyKey]
     * rides in `Idempotency-Key` so a retried outbox entry is safe.
     */
    suspend fun send(request: GermanWebRequest, token: String): GermanWebResponse {
        return try {
            runInterruptible(Dispatchers.IO) { sendBlocking(request, token) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GermanWebResponse.NetworkFailure(e.javaClass.simpleName)
        }
    }

    private fun sendBlocking(request: GermanWebRequest, token: String): GermanWebResponse {
        var connection: HttpURLConnection? = null
        try {
            connection = openConnection(URL(base + request.path))
            connection.requestMethod = request.method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            request.idempotencyKey?.let { connection.setRequestProperty(IDEMPOTENCY_KEY_HEADER, it) }
            val bodyJson = request.bodyJson
            if (bodyJson != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val retryAfter = connection.getHeaderField(RETRY_AFTER_HEADER)?.trim()?.toIntOrNull()
            val body = readBody(connection)?.let { scrub(it, token) }
            return GermanWebResponse.Http(status, body, retryAfter)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Maps one pairing exchange to a [PairingResult], context-free so tests can
     * drive it directly: 400 is a bad/expired/used code, 429 the rate limit,
     * other non-2xx statuses the server's problem. A 2xx body counts as
     * [PairingResult.Paired] only when it is complete — `token` with the `gfd_`
     * prefix plus non-blank `deviceId` and `username`; anything else (non-JSON,
     * missing or malformed fields) is [PairingResult.ServerError] with the
     * actual status, never a half-parsed pairing.
     */
    fun decodePairingResponse(status: Int, body: String?): PairingResult = when {
        status == 400 -> PairingResult.InvalidCode
        status == 429 -> PairingResult.TooManyAttempts
        status !in 200..299 -> PairingResult.ServerError(status)
        else -> pairedFrom(body, status)
    }

    private fun pairedFrom(body: String?, status: Int): PairingResult {
        val root = try {
            body?.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
        } catch (e: JSONException) {
            null
        } ?: return PairingResult.ServerError(status)
        val token = root.optString("token")
        val deviceId = root.optString("deviceId")
        val username = root.optString("username")
        if (!token.startsWith(TOKEN_PREFIX) || deviceId.isBlank() || username.isBlank()) {
            return PairingResult.ServerError(status)
        }
        return PairingResult.Paired(token, deviceId, username)
    }

    /** The response body: `inputStream` for 2xx, the error stream otherwise. */
    private fun readBody(connection: HttpURLConnection): String? {
        val stream = try {
            connection.inputStream
        } catch (e: IOException) {
            connection.errorStream ?: return null
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Last line of defense: the token must never surface in a response body. */
    private fun scrub(body: String, token: String): String =
        if (token.isBlank()) body else body.replace(token, REDACTED)

    /** Open for tests: unit tests replace this to serve canned responses, no network. */
    protected open fun openConnection(url: URL): HttpURLConnection =
        url.openConnection() as HttpURLConnection

    companion object {
        /** The pairing exchange path under the base URL, per docs/design/german-api.md. */
        const val EXCHANGE_PATH = "/api/auth/devices/exchange"

        /** Device tokens (`gfd_…`) are only ever accepted under `/api/german`. */
        const val TOKEN_PREFIX = "gfd_"

        /** Codes are 8 characters from an unambiguous alphabet, valid 10 minutes. */
        const val PAIRING_CODE_LENGTH = 8

        /** The device label the web app shows in its device list. */
        const val MAX_LABEL_LENGTH = 60

        const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        const val RETRY_AFTER_HEADER = "Retry-After"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        private const val REDACTED = "[redacted]"
    }
}

/** One authenticated call under `/api/german`: method and path pass through unchanged. */
data class GermanWebRequest(
    val method: String,
    val path: String,
    val bodyJson: String? = null,
    val idempotencyKey: String? = null,
)

/** The outcome of one [GermanWebApi.send]: the raw exchange, unmapped — callers decide. */
sealed class GermanWebResponse {
    data class Http(val status: Int, val body: String?, val retryAfterSeconds: Int?) : GermanWebResponse()

    /** Exception text can embed connection details; only the class name is carried. */
    data class NetworkFailure(val exceptionClass: String) : GermanWebResponse()
}

/** The outcome of one pairing-code exchange. Errors never carry the token or the code. */
sealed class PairingResult {
    /** A successful exchange. The raw `gfd_…` token appears exactly once, here. */
    data class Paired(val token: String, val deviceId: String, val username: String) : PairingResult() {
        override fun toString(): String = "Paired(token=$REDACTED, deviceId=$deviceId, username=$username)"
    }

    /** The code is unknown, expired or already used (HTTP 400), or not 8 characters. */
    object InvalidCode : PairingResult()

    /** The server's pairing rate limit was hit (HTTP 429, 10 failures per 15 minutes). */
    object TooManyAttempts : PairingResult()

    /** Any other failure, including a 2xx body that is not a complete pairing. */
    data class ServerError(val status: Int) : PairingResult()

    /** Exception text can embed connection details; only the class name is carried. */
    data class NetworkError(val exceptionClass: String) : PairingResult()

    private companion object {
        const val REDACTED = "[redacted]"
    }
}
