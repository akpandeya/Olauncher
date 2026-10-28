package app.olauncher.data.store

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * OpenAI-compatible chat adapter, first used for Melious (docs/design/ai-practice.md,
 * "Provider: Melious", confirmed 2026-10-27): POST `{base}/v1/chat/completions` with
 * `Authorization: Bearer <key>` and OpenAI-shaped JSON.
 *
 * [encodeRequest] and [decodeResponse] are context-free and unit-tested with canned
 * JSON (app/src/test/resources/ai/); [complete] only wires them to the network.
 * The key is read from the [KeySource] at call time and never appears in logs,
 * exceptions or [AiResult.Error.message] — every error message is scrubbed.
 *
 * Open only so tests can override [openConnection] with canned exchanges.
 */
open class OpenAiCompatibleProvider(
    private val config: AiProviderConfig,
    private val keySource: KeySource,
) : AiProvider {

    override suspend fun complete(request: AiRequest): AiResult {
        val key = keySource.get()
            ?: return AiResult.Error(AiResult.ErrorKind.AUTH, "No API key set")
        return try {
            // runInterruptible: a cancelled caller (the on-demand ~10 s deadline)
            // interrupts the thread at once — but a socket read already blocked
            // in the kernel only ends at the connection's read timeout, and the
            // disconnect() in the finally runs after the call returns, not
            // during it. The real bound is connect time (≤15 s) plus the
            // per-request read timeout (review R1 #2, R2 C).
            runInterruptible(Dispatchers.IO) { exchange(request, key) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AiResult.Error(AiResult.ErrorKind.NETWORK, "Request failed (${e.javaClass.simpleName})")
        }
    }

    /** The one blocking HTTP exchange; runs on the IO dispatcher via [complete]. */
    private fun exchange(request: AiRequest, key: String): AiResult {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(config.baseUrl.trimEnd('/') + CHAT_PATH)
            connection = openConnection(url)
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            // The deadline lives on the connection itself so a slow provider gives
            // up here, not only when a caller cancels.
            connection.readTimeout = request.readTimeoutMs ?: READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { it.write(encodeRequest(request).toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val retryAfter = connection.getHeaderField(RETRY_AFTER_HEADER)?.trim()?.toIntOrNull()
            val body = readBody(connection)
            val result = decodeResponse(status, retryAfter, body)
            return if (result is AiResult.Error) sanitize(result, key) else result
        } catch (e: IOException) {
            // Exception text can embed connection details; report the type only.
            return AiResult.Error(AiResult.ErrorKind.NETWORK, "Could not reach the provider (${e.javaClass.simpleName})")
        } catch (e: Exception) {
            return AiResult.Error(AiResult.ErrorKind.NETWORK, "Request failed (${e.javaClass.simpleName})")
        } finally {
            connection?.disconnect()
        }
    }

    /** Open for tests: unit tests replace this to serve canned responses, no network. */
    protected open fun openConnection(url: URL): HttpURLConnection =
        url.openConnection() as HttpURLConnection

    /**
     * The request body, OpenAI shape. JSON mode is `response_format: json_object`
     * (widely supported on Melious); strict `json_schema` needs a capable model and
     * would 400 `model_not_capable` on the rest, so it is not used here.
     */
    fun encodeRequest(request: AiRequest): String {
        val message = JSONObject()
            .put("role", "user")
            .put("content", request.prompt)
        val body = JSONObject()
            .put("model", config.model)
            .put("messages", JSONArray().put(message))
            .put("max_tokens", request.maxTokens)
        if (request.jsonMode) {
            body.put("response_format", JSONObject().put("type", "json_object"))
        }
        return body.toString()
    }

    /**
     * Maps one HTTP exchange to an [AiResult], per the confirmed Melious error table:
     * 401/403 AUTH; 429 RATE_LIMIT (honoring `Retry-After`) unless the error is a
     * `billing_error` (out of credits — `billing_insufficient_balance` or
     * `insufficient_quota`), which is BILLING and not fixable by retrying; 5xx SERVER;
     * other 4xx and unparseable or truncated bodies BAD_RESPONSE.
     */
    fun decodeResponse(httpStatus: Int, retryAfterSeconds: Int?, body: String?, latencyMs: Long = 0): AiResult {
        if (httpStatus in 200..299) {
            return decodeSuccess(body, latencyMs)
        }
        val error = errorOf(body)
        val type = error.optString("type")
        val code = error.optString("code")
        val detail = error.optString("message").ifBlank { "HTTP $httpStatus" }
        return when {
            type == "billing_error" || code in BILLING_CODES ->
                AiResult.Error(AiResult.ErrorKind.BILLING, detail)
            httpStatus == 401 || httpStatus == 403 ->
                AiResult.Error(AiResult.ErrorKind.AUTH, detail)
            httpStatus == 429 -> {
                val wait = retryAfterSeconds?.let { " Retry after ${it}s." } ?: ""
                AiResult.Error(AiResult.ErrorKind.RATE_LIMIT, "$detail$wait")
            }
            httpStatus >= 500 ->
                AiResult.Error(AiResult.ErrorKind.SERVER, detail)
            else ->
                AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, detail)
        }
    }

    private fun decodeSuccess(body: String?, latencyMs: Long): AiResult {
        if (body.isNullOrBlank()) {
            return AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "Empty response body")
        }
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "Response is not valid JSON (truncated?)")
        }
        val choice = root.optJSONArray("choices")?.optJSONObject(0)
        val text = choice?.optJSONObject("message")?.optString("content")
        if (text.isNullOrBlank()) {
            return AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "Response has no message content")
        }
        // A reply cut off at max_tokens is still HTTP 200 with valid JSON, but the
        // content is truncated — surface it as BAD_RESPONSE, not as parseable text
        // (review R1 #1).
        if (choice.optString("finish_reason") == "length") {
            return AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "Response was cut off at max_tokens")
        }
        val usage = root.optJSONObject("usage")
        val parsed = AiUsage(
            inputTokens = usage?.optInt("prompt_tokens", 0) ?: 0,
            outputTokens = usage?.optInt("completion_tokens", 0) ?: 0,
        )
        return AiResult.Ok(text, parsed, latencyMs)
    }

    /** The `error` object of the OpenAI error envelope, or empty when absent. */
    private fun errorOf(body: String?): JSONObject {
        if (body.isNullOrBlank()) return JSONObject()
        return try {
            JSONObject(body).optJSONObject("error") ?: JSONObject()
        } catch (e: JSONException) {
            JSONObject()
        }
    }

    /** Last line of defense: the key must never surface in an error message. */
    private fun sanitize(error: AiResult.Error, key: String): AiResult.Error {
        val message = if (key.isBlank()) error.message else error.message.replace(key, REDACTED)
        return error.copy(message = message)
    }

    private fun readBody(connection: HttpURLConnection): String? {
        val stream = try {
            connection.inputStream
        } catch (e: IOException) {
            connection.errorStream ?: return null
        }
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    companion object {
        /** The chat path under the base URL, per docs/design/ai-practice.md (9.2). */
        const val CHAT_PATH = "/v1/chat/completions"
        const val RETRY_AFTER_HEADER = "Retry-After"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000
        private const val REDACTED = "[redacted]"

        /** Melious out-of-credits codes (429, type `billing_error`), confirmed 2026-10-27. */
        private val BILLING_CODES = setOf("billing_insufficient_balance", "insufficient_quota")
    }
}
