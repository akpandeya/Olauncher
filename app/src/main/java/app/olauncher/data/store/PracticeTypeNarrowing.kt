package app.olauncher.data.store

/**
 * The coarse word-class type filter (epic 9 round 3, Opus's recommendation): the
 * caller sends a per-word narrowed [DrillType] list instead of all 22 ids — a noun
 * does not need `konjunktiv_i`, and a short list helps a small model more than any
 * prompt rule.
 *
 * The mapping is deliberately simple and deterministic: capitalized text → noun,
 * an infinitive-like ending → verb, anything else (phrases, adjectives, unknown)
 * returns an empty list and the caller sends the full list as before. Cloze is
 * never in the narrowed lists: the contract always asks for one cloze per word.
 */
object PracticeTypeNarrowing {

    /** Types that fit a noun: articles, plurals and the case/ending drills. */
    val NOUN_TYPES = listOf(
        DrillType.ARTICLES,
        DrillType.PLURALS,
        DrillType.CASE_ENDINGS,
        DrillType.ADJECTIVE_ENDINGS,
        DrillType.GENITIVE,
        DrillType.N_DECLENSION,
        DrillType.PREPOSITIONS,
        DrillType.VERB_PREPOSITIONS,
        DrillType.WORD_ORDER,
    )

    /** Types that fit a verb: its forms, its verb-frame and its particles. */
    val VERB_TYPES = listOf(
        DrillType.CONJUGATION,
        DrillType.SEPARABLE_VERBS,
        DrillType.MODAL_VERBS,
        DrillType.PRAETERITUM,
        DrillType.WERDEN,
        DrillType.PASSIVE,
        DrillType.IMPERATIVE,
        DrillType.INFINITIV_ZU,
        DrillType.KONJUNKTIV_II,
        DrillType.KONJUNKTIV_I,
        DrillType.VERB_PREPOSITIONS,
        DrillType.WORD_ORDER,
    )

    /**
     * The narrowed list for [word], or an empty list when the word class is unknown
     * (the caller then sends the full list as today). Never contains [DrillType.CLOZE].
     */
    fun narrow(word: Word): List<DrillType> {
        val text = word.text.trim()
        if (text.isEmpty() || text.contains(' ')) return emptyList()
        return when {
            text[0].isUpperCase() -> NOUN_TYPES
            text.length >= MIN_VERB_LEN && text.lowercase().let { it.endsWith("en") || it.endsWith("ern") || it.endsWith("eln") } -> VERB_TYPES
            else -> emptyList()
        }
    }

    /** Shorter lowercase words ("hin", "los") are not reliably verbs. */
    private const val MIN_VERB_LEN = 5
}
