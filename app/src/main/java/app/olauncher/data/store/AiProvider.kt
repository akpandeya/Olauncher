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
 */
data class AiRequest(
    val prompt: String,
    /** Ask for a JSON object response (the practice contract is JSON). */
    val jsonMode: Boolean = true,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
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
     * (Melious: 429 with type "billing_error") — retrying cannot fix it, the user
     * must top up, so it is distinct from [ErrorKind.AUTH].
     */
    data class Error(
        val kind: ErrorKind,
        val message: String,
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
