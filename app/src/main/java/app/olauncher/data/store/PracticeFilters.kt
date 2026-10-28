package app.olauncher.data.store

/**
 * The post-generation quality filters (epic 9, prompt-v2 round): mechanical checks
 * the prompt cannot guarantee (docs/design/ai-practice.md "Quality filters"), run
 * by [PracticeContract.parse] on every returned item BEFORE it is stored. A rejected
 * item never reaches `practice_items.json`; the word still gets its cloze fallbacks.
 *
 * Each check returns a machine-readable filter id ([Verdict.filter]) so the benchmark
 * harness can record which filter caught an item.
 */
object PracticeFilters {

    /** word_order tiles, rearranged, do not spell out the answer's words. */
    const val WORD_ORDER = "word_order"

    /** A non-cloze item whose answer equals the word's already-accepted cloze answer. */
    const val DUPLICATE = "duplicate"

    /** The answer does not fit the gap: `prompt.replace("___", answer)` != `full`. */
    const val ANSWER_FIT = "answer_fit"

    /** The answer (or its bracketed base form) leaks into the hint or the prompt. */
    const val LEAK = "leak"

    /** A separable-verb answer that contains a space (a split prefix form). */
    const val SEPARABLE_SPACE = "separable_space"

    /** Outcome of one item's filter pass: rejected with a filter id, or accepted. */
    data class Verdict(val filter: String?, val reason: String?) {
        val rejected: Boolean get() = filter != null

        companion object {
            val ACCEPT = Verdict(filter = null, reason = null)
            fun reject(filter: String, reason: String) = Verdict(filter, reason)
        }
    }

    /**
     * The full filter pass over one candidate item. [clozeAnswers] holds the word's
     * already-accepted cloze answers (from the same response) for the duplicate
     * check; empty on the first item.
     */
    fun check(
        type: DrillType,
        prompt: String,
        answer: String,
        hint: String?,
        full: String?,
        clozeAnswers: Set<String>,
    ): Verdict {
        if (type == DrillType.WORD_ORDER) {
            wordOrder(prompt, answer)?.let { return it }
        }
        if (type != DrillType.CLOZE && clozeAnswers.isNotEmpty()) {
            val cloze = clozeAnswers.firstOrNull { normalize(it) == normalize(answer) }
            if (cloze != null) {
                return Verdict.reject(DUPLICATE, "answer '$answer' repeats the accepted cloze answer '$cloze'")
            }
        }
        answerFit(prompt, answer, full)?.let { return it }
        leak(prompt, answer, hint)?.let { return it }
        if (type == DrillType.SEPARABLE_VERBS && answer.trim().contains(' ')) {
            return Verdict.reject(SEPARABLE_SPACE, "separable-verb answer '$answer' contains a space")
        }
        return Verdict.ACCEPT
    }

    /**
     * Capitalization fix (rejects nothing): a prompt that starts with the gap puts
     * the answer at sentence start, so the answer's first letter is capitalized
     * ("die" → "Die"). Returns the answer unchanged otherwise.
     */
    fun fixCapitalization(prompt: String, answer: String): String {
        if (!prompt.startsWith(DrillItem.GAP_MARKER)) return answer
        val first = answer.firstOrNull { !it.isWhitespace() } ?: return answer
        val upper = first.uppercaseChar()
        if (upper == first) return answer
        return answer.replaceFirst(first, upper)
    }

    /**
     * word_order: the prompt's " / " tile list, rearranged, must spell out exactly
     * the answer's words — ignoring case and punctuation, nothing added, dropped,
     * split or merged. A prompt without a tile list is unverifiable and rejected.
     */
    private fun wordOrder(prompt: String, answer: String): Verdict? {
        if (!prompt.contains(" / ")) {
            return Verdict.reject(WORD_ORDER, "prompt has no \" / \" tile list")
        }
        val tilePart = if (prompt.startsWith("Ordne:")) prompt.removePrefix("Ordne:") else prompt
        val tiles = tilePart.split(" / ").map { normalize(it) }.filter { it.isNotBlank() }
        val answerWords = normalize(answer).split(' ').filter { it.isNotBlank() }
        if (tiles.isEmpty() || answerWords.isEmpty()) {
            return Verdict.reject(WORD_ORDER, "empty tile list or answer")
        }
        if (tiles.sorted() != answerWords.sorted()) {
            return Verdict.reject(WORD_ORDER, "tiles $tiles do not spell the answer words $answerWords")
        }
        return null
    }

    /**
     * answer-fits-blank: with the optional `full` field, filling the gap must give
     * exactly the full sentence (whitespace-normalized). Checked only when the
     * prompt has a gap and the model sent `full` — old output without it skips the
     * exact check.
     */
    private fun answerFit(prompt: String, answer: String, full: String?): Verdict? {
        if (full.isNullOrBlank() || !prompt.contains(DrillItem.GAP_MARKER)) return null
        val filled = normalizeSentence(prompt.replace(DrillItem.GAP_MARKER, answer))
        val expected = normalizeSentence(full)
        if (filled != expected) {
            return Verdict.reject(ANSWER_FIT, "answer '$answer' in the gap gives '$filled', model's full sentence is '$expected'")
        }
        return null
    }

    /**
     * Leaks: the answer appears verbatim in the hint, a base form the prompt
     * brackets appears in the hint ("___ (anwachsen)" + hint "anwachsen"), or a
     * bracketed form in the prompt IS the answer ("___ (kurzfristig)").
     * Very short answers (1-2 chars, e.g. an article) are skipped — a substring
     * test on them would fire on almost any German text.
     */
    private fun leak(prompt: String, answer: String, hint: String?): Verdict? {
        val trimmed = answer.trim()
        val brackets = Regex("\\(([^)]*)\\)").findAll(prompt)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (brackets.any { normalize(it) == normalize(trimmed) }) {
            return Verdict.reject(LEAK, "prompt's bracketed form reveals the answer '$trimmed'")
        }
        if (hint == null || trimmed.length < MIN_LEAK_ANSWER_LEN) return null
        val hintLower = normalize(hint)
        // Whole-word matching: a raw substring test fired on compounds ("mit"
        // matched "Mitgliedsstaat") and cost correct preposition items.
        if (containsWord(hintLower, normalize(trimmed))) {
            return Verdict.reject(LEAK, "hint contains the answer '$trimmed'")
        }
        val base = brackets.firstOrNull { normalize(it) != normalize(trimmed) }
        if (base != null && base.length >= MIN_LEAK_ANSWER_LEN && containsWord(hintLower, normalize(base))) {
            return Verdict.reject(LEAK, "hint contains the prompt's base form '$base' for answer '$trimmed'")
        }
        return null
    }

    /** Case-insensitive, punctuation-stripped comparison form. */
    internal fun normalize(text: String): String =
        text.lowercase().replace(Regex("[.,;:!?\"'»«()\\[\\]]"), "").trim()

    /**
     * Sentence comparison form for answer-fits-blank: lowercase, punctuation
     * collapsed to spaces (the model writes ", " where the prompt has " - " and
     * both mean the same sentence), whitespace collapsed.
     */
    private fun normalizeSentence(text: String): String =
        text.lowercase()
            .replace(Regex("[.,;:!?\"'»«()\\[\\]„“–—-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /**
     * Whole-word containment in the normalized (punctuation-stripped) [text]:
     * [needle] must be bounded by string edges or non-letter characters, so
     * "mit" no longer matches inside "mitgliedsstaat".
     */
    private fun containsWord(text: String, needle: String): Boolean {
        if (needle.isBlank()) return false
        val boundaries = Regex("(^|[^\\p{L}\\p{Nd}])${Regex.escape(needle)}([^\\p{L}\\p{Nd}]|$)")
        return boundaries.containsMatchIn(text)
    }

    /** Answers shorter than this are exempt from the substring leak check. */
    private const val MIN_LEAK_ANSWER_LEN = 3
}
