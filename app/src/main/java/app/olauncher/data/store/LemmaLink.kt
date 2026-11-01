package app.olauncher.data.store

/**
 * Wiktionary glosses for inflected forms ("inflection of vernageln: third-person singular
 * present ...") carry no meaning of their own. This picks the base word out of such a gloss
 * so the lookup can follow that one link. Context-free so it is JVM-testable.
 */
object LemmaLink {

    data class Resolution(val baseForm: String, val meaning: String)

    private val INFLECTION_OF_REGEX = Regex(
        """(?i)^(?:\S+\s+[—–]\s+)?(?:[\w-]+(?:/[\w-]+)*\s+){0,4}""" +
            """(?:inflection|form|plural|participle|singular|preterite|subjunctive|imperative|""" +
            """comparative|superlative|genitive|dative|accusative|nominative)\s+of\s+(\S+)"""
    )
    private const val TRIM_CHARS = ":;,.()[]\"'“”„‚‘’"

    /** Every distinct base word the gloss points at, one per "; "-separated definition. */
    fun baseWords(gloss: String): List<String> {
        if (gloss.isBlank()) return emptyList()
        return gloss.split("; ").mapNotNull { segment ->
            val raw = INFLECTION_OF_REGEX.find(segment.trim())?.groupValues?.get(1) ?: return@mapNotNull null
            raw.trim { it in TRIM_CHARS }.takeIf { it.isNotEmpty() }
        }.distinct()
    }

    /** The base word of an inflection gloss, or null when it is not one or names several (never guess). */
    fun baseWord(gloss: String): String? = baseWords(gloss).singleOrNull()

    /**
     * Follows at most one link: [lookupBase] returns the base word's gloss or null. A base that is
     * itself an inflection, or a failed base lookup, returns null so the caller keeps the typed form.
     */
    fun resolve(gloss: String, lookupBase: (String) -> String?): Resolution? {
        val base = baseWord(gloss) ?: return null
        val baseGloss = lookupBase(base)?.takeIf { it.isNotBlank() } ?: return null
        if (baseWords(baseGloss).isNotEmpty()) return null
        return Resolution(base, baseGloss)
    }
}
