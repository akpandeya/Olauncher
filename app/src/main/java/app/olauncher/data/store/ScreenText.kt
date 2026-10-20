package app.olauncher.data.store

/** Turns the text lines read from the screen into word choices for the picker. */
object ScreenText {
    const val MAX_CONTEXT_CHARS = 400
    const val MAX_CHOICES = 300

    data class Choice(val text: String, val context: String)

    fun choices(lines: List<String>): List<Choice> {
        val seen = HashSet<String>()
        val result = ArrayList<Choice>()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val context = line.take(MAX_CONTEXT_CHARS)
            for (token in WordCapture.tokenize(line)) {
                if (token.length < 2) continue
                if (token.none { it.isLetter() }) continue
                if (!seen.add(token.lowercase())) continue
                result.add(Choice(token, context))
                if (result.size >= MAX_CHOICES) return result
            }
        }
        return result
    }
}
