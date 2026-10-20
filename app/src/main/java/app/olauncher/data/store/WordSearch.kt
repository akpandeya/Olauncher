package app.olauncher.data.store

import java.text.Normalizer
import java.util.Locale

/**
 * Filters the saved words by what the user types, matching the German word and its meaning.
 *
 * - Case does not matter.
 * - Umlauts match either way they are typed: "ä", "ae" and "a" all find "Mädchen"; "ß" and "ss" are the same.
 *   Other accents are ignored too ("cafe" finds "Café").
 * - Every word of the query must match, in any order ("past gehen" finds "ging · simple past of gehen").
 * - A query word of five or more letters also matches by its stem, so "untersuchen" finds "Untersuchung"
 *   and the other way round.
 * - A blank query keeps every word.
 */
object WordSearch {

    private const val MIN_STEM_SOURCE_LENGTH = 5
    private const val MIN_STEM_LENGTH = 4

    /** Longest first, so "ungen" is cut before "en". */
    private val germanEndings = listOf("ungen", "ung", "ern", "en", "er", "es", "em", "e", "n", "s", "t")

    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    fun filter(words: List<Word>, query: String): List<Word> {
        val terms = queryTerms(query)
        if (terms.isEmpty()) return words
        return words.filter { matches(it, terms) }
    }

    fun matches(word: Word, query: String): Boolean {
        val terms = queryTerms(query)
        return terms.isEmpty() || matches(word, terms)
    }

    private fun matches(word: Word, terms: List<Term>): Boolean {
        val haystack = Folded.of("${word.text} ${word.meaning}")
        return terms.all { it.isIn(haystack) }
    }

    private fun queryTerms(query: String): List<Term> =
        query.trim().split(whitespace).filter { it.isNotEmpty() }.map { Term.of(it) }

    /** The same text folded two ways: umlauts spelled out ("ae") and umlauts dropped to their base letter ("a"). */
    private data class Folded(val spelledOut: String, val baseLetters: String) {
        companion object {
            fun of(text: String): Folded {
                val lower = text.lowercase(Locale.ROOT)
                val spelledOut = stripMarks(
                    lower.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                )
                val baseLetters = stripMarks(lower.replace("ß", "ss"))
                    // A typed "ae" may stand for "ä", so fold it down to "a" on both sides as well.
                    .replace("ae", "a").replace("oe", "o").replace("ue", "u")
                return Folded(spelledOut, baseLetters)
            }

            private fun stripMarks(text: String): String =
                Normalizer.normalize(text, Normalizer.Form.NFD).replace(combiningMarks, "")
        }
    }

    private data class Term(val folded: Folded, val stems: Folded?) {
        fun isIn(haystack: Folded): Boolean =
            contains(haystack, folded) || (stems != null && contains(haystack, stems))

        private fun contains(haystack: Folded, needle: Folded): Boolean =
            haystack.spelledOut.contains(needle.spelledOut) || haystack.baseLetters.contains(needle.baseLetters)

        companion object {
            fun of(raw: String): Term {
                val folded = Folded.of(raw)
                return Term(folded, stemOf(raw)?.let { Folded.of(it) })
            }

            private fun stemOf(raw: String): String? {
                val lower = raw.lowercase(Locale.ROOT)
                if (lower.length < MIN_STEM_SOURCE_LENGTH) return null
                val ending = germanEndings.firstOrNull {
                    lower.endsWith(it) && lower.length - it.length >= MIN_STEM_LENGTH
                } ?: return null
                return lower.dropLast(ending.length)
            }
        }
    }
}
