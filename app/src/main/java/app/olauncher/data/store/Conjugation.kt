package app.olauncher.data.store

/** One present-tense paradigm cell: [person] is 1..3, [plural] the number, [form] the surface form. */
data class PresentForm(val person: Int, val plural: Boolean, val form: String)

/** A verb lemma plus the present-tense cells the offline dictionary holds for it. */
data class VerbParadigm(val lemma: String, val present: List<PresentForm>)

/** One drill card: the [pronoun] prompt and the [form] to recall for it. */
data class DrillPrompt(val pronoun: String, val form: String)

/**
 * Turns the offline dictionary's `forms` rows for one verb into a drillable
 * present-tense paradigm. Rows are (form, tags) pairs with tags space-joined
 * ("first-person indicative present singular"); rows of other moods and tenses,
 * and rows without a person/number cell, are skipped. Android-free so the JVM
 * tests drive it directly.
 */
object Conjugation {

    private val NON_PRESENT_TAGS = setOf(
        "subjunctive", "subjunctive-i", "subjunctive-ii",
        "preterite", "past", "participle", "imperative", "infinitive",
    )
    private val PERSON_TAGS = mapOf("first-person" to 1, "second-person" to 2, "third-person" to 3)
    private const val TAG_PRESENT = "present"
    private const val TAG_SINGULAR = "singular"
    private const val TAG_PLURAL = "plural"

    /** Orders the drill: 1sg → 2sg → 3sg → 1pl → 2pl → 3pl (number first, then person). */
    private val CELL_ORDER = compareBy<PresentForm>({ it.plural }, { it.person })

    /** Null when no row lands in a present cell, so the caller can show its graceful state. */
    fun parsePresent(lemma: String, rows: List<Pair<String, String>>): VerbParadigm? {
        val cellForms = mutableMapOf<Pair<Int, Boolean>, MutableList<String>>()
        for ((form, tagLine) in rows) {
            val tags = tagLine.split(' ').filter { it.isNotEmpty() }.toSet()
            if (TAG_PRESENT !in tags || tags.any { it in NON_PRESENT_TAGS }) continue
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
            cellForms.map { (cell, forms) ->
                PresentForm(cell.first, cell.second, resolveCell(forms))
            }.sortedWith(CELL_ORDER),
        )
    }

    /**
     * A cell can hold both a single-word form and a multiword one (a reflexive
     * "freue mich" beside "freue", or a separable "setze … um" split): the drill
     * prompts one word, so the single-word form wins. Ties break lexicographically
     * so the choice never depends on row order.
     */
    private fun resolveCell(forms: List<String>): String {
        val singleWord = forms.filterNot { form -> form.any { it.isWhitespace() } }
        return (singleWord.ifEmpty { forms }).min()
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
    fun drillPrompts(paradigm: VerbParadigm): List<DrillPrompt> =
        paradigm.present.sortedWith(CELL_ORDER).map { DrillPrompt(pronoun(it.person, it.plural), it.form) }
}
