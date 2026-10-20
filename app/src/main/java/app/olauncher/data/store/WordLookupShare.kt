package app.olauncher.data.store

/** Parses shared text for the "look up word" share target. Context-free. */
object WordLookupShare {
    const val MAX_WORDS = 3
    private const val MAX_CHARS = 40

    /** The word or short phrase (up to [MAX_WORDS] words) to look up, or null if the text is not one. */
    fun parse(raw: String): String? {
        if (WordCapture.classify(raw) == Capture.Invalid) return null
        val tokens = WordCapture.tokenize(raw)
        if (tokens.isEmpty() || tokens.size > MAX_WORDS) return null
        val phrase = tokens.joinToString(" ")
        return phrase.takeIf { it.length <= MAX_CHARS }
    }
}
