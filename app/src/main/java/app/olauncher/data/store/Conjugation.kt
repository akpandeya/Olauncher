package app.olauncher.data.store

enum class Tense {
    PRESENT,
    PRETERITE,
    SUBJUNCTIVE_II,
}

/** One paradigm cell: [person] is 1..3, [plural] the number, [form] the surface form. */
data class ParadigmCell(val person: Int, val plural: Boolean, val form: String)

typealias PresentForm = ParadigmCell

/** A verb lemma plus the cells the offline dictionary holds for it. */
data class VerbParadigm(
    val lemma: String,
    val tense: Tense,
    val cells: List<ParadigmCell>,
) {
    constructor(lemma: String, cells: List<ParadigmCell>) : this(lemma, Tense.PRESENT, cells)

    val present: List<ParadigmCell> get() = cells
}

/** One drill card: the [pronoun] prompt and the [form] to recall for it. */
data class DrillPrompt(val pronoun: String, val form: String)

/**
 * Turns the offline dictionary's `forms` rows for one verb into drillable
 * paradigms per tense. Rows are (form, tags) pairs with tags space-joined
 * ("first-person indicative present singular"); rows of other moods and tenses,
 * and rows without a person/number cell, are skipped. Android-free so the JVM
 * tests drive it directly.
 */
object Conjugation {

    private val NON_PRESENT_TAGS = setOf(
        "subjunctive", "subjunctive-i", "subjunctive-ii",
        "preterite", "past", "participle", "imperative", "infinitive",
    )
    private val NON_PRETERITE_TAGS = setOf(
        "present", "subjunctive", "subjunctive-i", "subjunctive-ii",
        "participle", "imperative", "infinitive",
    )
    private val NON_SUBJUNCTIVE_II_TAGS = setOf(
        "present", "preterite", "past", "subjunctive-i",
        "participle", "imperative", "infinitive",
    )

    private val PERSON_TAGS = mapOf("first-person" to 1, "second-person" to 2, "third-person" to 3)
    private const val TAG_PRESENT = "present"
    private const val TAG_PRETERITE = "preterite"
    private const val TAG_SUBJUNCTIVE_II = "subjunctive-ii"
    private const val TAG_SINGULAR = "singular"
    private const val TAG_PLURAL = "plural"

    /** A multiword form whose extra tokens are only these pronouns is the reflexive base, not a split. */
    private val REFLEXIVE_PRONOUNS = setOf("mich", "dich", "sich", "uns", "euch")

    /** Orders the drill: 1sg → 2sg → 3sg → 1pl → 2pl → 3pl (number first, then person). */
    private val CELL_ORDER = compareBy<ParadigmCell>({ it.plural }, { it.person })

    fun parse(lemma: String, rows: List<Pair<String, String>>, tense: Tense): VerbParadigm? {
        val (requiredTag, excludedTags) = when (tense) {
            Tense.PRESENT -> TAG_PRESENT to NON_PRESENT_TAGS
            Tense.PRETERITE -> TAG_PRETERITE to NON_PRETERITE_TAGS
            Tense.SUBJUNCTIVE_II -> TAG_SUBJUNCTIVE_II to NON_SUBJUNCTIVE_II_TAGS
        }
        val cellForms = mutableMapOf<Pair<Int, Boolean>, MutableList<String>>()
        for ((form, tagLine) in rows) {
            val tags = tagLine.split(' ').filter { it.isNotEmpty() }.toSet()
            if (requiredTag !in tags || tags.any { it in excludedTags }) continue
            val person = PERSON_TAGS.entries.firstOrNull { it.key in tags }?.value ?: continue
            val plural = when {
                TAG_SINGULAR in tags -> false
                TAG_PLURAL in tags -> true
                else -> continue
            }
            cellForms.getOrPut(person to plural) { mutableListOf() }.add(form)
        }
        if (cellForms.isEmpty()) return null
        return VerbParadigm(
            lemma,
            tense,
            cellForms.map { (cell, forms) ->
                ParadigmCell(cell.first, cell.second, resolveCell(forms))
            }.sortedWith(CELL_ORDER),
        )
    }

    /** Null when no row lands in a present cell, so the caller can show its graceful state. */
    fun parsePresent(lemma: String, rows: List<Pair<String, String>>): VerbParadigm? =
        parse(lemma, rows, Tense.PRESENT)

    fun parsePreterite(lemma: String, rows: List<Pair<String, String>>): VerbParadigm? =
        parse(lemma, rows, Tense.PRETERITE)

    fun parseSubjunctiveIi(lemma: String, rows: List<Pair<String, String>>): VerbParadigm? =
        parse(lemma, rows, Tense.SUBJUNCTIVE_II)

    fun parseAll(lemma: String, rows: List<Pair<String, String>>): Map<Tense, VerbParadigm> =
        Tense.values().mapNotNull { tense -> parse(lemma, rows, tense)?.let { tense to it } }.toMap()

    /**
     * A cell can hold competing surface forms: a separable split ("setze über")
     * beside the joined subordinate-clause shape, or a reflexive "freue mich"
     * beside "freue". The drill teaches the main-clause form, so separable
     * splits win over single-word forms; a multiword form that only adds
     * reflexive pronouns stays deprioritized behind its single-word base.
     * Ties break lexicographically so the choice never depends on row order.
     */
    private fun resolveCell(forms: List<String>): String {
        val splits = forms.filter { form ->
            val tokens = form.split(' ')
            tokens.size > 1 && tokens.drop(1).any { it !in REFLEXIVE_PRONOUNS }
        }
        val singleWord = forms.filterNot { form -> form.any { it.isWhitespace() } }
        return (splits.ifEmpty { singleWord }.ifEmpty { forms }).min()
    }

    fun pronoun(person: Int, plural: Boolean): String = when (person to plural) {
        1 to false -> "ich"
        2 to false -> "du"
        3 to false -> "er/sie/es"
        1 to true -> "wir"
        2 to true -> "ihr"
        3 to true -> "sie/Sie"
        else -> error("person must be 1..3, was $person")
    }

    /** Defective verbs keep only the cells they have; the rest are skipped, not errored. */
    fun drillPrompts(cells: List<ParadigmCell>): List<DrillPrompt> =
        cells.sortedWith(CELL_ORDER).map { DrillPrompt(pronoun(it.person, it.plural), it.form) }

    fun drillPrompts(paradigm: VerbParadigm): List<DrillPrompt> = drillPrompts(paradigm.cells)
}
