package app.olauncher.data.store

/**
 * Provider-agnostic AI interface (docs/design/ai-practice.md): Melious today,
 * Anthropic or another provider later. No provider name appears here — a provider
 * is picked through [AiProviderConfig] and [AiProviders.create].
 */
interface AiProvider {
    suspend fun complete(request: AiRequest): AiResult
}

/**
 * One generation request. The prompt comes from [PracticeContract.buildPrompt];
 * the model, base URL and key live in the provider config and [KeySource], not here.
 *
 * [maxTokens] and [readTimeoutMs] are sized per request by the caller: a small
 * chunk of words needs a small budget (review R1 #1 — one request can never
 * carry the whole queue under the default cap).
 */
data class AiRequest(
    val prompt: String,
    /** Ask for a JSON object response (the practice contract is JSON). */
    val jsonMode: Boolean = true,
    /**
     * Optional strict JSON schema, JSON-encoded. When set, the request asks for
     * `response_format: json_schema` instead of `json_object`; providers without
     * the capability reject it with 400 `model_not_capable`, and the caller can
     * fall back to [jsonMode] (benchmark round 2, #600).
     */
    val jsonSchema: String? = null,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    /** Socket read timeout in ms; null uses the provider's default. */
    val readTimeoutMs: Int? = null,
) {
    companion object {
        const val DEFAULT_MAX_TOKENS = 4096
    }
}

/** Token usage as reported by the provider. */
data class AiUsage(
    val inputTokens: Int,
    val outputTokens: Int,
)

/** The outcome of one [AiProvider.complete] call. */
sealed class AiResult {
    data class Ok(
        val text: String,
        val usage: AiUsage,
        val latencyMs: Long,
    ) : AiResult()

    /**
     * [ErrorKind.RATE_LIMIT] is ordinary throttling: backing off and retrying can
     * fix it. [ErrorKind.BILLING] means the account's prepaid credits are exhausted
     * (Melious: 429 with type "billing_error", or HTTP 402) — retrying cannot fix
     * it, the user must top up, so it is distinct from [ErrorKind.AUTH].
     *
     * [errorCode] carries the provider error envelope's `code` when present (for
     * example `model_not_capable` on a strict-schema request); it lets callers
     * react to a specific failure mode without scraping [message] text.
     */
    data class Error(
        val kind: ErrorKind,
        val message: String,
        val errorCode: String? = null,
    ) : AiResult()

    enum class ErrorKind { AUTH, RATE_LIMIT, NETWORK, BAD_RESPONSE, SERVER, BILLING }
}

/**
 * Supplies the API key at call time. The key must never appear in logs,
 * exceptions or [AiResult.Error.message].
 */
fun interface KeySource {
    fun get(): String?
}

/**
 * Which provider to call, where, and with which model (owner decision: provider,
 * base URL and model are config). Backed by the `AI_PROVIDER`, `AI_BASE_URL` and
 * `AI_MODEL` prefs.
 */
data class AiProviderConfig(
    val providerId: String,
    val baseUrl: String,
    val model: String,
) {
    /** Returns the first validation problem, or null when the config is usable. */
    fun validate(): String? = when {
        providerId.isBlank() -> "Provider id cannot be blank"
        !baseUrl.startsWith("https://") -> "Base URL must start with https://"
        else -> null
    }
}

/**
 * Maps a provider id to its adapter. Adding a provider means one adapter class
 * and one map entry here — no other code changes.
 */
object AiProviders {

    /** The default provider id, stored in the `AI_PROVIDER` pref. */
    const val MELIOUS = "melious"

    // Adding a provider = one adapter class + one entry in this map.
    private val adapters: Map<String, (AiProviderConfig, KeySource) -> AiProvider> = mapOf(
        MELIOUS to ::OpenAiCompatibleProvider,
    )

    /** Returns the adapter for [config], or null when the provider id is unknown. */
    fun create(config: AiProviderConfig, keySource: KeySource): AiProvider? {
        return adapters[config.providerId]?.invoke(config, keySource)
    }
}
