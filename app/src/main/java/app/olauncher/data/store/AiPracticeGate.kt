package app.olauncher.data.store

import androidx.annotation.StringRes
import app.olauncher.R

/**
 * The one guard in front of every AI practice network call (#602, 9.9; docs/design/
 * ai-practice.md "Provider": BYOK, opt-in, off works fine): the feature switch is on,
 * a key exists, and a model is set. With the switch off no call is made, ever.
 */
object AiPracticeGate {

    /** Why a call may not go out; the settings screen and the generator surface it. */
    enum class Block { DISABLED, NO_KEY, NO_MODEL }

    /** The blocking reason, or null when a call may go out. */
    fun block(enabled: Boolean, hasKey: Boolean, model: String): Block? = when {
        !enabled -> Block.DISABLED
        !hasKey -> Block.NO_KEY
        model.isBlank() -> Block.NO_MODEL
        else -> null
    }

    /** True only when [block] is null: the settings Test call and both generation paths check this. */
    fun canRun(enabled: Boolean, hasKey: Boolean, model: String): Boolean =
        block(enabled, hasKey, model) == null
}

/**
 * The one minimal call behind the settings Test button (#602, 9.9): a fixed tiny
 * prompt with a small token cap — an auth and connectivity check, not a generation.
 * It goes through [AiProvider] like every other call; the key only travels in the
 * [KeySource] closure and is never logged or shown.
 */
object AiTestCall {

    const val PROMPT = "Reply with the single word OK."
    const val MAX_TOKENS = 16

    suspend fun run(providerId: String, baseUrl: String, model: String, key: String): AiResult {
        val config = AiProviderConfig(providerId, baseUrl, model)
        val provider = AiProviders.create(config, KeySource { key })
            ?: return AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "Unknown provider $providerId")
        return provider.complete(AiRequest(prompt = PROMPT, jsonMode = false, maxTokens = MAX_TOKENS))
    }
}

/**
 * User-facing text for a [AiResult] outcome (#602, 9.9), as string resources so every
 * wording lives in strings.xml. The key itself is never shown: the error message of an
 * [AiResult.Error] is scrubbed by the provider and only the kind is mapped here.
 */
object AiPracticeMessages {

    /** The settings result line for one [AiResult.Error] kind — every kind has a line. */
    @StringRes
    fun labelRes(kind: AiResult.ErrorKind): Int = when (kind) {
        AiResult.ErrorKind.AUTH -> R.string.ai_practice_key_rejected
        AiResult.ErrorKind.BILLING -> R.string.ai_practice_billing_blocked
        AiResult.ErrorKind.RATE_LIMIT -> R.string.ai_error_rate_limit
        AiResult.ErrorKind.NETWORK -> R.string.ai_error_network
        AiResult.ErrorKind.BAD_RESPONSE -> R.string.ai_error_bad_response
        AiResult.ErrorKind.SERVER -> R.string.ai_error_server
    }

    /** Latency as "1.2" for the "Works (model, 1.2 s)" result line. */
    fun secondsText(latencyMs: Long): String = String.format(java.util.Locale.US, "%.1f", latencyMs / 1000.0)

    /** The provider name shown to the user, e.g. "Melious" for the `melious` id. */
    fun providerLabel(providerId: String): String = when (providerId) {
        AiProviders.MELIOUS -> "Melious"
        else -> providerId.replaceFirstChar { it.uppercase() }
    }
}
