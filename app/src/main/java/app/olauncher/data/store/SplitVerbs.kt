package app.olauncher.data.store

import java.util.Locale

/**
 * Detects a German separable verb split across two tokens ("nehme … auf" for "aufnehmen")
 * inside one accessibility node (#523). Context-free and JVM-testable: the caller injects
 * the lemma lookup, so the tests never touch the bundled database.
 *
 * The verbal bracket puts the separated particle at the end of the main clause, so the
 * detector only ever pairs a clause-final particle with an earlier verb of the same clause.
 * "Ich warte auf dich" has a preposition that is not clause-final and yields no chip.
 */
object SplitVerbs {

    /** One detected split: the verb and particle words, and their joined lemma. */
    data class Match(val verb: ScreenWord, val particle: ScreenWord, val lemma: String)

    /** Nearest verb candidates considered when the tapped word is the clause-final particle. */
    private const val MAX_VERB_CANDIDATES = 12

    /** A word whose raw text ends with one of these closes a clause. */
    private val CLAUSE_CLOSERS = charArrayOf('.', ',', ';', ':', '!', '?', '–')

    /**
     * Candidate "<verb> <particle>" pairs for [tapped] within its clause, nearest verb first.
     * A clause-final [tapped] is the particle and pairs with the earlier words of its clause;
     * any other [tapped] is the verb and pairs with the clause's last word. Joins never cross
     * nodes or clauses, so an empty list means there is nothing to join.
     */
    internal fun pairs(nodeWords: List<ScreenWord>, tapped: ScreenWord): List<Pair<ScreenWord, ScreenWord>> {
        val clause = clausesOf(nodeWords).firstOrNull { c -> c.any { it == tapped } } ?: return emptyList()
        val last = clause.last()
        return if (last == tapped) {
            clause.dropLast(1).asReversed()
                .take(MAX_VERB_CANDIDATES)
                .map { it to tapped }
        } else {
            listOf(tapped to last)
        }
    }

    /**
     * First pair whose lowercased split form ("nehme auf") maps to a verb lemma. Several
     * lemmas prefer the no-space spelling ("bestehenbleiben" over "bestehen bleiben"); if
     * more than one remains, returns null — never guess.
     */
    fun find(nodeWords: List<ScreenWord>, tapped: ScreenWord, verbLemmas: (String) -> List<String>): Match? {
        for ((verb, particle) in pairs(nodeWords, tapped)) {
            val lemmas = verbLemmas(splitForm(verb, particle))
            if (lemmas.isEmpty()) continue
            return resolveLemma(lemmas)?.let { Match(verb, particle, it) }
        }
        return null
    }

    /** The clause-cut node words: a closing punctuation ends a clause, and so does the node end. */
    private fun clausesOf(words: List<ScreenWord>): List<List<ScreenWord>> {
        val clauses = ArrayList<List<ScreenWord>>()
        var current = ArrayList<ScreenWord>()
        for (word in words) {
            current += word
            val lastChar = word.text.lastOrNull()
            if (lastChar != null && lastChar in CLAUSE_CLOSERS) {
                clauses += current
                current = ArrayList()
            }
        }
        if (current.isNotEmpty()) clauses += current
        return clauses
    }

    /** The lookup query: "<verb> <particle>", both tokens stripped and lowercased (the DB is case-sensitive). */
    private fun splitForm(verb: ScreenWord, particle: ScreenWord): String =
        "${SavedMarks.tokenOf(verb)} ${SavedMarks.tokenOf(particle)}".lowercase(Locale.GERMAN)

    /** The single lemma, preferring the no-space spelling; null when several remain (never guess). */
    private fun resolveLemma(lemmas: List<String>): String? {
        val distinct = lemmas.distinct()
        if (distinct.size <= 1) return distinct.firstOrNull()
        return distinct.filterNot { it.contains(' ') }.singleOrNull()
    }
}
