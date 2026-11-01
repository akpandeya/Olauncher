package app.olauncher.data.store

/**
 * Extracts the sentence around a word span from one paragraph, so a picked word keeps the
 * sentence it appeared in instead of the whole node text. Context-free and JVM-testable.
 */
object ContextSentence {

    private const val SENTENCE_PUNCT = ".!?…"
    private const val QUOTES = "„“”‚‘«»\"'"

    /** Abbreviations whose dot must not end a sentence: z. B., d. h., Nr., Dr., usw. */
    private val ABBREVIATIONS = setOf("z", "b", "d", "h", "nr", "dr", "usw")

    /** Month names that follow a German ordinal date ("am 3. Oktober"): its dot is no boundary. */
    private val MONTHS = setOf(
        "januar", "jänner", "februar", "märz", "april", "mai", "juni", "juli",
        "august", "september", "oktober", "november", "dezember",
    )

    /**
     * Returns the sentence in [paragraph] that contains the span [start, end). Sentences split
     * after `.`, `!`, `?` and `…` when a space and a capital letter or quote follow, so
     * abbreviations (z. B., Dr., …) and numbers like 3.5 stay intact. Longer sentences are
     * capped at [ScreenText.MAX_CONTEXT_CHARS] by trimming around the span, never through it.
     */
    fun around(paragraph: String, start: Int, end: Int): String {
        if (paragraph.isBlank()) return ""
        val s = start.coerceIn(0, paragraph.length)
        val e = end.coerceIn(s, paragraph.length)
        val lo = sentenceStart(paragraph, s).coerceAtMost(s)
        val hi = sentenceEnd(paragraph, e).coerceAtLeast(e)
        if (hi - lo <= ScreenText.MAX_CONTEXT_CHARS) return paragraph.substring(lo, hi).trim()
        return cappedAround(paragraph, lo, hi, s, e)
    }

    /**
     * Splits [paragraph] into sentences using German punctuation and boundary rules. Reuses
     * [boundaryAfter] so captured context and harvested sentences split identically.
     */
    fun split(paragraph: String): List<String> {
        if (paragraph.isBlank()) return emptyList()
        val sentences = mutableListOf<String>()
        var start = 0
        var j = 0
        while (j < paragraph.length) {
            val k = boundaryAfter(paragraph, j)
            if (k != null) {
                val piece = paragraph.substring(start, k).trim()
                if (piece.isNotEmpty()) {
                    sentences.add(piece)
                }
                start = k
                j = k
            } else {
                j++
            }
        }
        val tail = paragraph.substring(start).trim()
        if (tail.isNotEmpty()) {
            sentences.add(tail)
        }
        return sentences
    }

    /** Start index of the sentence containing [s]: first boundary before it, else 0. */
    private fun sentenceStart(p: String, s: Int): Int {
        var j = s - 1
        while (j >= 0) {
            val next = boundaryAfter(p, j)
            if (next != null) return next.coerceAtMost(s)
            j--
        }
        return 0
    }

    /** End index (exclusive) of the sentence containing [from]: first boundary at or after it. */
    private fun sentenceEnd(p: String, from: Int): Int {
        var j = from
        while (j < p.length) {
            boundaryAfter(p, j)?.let { return it }
            j++
        }
        return p.length
    }

    /** If [p][j] ends a sentence, returns the index where the next sentence starts, else null. */
    private fun boundaryAfter(p: String, j: Int): Int? {
        if (p[j] !in SENTENCE_PUNCT) return null
        if (p[j] == '.' && isAbbreviation(p, j)) return null
        var k = j + 1
        // A closing quote after the punctuation (…!“) still belongs to this sentence.
        while (k < p.length && p[k] in QUOTES) k++
        if (k >= p.length) return k
        if (!p[k].isWhitespace()) return null
        while (k < p.length && p[k].isWhitespace()) k++
        if (k >= p.length) return k
        val next = p[k]
        if (p[j] == '.' && isOrdinalDate(p, j, k)) return null
        return if (next.isUpperCase() || next in QUOTES) k else null
    }

    /** True when the dot at [dot] ends a 1–2 digit day and the word at [next] is a month. */
    private fun isOrdinalDate(p: String, dot: Int, next: Int): Boolean {
        var i = dot
        while (i > 0 && p[i - 1].isDigit()) i--
        val digits = dot - i
        if (digits !in 1..2 || (i > 0 && p[i - 1].isLetterOrDigit())) return false
        var end = next
        while (end < p.length && p[end].isLetter()) end++
        return p.substring(next, end).lowercase() in MONTHS
    }

    /** True when the dot at [dot] belongs to a known abbreviation like "Dr." or "z. B.". */
    private fun isAbbreviation(p: String, dot: Int): Boolean {
        var i = dot
        while (i > 0 && p[i - 1].isLetter()) i--
        return i < dot && p.substring(i, dot).lowercase() in ABBREVIATIONS
    }

    /** Trims [hi] - [lo] down to [ScreenText.MAX_CONTEXT_CHARS] while keeping [s, e) whole. */
    private fun cappedAround(p: String, lo: Int, hi: Int, s: Int, e: Int): String {
        val excess = (hi - lo) - ScreenText.MAX_CONTEXT_CHARS
        var cutLeft = minOf(s - lo, excess / 2)
        var cutRight = minOf(hi - e, excess - cutLeft)
        cutLeft += minOf(excess - cutLeft - cutRight, s - lo - cutLeft)
        cutRight += minOf(excess - cutLeft - cutRight, hi - e - cutRight)
        while (cutLeft < s - lo && p[lo + cutLeft].isWhitespace()) cutLeft++
        while (cutRight < hi - e && p[hi - cutRight - 1].isWhitespace()) cutRight++
        return p.substring(lo + cutLeft, hi - cutRight).trim()
    }
}
