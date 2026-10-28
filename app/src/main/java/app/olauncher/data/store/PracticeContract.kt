package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject

/**
 * The outcome of [PracticeContract.parse]: the valid AI [items], the locally built
 * [clozeFallbacks] that fill the missing slots, and the [rejected] problems.
 */
data class PracticeParseResult(
    val items: List<PracticeItem> = emptyList(),
    val clozeFallbacks: List<PracticeItem> = emptyList(),
    val rejected: List<String> = emptyList(),
)

/**
 * The AI contract for practice generation (docs/design/ai-practice.md):
 * [buildPrompt] asks for 3 items per word (one CLOZE + two other drill types),
 * and [parse] validates the response so a bad response never drops a word.
 */
object PracticeContract {

    /** Owner decision (2026-10-27): every word gets a pool of 3 items per generation. */
    const val ITEMS_PER_WORD = 3

    /** Shortest shared stem for a context token to count as the word's inflected form. */
    private const val MIN_FALLBACK_STEM = 3

    /**
     * The generation prompt: every requested word with its saved [Word.context], the
     * allowed [DrillType] ids and the JSON schema, asking for [ITEMS_PER_WORD] items
     * per word (one CLOZE + two other types). Same input gives the same text.
     */
    fun buildPrompt(words: List<Word>, types: List<DrillType>): String {
        val builder = StringBuilder()
        builder.append("You create German practice drills from saved words.\n")
        builder.append("\n")
        builder.append("For every word below, produce exactly $ITEMS_PER_WORD items: one of type \"cloze\" ")
        builder.append("and two of the other allowed types. Ground every item in the word's saved context.\n")
        builder.append("\n")
        builder.append("Allowed types: ${types.joinToString(", ") { it.id }}\n")
        builder.append("\n")
        builder.append("Words:\n")
        for (word in words) {
            builder.append("- word_id ${word.id}: ${word.text}")
            if (word.context.isNotBlank()) {
                builder.append(" | context: ${word.context}")
            }
            builder.append("\n")
        }
        builder.append("\n")
        builder.append("Respond with one JSON object and nothing else:\n")
        builder.append("{\"items\": [{\"word_id\": 1, \"type\": \"cloze\", \"prompt\": \"...\", ")
        builder.append("\"answer\": \"...\", \"choices\": [\"...\", \"...\", \"...\"], \"hint\": \"optional\"}]}\n")
        builder.append("\n")
        builder.append("Rules:\n")
        builder.append("- cloze prompts use ${DrillItem.GAP_MARKER} for the gap\n")
        builder.append("- the answer must be one of the choices; use 2-4 distinct choices\n")
        builder.append("- the hint is optional and must never reveal the answer\n")
        builder.append("- every word_id from the list above gets its $ITEMS_PER_WORD items\n")
        return builder.toString()
    }

    /**
     * Validates an AI response against the requested words. Completeness is checked
     * independently of the response count: every requested word id needs at least
     * [ITEMS_PER_WORD] valid items (multi-item pool); missing slots become cloze
     * fallbacks built from the saved context, so a word is never dropped. Truncated
     * or malformed JSON is rejected before any item is produced. Extra JSON keys
     * are ignored.
     */
    fun parse(
        json: String,
        requestedWords: List<Word>,
        model: String = "",
        source: String = PracticeItem.Source.WORD,
    ): PracticeParseResult {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return PracticeParseResult(
                rejected = listOf("Not valid JSON (truncated or malformed); rejected before any item was produced"),
            )
        }

        val requested = requestedWords.associateBy { it.id }
        val pools = LinkedHashMap<Long, MutableList<PracticeItem>>()
        for (id in requested.keys) {
            pools[id] = mutableListOf()
        }
        val rejected = mutableListOf<String>()

        val itemsArray = root.optJSONArray("items")
        if (itemsArray == null) {
            rejected += "Response has no items array; every word falls back to a cloze"
        } else {
            for (i in 0 until itemsArray.length()) {
                val obj = try {
                    itemsArray.getJSONObject(i)
                } catch (e: Exception) {
                    rejected += "Item $i is not a JSON object"
                    continue
                }
                val word = wordIdOf(obj)?.let { requested[it] }
                if (word == null) {
                    rejected += "Item $i has an unknown or missing word_id"
                    continue
                }
                val typeRaw = obj.optString("type")
                val type = DrillType.fromId(typeRaw)
                val prompt = obj.optString("prompt")
                val answer = obj.optString("answer")
                val choices = choicesOf(obj)
                val hint = optionalString(obj, "hint")
                val check = DrillItem(
                    id = DrillItemId(type ?: DrillType.CLOZE, "practice:${word.id}"),
                    prompt = prompt,
                    answer = answer,
                    choices = choices,
                    hint = hint,
                ).validate()
                when {
                    type == null ->
                        rejected += "Item $i: unknown type '$typeRaw' — replaced with a cloze fallback"
                    !check.isValid ->
                        rejected += "Item $i: ${check.problems.joinToString("; ")} — replaced with a cloze fallback"
                    else ->
                        pools.getValue(word.id) += PracticeItem(
                            wordId = word.id,
                            type = type,
                            prompt = prompt,
                            answer = answer,
                            choices = choices,
                            hint = hint,
                            source = source,
                            model = model.ifBlank { null },
                        )
                }
            }
        }

        val items = mutableListOf<PracticeItem>()
        val fallbacks = mutableListOf<PracticeItem>()
        for ((wordId, pool) in pools) {
            items += pool
            val missing = ITEMS_PER_WORD - pool.size
            if (missing > 0) {
                fallbacks += clozeFallbacks(requested.getValue(wordId), missing, source)
            }
        }
        return PracticeParseResult(items = items, clozeFallbacks = fallbacks, rejected = rejected)
    }

    /**
     * Builds one cloze fallback per missing slot from the saved context, with the
     * word blanked. The choices rotate per slot so the pool does not hold three
     * identical cards. A word with blank text gets a deterministic minimal card —
     * the word is never dropped (review R1 #9).
     */
    private fun clozeFallbacks(word: Word, count: Int, source: String): List<PracticeItem> {
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

    /** Distinct deterministic choices containing the answer; no case-flip padding. */
    private fun fallbackChoices(answer: String, text: String): List<String> {
        val choices = LinkedHashSet<String>()
        for (candidate in listOf(answer, text, "${text}n")) {
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
