package app.olauncher.data.store

import java.util.Locale

/** Where a word's context sentence came from. */
object ContextSource {
    /** Straight from the source (screen capture, reader or news); the default for every word. */
    const val CAPTURED = "captured"

    /** Typed or edited by the user; never overwritten by a rewrite. */
    const val USER = "user"

    /** Written by the AI rewrite flow. */
    const val AUTO = "auto"
}

/**
 * Decides whether a word's context sentence is too poor to keep, so the AI rewrite may
 * replace it. Deliberately cheap and context-free: circle-to-search captures are often
 * bare screen fragments ("Startseite Politik Wirtschaft Sport") or unrelated English UI
 * text, and those must be told apart from a real German sentence without any AI call.
 */
object ContextQuality {

    /** A context with fewer word tokens than this is a fragment, not a sentence. */
    const val MIN_TOKENS = 4

    /** A context where fewer than this share of chunks look German counts as non-German. */
    const val MIN_GERMAN_SHARE = 0.5

    /** Common German finite verbs and auxiliaries; any one of them proves a real sentence. */
    private val FINITE_VERBS = setOf(
        "ist", "sind", "war", "waren", "hat", "haben", "hatte", "hatten",
        "wird", "werden", "wurde", "wurden", "kann", "können", "muss", "müssen",
        "soll", "sollen", "gibt"
    )

    /**
     * Unambiguously English words: a chunk matching one of these (ignoring case) counts
     * against the German share, so English UI text fails rule 4 even though it is letters.
     */
    private val ENGLISH_STOPWORDS = setOf(
        "the", "and", "of", "to", "for", "with", "is", "are", "be", "this", "that",
        "it", "you", "we", "they", "he", "she", "at", "by", "from", "not", "but",
        "have", "has", "which", "would", "your", "our", "their", "there", "been"
    )

    /**
     * True when [context] is too weak to keep as the word's example sentence. A context
     * is weak when any of these holds:
     *
     * 1. The trimmed context is blank — there is nothing to judge or keep.
     * 2. It has fewer than [MIN_TOKENS] word tokens ([SentenceWordMatch.tokens]) — a
     *    captured fragment like "die neue Regierung" is not a sentence to learn from.
     * 3. It has no sentence punctuation (`.` `!` `?` `…`) **and** no verb-like token.
     *    Screen navigation text is a row of capitalized nouns with no period and no verb;
     *    a real sentence almost always carries one of the two. A token is verb-like when
     *    it is one of the common German finite verbs/auxiliaries, or when it is not the
     *    first token, starts lowercase, has at least 4 letters, ends in `en`, `t`, `te`
     *    or `st`, and is not a token of [wordText] itself (the word or its inflected
     *    forms must not be mistaken for the sentence's verb).
     * 4. Fewer than [MIN_GERMAN_SHARE] of its whitespace-separated chunks (surrounding
     *    punctuation stripped, empty chunks dropped) look German. A chunk looks German
     *    when it consists only of letters and hyphens and is not, ignoring case, in the
     *    English stopword set. Digits, times, URLs, currency symbols and English words
     *    therefore count against the context — a "12:30 Uhr – 3 € – #news" ticker line
     *    is not a usable sentence even though it mentions the word.
     */
    fun isWeak(context: String, wordText: String): Boolean {
        val trimmed = context.trim()
        if (trimmed.isEmpty()) return true // Rule 1
        val tokens = SentenceWordMatch.tokens(trimmed)
        if (tokens.size < MIN_TOKENS) return true // Rule 2
        val hasSentencePunctuation = trimmed.any { it == '.' || it == '!' || it == '?' || it == '…' }
        if (!hasSentencePunctuation && !hasVerbLikeToken(tokens, wordText)) return true // Rule 3
        if (germanShare(trimmed) < MIN_GERMAN_SHARE) return true // Rule 4
        return false
    }

    /**
     * The one eligibility rule shared by the rewrite planner and [WordsStore]: a rewrite
     * may fill a blank context, or replace a weak one that the user did not type. The
     * user's own context ([ContextSource.USER]) is never replaceable, captured contexts
     * (including every legacy word saved before the source existed) and AI-written ones are.
     */
    fun isReplaceable(word: Word): Boolean =
        word.context.isBlank() ||
            (word.contextSource != ContextSource.USER && isWeak(word.context, word.text))

    /** Any known finite verb, or a token whose shape looks like a conjugated German verb. */
    private fun hasVerbLikeToken(tokens: List<String>, wordText: String): Boolean {
        val wordTokens = SentenceWordMatch.tokens(wordText)
            .mapTo(HashSet()) { it.lowercase(Locale.GERMAN) }
        return tokens.withIndex().any { (index, token) ->
            token.lowercase(Locale.GERMAN) in FINITE_VERBS ||
                looksLikeFiniteVerb(token, index == 0, wordTokens)
        }
    }

    /** Not the first token, lowercase, at least 4 letters, a finite-verb ending, not the word itself. */
    private fun looksLikeFiniteVerb(token: String, isFirst: Boolean, wordTokens: Set<String>): Boolean {
        if (isFirst || token.isEmpty() || !token[0].isLowerCase()) return false
        if (token.length < 4) return false
        val verbEnding = token.endsWith("en") || token.endsWith("t") ||
            token.endsWith("te") || token.endsWith("st")
        if (!verbEnding) return false
        return token.lowercase(Locale.GERMAN) !in wordTokens
    }

    /** Share of whitespace-separated chunks (punctuation-stripped, empties dropped) that look German. */
    private fun germanShare(context: String): Double {
        val chunks = context.split(Regex("\\s+"))
            .map { it.stripSurroundingPunctuation() }
            .filter { it.isNotEmpty() }
        if (chunks.isEmpty()) return 0.0
        val german = chunks.count { looksGerman(it) }
        return german.toDouble() / chunks.size
    }

    /** Letters and hyphens only, and not one of the unambiguous English stopwords. */
    private fun looksGerman(chunk: String): Boolean {
        if (!chunk.all { it.isLetter() || it == '-' }) return false
        return chunk.lowercase(Locale.GERMAN) !in ENGLISH_STOPWORDS
    }

    /** Drops leading and trailing characters that are not letters or digits ("year," → "year"). */
    private fun String.stripSurroundingPunctuation(): String {
        var start = 0
        var end = length
        while (start < end && !this[start].isLetterOrDigit()) start++
        while (end > start && !this[end - 1].isLetterOrDigit()) end--
        return substring(start, end)
    }
}
