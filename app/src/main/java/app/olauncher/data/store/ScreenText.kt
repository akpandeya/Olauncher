package app.olauncher.data.store

/** Turns the paragraphs read from the screen into word choices for the picker. */
object ScreenText {
    const val MAX_CONTEXT_CHARS = 400
    const val MAX_CHOICES = 300

    /** Mirrors the punctuation stripped by [WordCapture] so picker tokens match saved words. */
    private const val PUNCTUATION = ".,;:!?\"'„“”‚‘()[]«»"
    private val PUNCTUATION_SET = PUNCTUATION.toSet()

    data class Choice(val text: String, val context: String)

    /** Each entry is one node's full text, treated as a single paragraph. */
    fun choices(paragraphs: List<String>): List<Choice> {
        val seen = HashSet<String>()
        val result = ArrayList<Choice>()
        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) continue
            var i = 0
            while (i < paragraph.length && result.size < MAX_CHOICES) {
                while (i < paragraph.length && paragraph[i].isWhitespace()) i++
                val wordStart = i
                while (i < paragraph.length && !paragraph[i].isWhitespace()) i++
                val word = paragraph.substring(wordStart, i)
                // Strip the same punctuation WordCapture strips so tokens stay identical.
                val token = word.filter { it !in PUNCTUATION_SET }
                val first = word.indexOfFirst { it !in PUNCTUATION_SET }
                if (first < 0) continue
                val last = word.indexOfLast { it !in PUNCTUATION_SET }
                if (token.length < 2) continue
                if (token.none { it.isLetter() }) continue
                if (!seen.add(token.lowercase())) continue
                val context = ContextSentence.around(paragraph, wordStart + first, wordStart + last + 1)
                result.add(Choice(token, context))
            }
        }
        return result
    }
}
