package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * The context-free contract for the third tier of the join cascade (#523): when the
 * bundled dictionary ([SplitVerbs.find]) and the online composition both find no split
 * verb, the AI is asked about one tapped word and the paragraph it came from.
 *
 * The AI only ever PROPOSES an [Answer]; the wiring verifies the lemma against the
 * dictionary before any chip, join or save happens, so a wrong proposal shows nothing.
 */
object SplitVerbAi {

    /** Small output budget: the answer is two keys, and this runs on a tap path. */
    const val MAX_TOKENS = 128

    /** Short read timeout: a tap must never be blocked by a slow provider. */
    const val READ_TIMEOUT_MS = 8_000

    /** One parsed answer. [lemma] is the proposed infinitive with its particle, e.g. "aufnehmen". */
    data class Answer(val isSplitVerb: Boolean, val lemma: String?)

    /**
     * The prompt for one tapped word: the paragraph (JSON-quoted, so the text cannot break
     * out of its field) and the tapped token itself, then the two-key reply shape. Same
     * input gives the same text.
     */
    fun buildPrompt(sentence: String, tapped: String): String = buildString {
        append("A learner tapped one word in a German sentence. Say whether that word is part of a ")
        append("separable verb in this sentence, and give the verb's dictionary lemma.\n")
        append("\n")
        append("Sentence: ")
        append(JSONObject.quote(sentence))
        append("\n")
        append("Tapped word: ")
        append(JSONObject.quote(tapped))
        append("\n")
        append("\n")
        append("Rules:\n")
        append("- isSplitVerb is true only when the tapped word is one part of a separable verb whose ")
        append("particle is separated from it in this sentence, e.g. \"nehme\" in \"Ich nehme den Koffer auf.\".\n")
        append("- lemma is the full infinitive with the particle attached, lowercased (e.g. \"aufnehmen\"); ")
        append("when isSplitVerb is false, lemma is null.\n")
        append("- No explanations and no extra keys.\n")
        append("\n")
        append("Respond with exactly one JSON object:\n")
        append("{\"isSplitVerb\": true, \"lemma\": \"aufnehmen\"}\n")
    }

    /**
     * The strict response schema for `response_format: json_schema`, following the practice
     * contract's convention: both keys required, `additionalProperties` false. A provider
     * without strict-schema support is retried with `json_object` ([AiRequest.jsonSchema]).
     */
    fun schema(): String = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject()
                .put("isSplitVerb", JSONObject().put("type", "boolean"))
                .put("lemma", JSONObject().put("type", JSONArray(listOf("string", "null")))),
        )
        .put("required", JSONArray(listOf("isSplitVerb", "lemma")))
        .put("additionalProperties", false)
        .toString()

    /**
     * Parses and validates one response. Strict: a JSON object with exactly the two
     * contract keys, a real boolean `isSplitVerb`, and — when it is true — a non-blank
     * string `lemma`; a false answer carries no lemma. Any partial or out-of-contract
     * input yields null, never a half-filled [Answer].
     */
    fun parseResponse(text: String): Answer? {
        // org.json's JSONObject(String) does not require the input to be exhausted — a valid
        // object followed by junk (e.g. a trailing code fence) would still parse. The answer
        // must be exactly one object, so reject anything that does not end on the closing brace.
        if (!text.trim().endsWith("}")) return null
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key != KEY_SPLIT && key != KEY_LEMMA) return null
        }
        val isSplitVerb = json.opt(KEY_SPLIT) as? Boolean ?: return null
        val lemmaValue = if (json.has(KEY_LEMMA) && !json.isNull(KEY_LEMMA)) json.opt(KEY_LEMMA) else null
        if (!isSplitVerb) {
            // A false answer is exactly "false with no lemma"; anything else is out of contract.
            return if (lemmaValue == null) Answer(false, null) else null
        }
        val lemma = lemmaValue as? String ?: return null
        return if (lemma.isBlank()) null else Answer(true, lemma)
    }

    private const val KEY_SPLIT = "isSplitVerb"
    private const val KEY_LEMMA = "lemma"
}
