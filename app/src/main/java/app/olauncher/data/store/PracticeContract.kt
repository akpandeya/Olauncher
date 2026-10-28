package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * The outcome of [PracticeContract.parse]: the valid AI [items], the locally built
 * [clozeFallbacks] that fill the missing slots, the [rejected] problems, and the
 * structured [rejections] (every dropped item with the filter that dropped it —
 * the benchmark harness records the filter per raw item).
 */
data class PracticeParseResult(
    val items: List<PracticeItem> = emptyList(),
    val clozeFallbacks: List<PracticeItem> = emptyList(),
    val rejected: List<String> = emptyList(),
    val rejections: List<PracticeRejection> = emptyList(),
    /** True when the response itself was unusable (invalid JSON or no items array). */
    val malformed: Boolean = false,
)

/**
 * One dropped item, structured: [filter] names what dropped it — a quality filter
 * id ([PracticeFilters]), or `contract` (failed `DrillItem.validate`),
 * `unknown_type`, or `word_id` (unknown or missing word id).
 */
data class PracticeRejection(
    val wordId: Long?,
    val type: DrillType?,
    val prompt: String,
    val answer: String,
    val choices: List<String>,
    val hint: String?,
    val filter: String,
    val reason: String,
    val full: String? = null,
)

/**
 * Which `response_format` a generation request uses. Production starts each model
 * at its measured-best mode ([initialMode]) and falls back to `json_object` only
 * on the provider's capability/limit codes — the same rule the benchmark harness
 * applies ([AiBenchLogic.SCHEMA_FALLBACK_CODES]).
 */
object PracticeSchemaModes {

    const val JSON_SCHEMA = "json_schema"
    const val JSON_OBJECT = "json_object"

    /**
     * The model whose measured-best mode is `json_object` (round-3 review
     * condition 1): glm-5.3-flash passed the benchmark in json_object (97.2 %
     * validity) while its strict-schema runs lost whole batches to runaway
     * generations — in production a cut-off runaway comes back as BAD_RESPONSE,
     * which never triggers the json_object fallback, so a billed call would
     * yield only cloze fallbacks. Context-free mapping; a per-model config
     * table is deliberately out of scope (owner, 2026-10-28).
     */
    const val JSON_OBJECT_MODEL = "glm-5.3-flash"

    /**
     * The mode a request to [model] starts in: `json_object` for the model with
     * the measured preference, strict `json_schema` for everything else (the
     * previous behaviour). Context-free and unit-tested.
     */
    fun initialMode(model: String): String =
        if (model == JSON_OBJECT_MODEL) JSON_OBJECT else JSON_SCHEMA

    /**
     * Error codes that mean "this model cannot honour the strict schema": no
     * schema support (`model_not_capable`) or a schema payload that busts the
     * context window (`context_length_exceeded`). Any other failure is real and
     * must be recorded, not hidden behind a mode switch.
     */
    val FALLBACK_ERROR_CODES = setOf("model_not_capable", "context_length_exceeded")

    /**
     * The mode to retry with after [error], or null when [current] already is
     * `json_object` or the error is a real failure. Context-free and unit-tested.
     */
    fun fallbackMode(current: String, error: AiResult.Error): String? =
        if (current == JSON_SCHEMA && error.errorCode in FALLBACK_ERROR_CODES) JSON_OBJECT else null
}

/**
 * The AI contract for practice generation (docs/design/ai-practice.md):
 * [buildPrompt] asks for 3 items per word (one cloze + two other drill types — a
 * second cloze is allowed when only one other type fits), and [parse] validates the
 * response so a bad response never drops a word: schema validation, the quality
 * filters ([PracticeFilters]), the completeness check and the cloze fallback.
 */
object PracticeContract {

    /** Owner decision (2026-10-27): every word gets a pool of 3 items per generation. */
    const val ITEMS_PER_WORD = 3

    /** Shortest shared stem for a context token to count as the word's inflected form. */
    private const val MIN_FALLBACK_STEM = 3

    /**
     * The generation prompt (v2, 2026-10-28): every requested word with its saved
     * context (JSON-quoted) and meaning, the allowed [DrillType] ids, the type
     * rules and the JSON schema, asking for [ITEMS_PER_WORD] items per word. Same
     * input gives the same text. [typesByWord] optionally narrows the allowed
     * types per word ([PracticeTypeNarrowing]); a word without an entry picks from
     * the full [types] list.
     */
    fun buildPrompt(
        words: List<Word>,
        types: List<DrillType>,
        typesByWord: Map<Long, List<DrillType>> = emptyMap(),
    ): String {
        val builder = StringBuilder()
        builder.append(
            "You write German practice cards from words a learner saved while reading. " +
                "The learner sees only \"prompt\" and \"hint\", says the answer aloud, then sees " +
                "\"answer\" and grades themself. Write prompts and hints in German.\n",
        )
        builder.append("\n")
        builder.append("For every word below, write exactly $ITEMS_PER_WORD cards:\n")
        builder.append("1. One \"cloze\": the word's context sentence with the word, in the form used there, replaced by ___.\n")
        builder.append("2. Two cards of other types from this list. Pick only types that fit this word: ")
        builder.append("${types.joinToString(", ") { it.id}}\n")
        builder.append("   If only one other type fits, make the last card a second \"cloze\" that blanks a different form of the word.\n")
        builder.append("\n")
        builder.append("Rules for every card:\n")
        builder.append("- The gap tests the saved word itself: one of its forms, or the article, ending or preposition attached to it. Never test another word in the sentence.\n")
        builder.append("- Reuse the context sentence. You may drop whole clauses, but keep the words around the gap unchanged and invent no facts. Only if a word has no context, write one short, natural sentence.\n")
        builder.append("- Exactly one ___ per prompt. Putting the answer into ___ must give a correct German sentence with normal capitalisation (\"Die …\" at sentence start).\n")
        builder.append("- full: the prompt with its ___ replaced by the answer — exactly the sentence the learner reads when the gap is filled, not the original context sentence.\n")
        builder.append("- The $ITEMS_PER_WORD cards of one word have $ITEMS_PER_WORD different answers.\n")
        builder.append("- Neither prompt nor hint may contain the answer or spell it out (no \"bei + dem\" for \"beim\"). A base form in brackets, \"___ (anwachsen)\", is allowed only if the answer differs from it.\n")
        builder.append("- hint: a German synonym or short definition, or tense and person (\"Präsens, 3. Person Singular\"). At most 6 words, no rule explanations.\n")
        builder.append("- \"type\" names what the gap tests: articles → an article; plurals → a plural noun; conjugation, praeteritum, modal_verbs → a finite verb form; prepositions, verb_prepositions → a preposition; case_endings, adjective_endings, genitive, n_declension → a form whose ending is the point; any other type → its grammar point.\n")
        builder.append("\n")
        builder.append("Type rules:\n")
        builder.append("- Separable verb in a main clause: the gap takes only the finite verb; the prefix stays in the prompt at the clause end. \"Das Personal ___ von 3.000 auf 6.000 an.\" → \"wächst\". After a modal or in a subordinate clause the verb is one word: \"…, weil das Personal ___.\" → \"anwächst\".\n")
        builder.append("- articles: blank an article only if exactly one article fits (\"___ eigenes Zentrum\" → \"ein\"). If a definite and an indefinite article would both fit, write \"bestimmter Artikel\" or \"unbestimmter Artikel\" as the hint.\n")
        builder.append("- word_order: take one main clause of 4-8 words from the context. prompt = \"Ordne: \" + its words shuffled, one word per tile, separated by \" / \". answer = exactly those tiles in the right order: same words, same spelling, nothing added, dropped, split or merged, no comma. choices = the answer plus 1-2 wrong orders of the same tiles.\n")
        builder.append("- All other types: choices = the answer plus 1-3 wrong forms of the same word (e.g. \"weigert\", \"weigern\", \"weigerst\").\n")
        builder.append("\n")
        builder.append("Words:\n")
        for (word in words) {
            builder.append("- word_id ${word.id}: ${word.text}\n")
            if (word.context.isNotBlank()) {
                builder.append("  context: ${JSONObject.quote(word.context)}\n")
            }
            if (word.meaning.isNotBlank()) {
                builder.append("  meaning: ${word.meaning}\n")
            }
            val narrowed = typesByWord[word.id]
            if (narrowed != null && narrowed.isNotEmpty()) {
                builder.append("  types: ${narrowed.joinToString(", ") { it.id }}\n")
            }
        }
        builder.append("\n")
        builder.append("Respond with one JSON object and nothing else:\n")
        builder.append(
            "{\"items\": [{\"word_id\": 1, \"type\": \"cloze\", \"prompt\": \"...\", \"answer\": \"...\", " +
                "\"choices\": [\"...\", \"...\"], \"full\": \"...\", \"hint\": \"...\"}]}\n",
        )
        return builder.toString()
    }

    /**
     * The strict response schema for `response_format: json_schema`, derived from
     * the v2 contract's response shape and shared with the benchmark harness
     * (single source of truth). `hint` is omitted so it cannot break strictness
     * (the parser treats it as optional); `full` is required — the answer-fits-blank
     * filter needs it. All properties required, `additionalProperties` false, per
     * the strict schema convention.
     */
    fun responseSchemaJson(): String {
        val item = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("word_id", JSONObject().put("type", "integer"))
                    .put(
                        "type",
                        JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray(DrillType.values().map { it.id })),
                    )
                    .put("prompt", JSONObject().put("type", "string"))
                    .put("answer", JSONObject().put("type", "string"))
                    .put(
                        "choices",
                        JSONObject()
                            .put("type", "array")
                            .put("items", JSONObject().put("type", "string")),
                    )
                    .put("full", JSONObject().put("type", "string")),
            )
            .put(
                "required",
                JSONArray(listOf("word_id", "type", "prompt", "answer", "choices", "full")),
            )
            .put("additionalProperties", false)
        return JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject().put(
                    "items",
                    JSONObject()
                        .put("type", "array")
                        .put("items", item),
                ),
            )
            .put("required", JSONArray(listOf("items")))
            .put("additionalProperties", false)
            .toString()
    }

    /**
     * Validates an AI response against the requested words. Completeness is checked
     * independently of the response count: every requested word id needs at least
     * [ITEMS_PER_WORD] valid items (multi-item pool); missing slots become cloze
     * fallbacks built from the saved context, so a word is never dropped. Truncated
     * or malformed JSON is rejected before any item is produced. Extra JSON keys
     * are ignored.
     *
     * The quality filters ([PracticeFilters]) run on every item before it is
     * accepted; a filter-rejected item never reaches the pool. With
     * [padFallbacks] = false the cloze fallbacks are NOT built (the caller fills
     * the missing slots itself after a retry — see [PracticeGenerator]); the
     * completeness gap is then visible as a per-word pool below [ITEMS_PER_WORD].
     */
    fun parse(
        json: String,
        requestedWords: List<Word>,
        model: String = "",
        source: String = PracticeItem.Source.WORD,
        padFallbacks: Boolean = true,
    ): PracticeParseResult {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return PracticeParseResult(
                rejected = listOf("Not valid JSON (truncated or malformed); rejected before any item was produced"),
                malformed = true,
            )
        }

        val requested = requestedWords.associateBy { it.id }
        val pools = LinkedHashMap<Long, MutableList<PracticeItem>>()
        for (id in requested.keys) {
            pools[id] = mutableListOf()
        }
        val rejected = mutableListOf<String>()
        val rejections = mutableListOf<PracticeRejection>()

        val itemsArray = root.optJSONArray("items")
        if (itemsArray == null) {
            rejected += "Response has no items array; every word falls back to a cloze"
            return finish(pools, requested, rejected, rejections, source, model, padFallbacks, malformed = true)
        }

        for (i in 0 until itemsArray.length()) {
            val obj = try {
                itemsArray.getJSONObject(i)
            } catch (e: Exception) {
                rejected += "Item $i is not a JSON object"
                continue
            }
            val echoedId = wordIdOf(obj)
            val word = echoedId?.let { requested[it] }
            if (word == null) {
                rejected += "Item $i has an unknown or missing word_id"
                rejections += PracticeRejection(
                    wordId = echoedId, type = DrillType.fromId(obj.optString("type")),
                    prompt = obj.optString("prompt"), answer = obj.optString("answer"),
                    choices = choicesOf(obj), hint = optionalString(obj, "hint"),
                    filter = "word_id", reason = "unknown or missing word_id",
                    full = optionalString(obj, "full"),
                )
                continue
            }
            val typeRaw = obj.optString("type")
            val type = DrillType.fromId(typeRaw)
            val prompt = obj.optString("prompt")
            val answer = obj.optString("answer")
            val choices = choicesOf(obj)
            val hint = optionalString(obj, "hint")
            val full = optionalString(obj, "full")
            // Capitalization fix first (a sentence-initial gap capitalizes the
            // answer); the matching choice is fixed with it, so the validated
            // and stored item keeps answer ∈ choices.
            val fixedAnswer = PracticeFilters.fixCapitalization(prompt, answer)
            val fixedChoices = if (fixedAnswer != answer) choices.map { if (it == answer) fixedAnswer else it } else choices
            val check = DrillItem(
                id = DrillItemId(type ?: DrillType.CLOZE, "practice:${word.id}"),
                prompt = prompt,
                answer = fixedAnswer,
                choices = fixedChoices,
                hint = hint,
            ).validate()
            if (type == null) {
                rejected += "Item $i: unknown type '$typeRaw' — replaced with a cloze fallback"
                rejections += PracticeRejection(
                    wordId = word.id, type = null, prompt = prompt, answer = answer,
                    choices = choices, hint = hint, filter = "unknown_type",
                    reason = "unknown type '$typeRaw'", full = full,
                )
                continue
            }
            if (!check.isValid) {
                rejected += "Item $i: ${check.problems.joinToString("; ")} — replaced with a cloze fallback"
                rejections += PracticeRejection(
                    wordId = word.id, type = type, prompt = prompt, answer = fixedAnswer,
                    choices = fixedChoices, hint = hint, filter = "contract",
                    reason = check.problems.joinToString("; "), full = full,
                )
                continue
            }
            val clozeAnswers = pools.getValue(word.id)
                .filter { it.type == DrillType.CLOZE }
                .map { it.answer }
                .toSet()
            val verdict = PracticeFilters.check(type, prompt, fixedAnswer, hint, full, clozeAnswers)
            if (verdict.rejected) {
                rejected += "Item $i: filter '${verdict.filter}' — ${verdict.reason}"
                rejections += PracticeRejection(
                    wordId = word.id, type = type, prompt = prompt, answer = fixedAnswer,
                    choices = fixedChoices, hint = hint, filter = verdict.filter!!,
                    reason = verdict.reason ?: "", full = full,
                )
                continue
            }
            pools.getValue(word.id) += PracticeItem(
                wordId = word.id,
                type = type,
                prompt = prompt,
                answer = fixedAnswer,
                choices = fixedChoices,
                hint = hint,
                source = source,
                model = model.ifBlank { null },
                full = full,
            )
        }

        return finish(pools, requested, rejected, rejections, source, model, padFallbacks, malformed = false)
    }

    /** Builds the result: fallback padding (unless [padFallbacks]) plus the lists. */
    private fun finish(
        pools: LinkedHashMap<Long, MutableList<PracticeItem>>,
        requested: Map<Long, Word>,
        rejected: List<String>,
        rejections: List<PracticeRejection>,
        source: String,
        model: String,
        padFallbacks: Boolean,
        malformed: Boolean,
    ): PracticeParseResult {
        val items = mutableListOf<PracticeItem>()
        val fallbacks = mutableListOf<PracticeItem>()
        for ((wordId, pool) in pools) {
            items += pool
            val missing = ITEMS_PER_WORD - pool.size
            if (padFallbacks && missing > 0) {
                fallbacks += fallbacksFor(requested.getValue(wordId), missing, source)
            }
        }
        return PracticeParseResult(
            items = items,
            clozeFallbacks = fallbacks,
            rejected = rejected,
            rejections = rejections,
            malformed = malformed,
        )
    }

    /**
     * Builds one cloze fallback per missing slot from the saved context, with the
     * word blanked. The choices rotate per slot so the pool does not hold three
     * identical cards. A word with blank text gets a deterministic minimal card —
     * the word is never dropped (review R1 #9). Public so the generator can fill
     * whatever stays missing after its retry-on-missing pass.
     */
    fun fallbacksFor(word: Word, count: Int, source: String): List<PracticeItem> {
        val base = clozeFallback(word, source)
        return (0 until count).map { slot ->
            if (slot == 0) base
            else base.copy(choices = base.choices.drop(slot) + base.choices.take(slot))
        }
    }

    /**
     * A cloze built from the saved [Word.context] with the word blanked. When the
     * word appears only in an inflected form, the context token sharing the longest
     * stem with it is blanked instead. Without usable context the card is a bare gap
     * with the word as the answer — the word is never dropped. A word with blank
     * text gets a deterministic minimal card (the saved meaning, else the word id,
     * as the answer) so "never dropped" holds for it too (review R1 #9). There is
     * no hint, so the fallback can never reveal the answer (#643).
     */
    private fun clozeFallback(word: Word, source: String): PracticeItem {
        val text = word.text.trim()
        if (text.isEmpty()) {
            val answer = word.meaning.trim().ifEmpty { word.id.toString() }
            return PracticeItem(
                wordId = word.id,
                type = DrillType.CLOZE,
                prompt = DrillItem.GAP_MARKER,
                answer = answer,
                choices = listOf(answer, "$answer?"),
                hint = null,
                source = source,
                model = null,
            )
        }
        val span = blankSpan(word.context, text)
        val prompt: String
        val answer: String
        if (span != null) {
            val (index, matched) = span
            prompt = word.context.substring(0, index) + DrillItem.GAP_MARKER +
                word.context.substring(index + matched.length)
            answer = matched
        } else {
            prompt = DrillItem.GAP_MARKER
            answer = text
        }
        return PracticeItem(
            wordId = word.id,
            type = DrillType.CLOZE,
            prompt = prompt,
            answer = answer,
            choices = fallbackChoices(answer, text),
            hint = null,
            source = source,
            model = null,
        )
    }

    /**
     * Finds what to blank in the saved context: the first occurrence of the word
     * (case-insensitive), else the context token sharing the longest stem with it
     * (an inflected form), else null.
     */
    private fun blankSpan(context: String, text: String): Pair<Int, String>? {
        val exact = context.indexOf(text, ignoreCase = true)
        if (exact >= 0) return exact to context.substring(exact, exact + text.length)

        var best: Pair<Int, String>? = null
        var bestStem = 0
        var i = 0
        while (i < context.length) {
            while (i < context.length && context[i].isWhitespace()) i++
            if (i >= context.length) break
            var j = i
            while (j < context.length && !context[j].isWhitespace()) j++
            val token = context.substring(i, j).trimEnd('.', ',', '!', '?', ':', ';', '"', '\'', '»', '«', '“', '”')
            val stem = text.commonPrefixWith(token, ignoreCase = true).length
            if (stem >= MIN_FALLBACK_STEM && stem * 2 >= text.length && stem > bestStem) {
                bestStem = stem
                best = i to token
            }
            i = j
        }
        return best
    }

    /**
     * Distinct deterministic choices containing the answer; no case-flip padding.
     * A trailing question mark (the blank-text card's style) fills the third slot
     * when the word itself adds no distinct form, so the three-slot rotation
     * never repeats a card (review R2 E).
     */
    private fun fallbackChoices(answer: String, text: String): List<String> {
        val choices = LinkedHashSet<String>()
        for (candidate in listOf(answer, text, "${text}n", "$answer?")) {
            choices.add(candidate)
            if (choices.size >= 3) break
        }
        return choices.toList()
    }

    /** The AI echoes word_id as a number or a string; both decode. */
    private fun wordIdOf(obj: JSONObject): Long? = when (val raw = obj.opt("word_id")) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }

    private fun choicesOf(obj: JSONObject): List<String> {
        val array = obj.optJSONArray("choices") ?: return emptyList()
        return (0 until array.length()).map { array.optString(it) }
    }

    private fun optionalString(obj: JSONObject, key: String): String? {
        return if (obj.has(key) && !obj.isNull(key)) obj.getString(key) else null
    }
}
