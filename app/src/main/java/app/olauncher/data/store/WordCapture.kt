package app.olauncher.data.store

import android.content.Context

sealed interface Capture {
    data class Word(val text: String) : Capture
    data class Sentence(val text: String, val tokens: List<String>) : Capture
    data object Invalid : Capture
}

object WordLookup {
    @Suppress("UNUSED_PARAMETER")
    fun enqueue(context: Context, wordId: Long) {
        // No-op stub until task-03 lands
    }
}

object WordCapture {
    private const val MAX_WORD_CHARS = 40
    private const val MAX_SENTENCE_CHARS = 400
    private const val PUNCTUATION = ".,;:!?\"'„“”‚‘()[]«»"
    private val PUNCTUATION_SET = PUNCTUATION.toSet()
    private val URL_REGEX = Regex("""(?i)\b(https?://|ftp://|www\.)\S+""")

    fun classify(raw: String): Capture {
        if (raw.isBlank()) return Capture.Invalid
        if (URL_REGEX.containsMatchIn(raw)) return Capture.Invalid

        val trimmed = raw.trim()
        if (trimmed.length > MAX_SENTENCE_CHARS) return Capture.Invalid

        // Check Word candidate: 1-4 words after trimming whitespace and surrounding punctuation, <= 40 chars, no URL
        val wordCandidate = trimmed.trim { it.isWhitespace() || it in PUNCTUATION_SET }
        if (wordCandidate.isNotEmpty() && wordCandidate.length <= MAX_WORD_CHARS) {
            val words = wordCandidate.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (words.size in 1..4) {
                return Capture.Word(wordCandidate)
            }
        }

        // Check Sentence candidate: longer selections up to 400 chars, tokens = the words without punctuation
        val tokens = trimmed.split(Regex("""\s+"""))
            .map { it.filter { c -> c !in PUNCTUATION_SET }.trim() }
            .filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }

        if (tokens.size >= 2) {
            return Capture.Sentence(text = trimmed, tokens = tokens)
        }

        return Capture.Invalid
    }
}
