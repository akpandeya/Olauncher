package app.olauncher.data.store

/**
 * Safety checks for drill item hints.
 *
 * A hint may only be shown after a wrong answer, and it must never contain the
 * answer itself. The check is word-based: a hint reveals the answer when every
 * word of the answer appears as a word in the hint (case-insensitive, with
 * punctuation ignored), so "Maskulinum Akkusativ: meinen" reveals "meinen"
 * while "to arrive" does not reveal the prefix "an".
 */
object DrillHints {

    /**
     * True when [hint] contains every word of [answer] as a whole word
     * (case-insensitive, punctuation ignored).
     */
    fun revealsAnswer(hint: String, answer: String): Boolean {
        val answerWords = words(answer)
        if (answerWords.isEmpty()) return false
        val hintWords = words(hint).toSet()
        return answerWords.all { it in hintWords }
    }

    /**
     * Lowercased word tokens of [text]. Tokens are split on everything that is
     * not a letter or digit, so compact cues such as "-ung→die" or "Wo?→Dativ"
     * are still checked word by word instead of collapsing into one blob.
     */
    fun words(text: String): List<String> =
        text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
}
