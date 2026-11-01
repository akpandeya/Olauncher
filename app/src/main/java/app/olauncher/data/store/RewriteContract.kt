package app.olauncher.data.store

import org.json.JSONObject

/**
 * The outcome of [RewriteContract.parse]: [Ok] carries the normalized rewritten
 * sentence, [Rejected] names the filter that dropped the answer and why — the
 * reason is short English suitable for appending to a retry prompt.
 */
sealed class RewriteParse {
    data class Ok(val text: String) : RewriteParse()
    data class Rejected(val filter: String, val reason: String) : RewriteParse()
}

/**
 * The AI contract for one-sentence rewrites (issue #693): [buildPrompt] asks for
 * a minimal edit of one saved sentence so it contains one saved word, and
 * [parse] validates the response's JSON structure only (later tasks add
 * [RewriteGuards] for containment, grounding and sanity). Rejection happens
 * before any text is produced — a truncated or non-object answer never yields
 * an [RewriteParse.Ok], and the caller reprompts with the rejection reason for
 * at most 2 retries.
 */
object RewriteContract {

    /**
     * One short sentence is a tiny output; a small budget keeps a runaway
     * generation from billing tokens for no usable answer. Sized for
     * `AiRequest.maxTokens`.
     */
    const val MAX_TOKENS = 256

    /**
     * A single-sentence generation can still queue on a slow provider, so the
     * read timeout is generous. Sized for `AiRequest.readTimeoutMs`.
     */
    const val READ_TIMEOUT_MS = 60_000

    /** Filter id: the response was not a usable JSON object at all. */
    const val FILTER_JSON = "json"

    /** Filter id: the JSON object has no usable `sentence` string. */
    const val FILTER_MISSING = "missing_sentence"

    /**
     * The rewrite prompt: the [source] sentence and the [word], verbatim and
     * JSON-quoted, the minimal-edit rule and the strict one-key response shape.
     * When [retryReason] is non-null and non-blank (a previous answer was
     * rejected by [parse] or the guards), the prompt ends with the rejection
     * reason verbatim so the model fixes exactly that. Same input gives the
     * same text.
     */
    fun buildPrompt(source: String, word: String, retryReason: String? = null): String {
        val builder = StringBuilder()
        builder.append("You prepare German reading practice from sentences a learner saved. ")
        builder.append("Turn the source sentence below into a practice sentence for the saved word.\n")
        builder.append("\n")
        builder.append("Source sentence: ${JSONObject.quote(source)}\n")
        builder.append("Saved word: ${JSONObject.quote(word)}\n")
        builder.append("\n")
        builder.append("Task: make the minimal edit to the source sentence so it naturally contains the saved word in a fitting inflection.\n")
        builder.append("- Adjust only the grammar around the saved word (endings, articles, verb forms, word order where needed).\n")
        builder.append("- Do not rewrite the sentence freely. Keep everything else unchanged.\n")
        builder.append("- Invent no new facts, names or numbers that are not in the source sentence.\n")
        builder.append("- Write one German sentence only.\n")
        builder.append("\n")
        builder.append("Respond with exactly this JSON object and nothing else:\n")
        builder.append("{\"sentence\": \"...\"}\n")
        if (!retryReason.isNullOrBlank()) {
            builder.append("\n")
            builder.append("Your previous answer was rejected: ${retryReason.trim()} ")
            builder.append("Fix exactly that problem and answer again with the same JSON shape.\n")
        }
        return builder.toString()
    }

    /**
     * Validates the raw AI response's structure: it must be a JSON object with a
     * non-blank `sentence` string. Anything else — invalid or truncated JSON, a
     * top-level array, a non-string value (numbers do not coerce to strings
     * here), a blank value — is rejected before any text is produced. Extra JSON
     * keys are ignored. The accepted sentence is normalized with
     * [RecentSentencesCodec.normalizeText] (NFC, nbsp to space, collapsed
     * whitespace, trimmed).
     */
    fun parse(raw: String): RewriteParse {
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            return RewriteParse.Rejected(
                filter = FILTER_JSON,
                reason = "The answer was not valid JSON (truncated or malformed)",
            )
        }
        val sentence = root.opt("sentence") as? String
        if (sentence == null) {
            return RewriteParse.Rejected(
                filter = FILTER_MISSING,
                reason = "The answer had no \"sentence\" string field",
            )
        }
        if (sentence.isBlank()) {
            return RewriteParse.Rejected(
                filter = FILTER_MISSING,
                reason = "The answer's \"sentence\" field was empty",
            )
        }
        return RewriteParse.Ok(RecentSentencesCodec.normalizeText(sentence))
    }
}
