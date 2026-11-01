package app.olauncher.data.store

import java.util.Locale

/**
 * Containment, grounding and sanity checks for an AI-rewritten harvested sentence
 * (issue #693): a rewrite is accepted only if it still contains the saved word (or
 * an inflection of it), stays lexically grounded in the source sentence (no
 * hallucinated content) and looks like a clean German sentence. Embedding/cosine
 * similarity is deliberately out of scope (follow-up issue).
 *
 * Each guard returns a machine-readable filter id ([RewriteVerdict.filter]) so the
 * caller can record which guard caught a rewrite. The human-readable reason is
 * appended to the retry prompt, so guards phrase it as a short English instruction.
 *
 * [SentenceWordMatcher] provides lemma keys via the offline dictionary; when that
 * dictionary is switched off, `keyOf` falls back to the form itself and the
 * containment stem fallback below keeps inflected forms working.
 */
object RewriteGuards {

    const val SANITY_LENGTH = "sanity_length"
    const val SANITY_ARTIFACT = "sanity_artifact"
    const val SANITY_SCRIPT = "sanity_script"
    const val SANITY_PUNCTUATION = "sanity_punctuation"
    const val CONTAINMENT = "containment"
    const val GROUNDING = "grounding"

    /** Sanity window for the trimmed rewrite, in characters. */
    const val MIN_CHARS = 20
    const val MAX_CHARS = 250

    /** Minimum share of non-target rewrite tokens that must come from the source. */
    const val MIN_GROUNDED_SHARE = 0.70

    /** Maximum number of distinct new (ungrounded) words a rewrite may introduce. */
    const val MAX_NEW_TOKENS = 3

    /** Prompt artifacts that betray a model echoing the instructions instead of writing a sentence. */
    private val ARTIFACTS = listOf("___", "{", "}", "```", "rewrite:", "satz:", "sentence:")

    /** Closing punctuation, optionally followed by German/English closing quotes. */
    private val FINAL_PUNCTUATION_REGEX = Regex("""[.!?\u2026]["\u201C\u201D\u00BB\u00AB']*${'$'}""")

    private val LEADING_ARTICLE_REGEX = Regex("""^(?i)(?:der|die|das|ein|eine)\s+""")

    /**
     * The full guard pass over one rewrite: sanity, then containment, then
     * grounding. Returns the first rejection, or [RewriteVerdict.ACCEPT].
     */
    fun check(
        source: String,
        rewrite: String,
        wordText: String,
        matcher: SentenceWordMatcher,
    ): RewriteVerdict {
        sanity(rewrite).takeIf { it.rejected }?.let { return it }
        containment(wordText, rewrite, matcher).takeIf { it.rejected }?.let { return it }
        return grounding(source, rewrite, wordText, matcher)
    }

    /**
     * Sanity: on the trimmed rewrite, a plausible sentence length, no prompt
     * artifacts (placeholder markers, braces, code fences, prompt labels, line
     * breaks), Latin script only (umlauts and ß included, so a Cyrillic or CJK
     * hallucination is caught), and final sentence punctuation.
     */
    fun sanity(rewrite: String): RewriteVerdict {
        val trimmed = rewrite.trim()
        if (trimmed.length < MIN_CHARS || trimmed.length > MAX_CHARS) {
            return RewriteVerdict.reject(
                SANITY_LENGTH,
                "the rewrite must be $MIN_CHARS to $MAX_CHARS characters long, not ${trimmed.length}",
            )
        }
        val artifact = ARTIFACTS.firstOrNull { trimmed.contains(it, ignoreCase = true) }
        if (artifact != null || trimmed.contains('\n') || trimmed.contains('\r')) {
            return RewriteVerdict.reject(
                SANITY_ARTIFACT,
                "the rewrite contains a prompt artifact" + (artifact?.let { " ($it)" } ?: " (a line break)"),
            )
        }
        if (!allLettersLatin(trimmed)) {
            return RewriteVerdict.reject(
                SANITY_SCRIPT,
                "the rewrite must use only Latin letters (umlauts and ß are fine)",
            )
        }
        if (!FINAL_PUNCTUATION_REGEX.containsMatchIn(trimmed)) {
            return RewriteVerdict.reject(
                SANITY_PUNCTUATION,
                "the rewrite must end with ., !, ? or …",
            )
        }
        return RewriteVerdict.ACCEPT
    }

    /**
     * Containment: the rewrite must still contain the saved word — an exact form,
     * a lemma match via the offline dictionary, or a multi-word phrase hit
     * ([SentenceWordMatcher.matches] covers all three and strips a leading
     * der/die/das/ein/eine). Because the dictionary may be switched off, a
     * fallback accepts a rewrite token that starts with the target's stem
     * ("insistieren" → "insistier" accepts "insistiert") — but only for a
     * single-word target and a stem of at least 4 characters, so short or
     * phrase targets still need a real match.
     */
    fun containment(wordText: String, rewrite: String, matcher: SentenceWordMatcher): RewriteVerdict {
        if (matcher.matches(wordText, rewrite)) return RewriteVerdict.ACCEPT
        val stem = singleWordStem(wordText)
        if (stem != null && SentenceWordMatch.tokens(rewrite).any { it.startsWith(stem, ignoreCase = true) }) {
            return RewriteVerdict.ACCEPT
        }
        return RewriteVerdict.reject(
            CONTAINMENT,
            "the rewrite no longer contains the saved word \"$wordText\" or an inflection of it",
        )
    }

    /**
     * Grounding: every non-target rewrite token must mostly come from the source
     * sentence (matched by lemma key or lowercase form), so the model cannot invent
     * content. Target forms are removed first — the rewrite is supposed to contain
     * the word, and its inflected or stem-shared forms are not "new" words. The
     * rewrite fails when it shares no words with the source, when too small a share
     * of its words is grounded, or when it introduces too many distinct new words.
     */
    fun grounding(
        source: String,
        rewrite: String,
        wordText: String,
        matcher: SentenceWordMatcher,
    ): RewriteVerdict {
        val phrase = stripLeadingArticle(wordText)
        val targetKey = matcher.keyOf(phrase)
        val phraseTokens = SentenceWordMatch.tokens(phrase)
        val stem = singleWordStem(wordText)
        fun isTargetForm(token: String): Boolean =
            matcher.keyOf(token) == targetKey ||
                phraseTokens.any { token.equals(it, ignoreCase = true) } ||
                (stem != null && token.startsWith(stem, ignoreCase = true))

        val sourceKeys = SentenceWordMatch.tokens(source).map { matcher.keyOf(it) }.toSet()
        val sourceLower = SentenceWordMatch.tokens(source).map { it.lowercase(Locale.GERMAN) }.toSet()

        val remaining = SentenceWordMatch.tokens(rewrite).filterNot(::isTargetForm)
        if (remaining.isEmpty()) {
            return RewriteVerdict.reject(
                GROUNDING,
                "the rewrite shares no words with the original sentence",
            )
        }
        val grounded = remaining.count { matcher.keyOf(it) in sourceKeys || it.lowercase(Locale.GERMAN) in sourceLower }
        val share = grounded.toDouble() / remaining.size
        if (share < MIN_GROUNDED_SHARE) {
            return RewriteVerdict.reject(
                GROUNDING,
                "only ${(share * 100).toInt()}% of the rewrite's words come from the original sentence" +
                    " (at least ${(MIN_GROUNDED_SHARE * 100).toInt()}% required)",
            )
        }
        val newTokens = remaining.filterNot { matcher.keyOf(it) in sourceKeys || it.lowercase(Locale.GERMAN) in sourceLower }
            .map { it.lowercase(Locale.GERMAN) }
            .distinct()
        if (newTokens.size > MAX_NEW_TOKENS) {
            return RewriteVerdict.reject(
                GROUNDING,
                "the rewrite introduces ${newTokens.size} new words ($newTokens)" +
                    " that are not in the original sentence (at most $MAX_NEW_TOKENS allowed)",
            )
        }
        return RewriteVerdict.ACCEPT
    }

    /**
     * The inflection-stem fallback form of a single-word target, or null when the
     * target is a phrase or its stem is too short to be distinctive: the target
     * lowercased with one trailing "en", "n" or "e" removed, at least 4 characters.
     */
    private fun singleWordStem(wordText: String): String? {
        val word = stripLeadingArticle(wordText)
        if (word.any { it.isWhitespace() }) return null
        val lower = word.lowercase(Locale.GERMAN)
        val stem = when {
            lower.endsWith("en") -> lower.removeSuffix("en")
            lower.endsWith("n") -> lower.removeSuffix("n")
            lower.endsWith("e") -> lower.removeSuffix("e")
            else -> lower
        }
        return if (stem.length >= 4) stem else null
    }

    private fun stripLeadingArticle(wordText: String): String {
        val trimmed = wordText.trim()
        val stripped = LEADING_ARTICLE_REGEX.replaceFirst(trimmed, "").trim()
        return if (stripped.isNotEmpty()) stripped else trimmed
    }

    /** True when every letter in [text] is Latin script (umlauts and ß included). */
    private fun allLettersLatin(text: String): Boolean {
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (Character.isLetter(codePoint) &&
                Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN
            ) {
                return false
            }
            index += Character.charCount(codePoint)
        }
        return true
    }
}

/**
 * Outcome of one rewrite's guard pass: rejected with a filter id, or accepted.
 * Mirrors [PracticeFilters.Verdict].
 */
data class RewriteVerdict(val filter: String?, val reason: String?) {
    val rejected: Boolean get() = filter != null
    val accepted: Boolean get() = filter == null

    companion object {
        val ACCEPT = RewriteVerdict(filter = null, reason = null)
        fun reject(filter: String, reason: String) = RewriteVerdict(filter, reason)
    }
}
