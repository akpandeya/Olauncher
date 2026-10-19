package app.olauncher.helper

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

data class LetterPos(
    val letter: Char,
    val index: Int,
) {
    val pos: Int get() = index
    val position: Int get() = index
    val text: String get() = letter.toString()

    constructor(letterStr: String, index: Int) : this(letterStr.firstOrNull() ?: '#', index)
}

object LetterIndex {

    fun build(labels: List<String>, collator: Collator = Collator.getInstance()): List<LetterPos> {
        val firstIndices = mutableMapOf<Char, Int>()
        for ((index, label) in labels.withIndex()) {
            val letter = letterFor(label)
            if (!firstIndices.containsKey(letter)) {
                firstIndices[letter] = index
            }
        }
        val comparator = Comparator<LetterPos> { a, b ->
            if (a.letter == b.letter) 0
            else if (a.letter == '#') -1
            else if (b.letter == '#') 1
            else collator.compare(a.letter.toString(), b.letter.toString())
        }
        return firstIndices.map { (letter, index) ->
            LetterPos(letter, index)
        }.sortedWith(comparator)
    }

    fun letterAt(y: Float, height: Float, count: Int): Int {
        if (count <= 0) return -1
        if (height <= 0f) return 0
        val raw = (y / height * count).toInt()
        return raw.coerceIn(0, count - 1)
    }

    fun letterAt(y: Int, height: Int, count: Int): Int =
        letterAt(y.toFloat(), height.toFloat(), count)

    fun letterAt(y: Float, height: Int, count: Int): Int =
        letterAt(y, height.toFloat(), count)

    fun letterFor(label: String): Char {
        val trimmed = label.trimStart()
        if (trimmed.isEmpty()) return '#'
        val firstChar = trimmed.first()
        val upperStr = firstChar.toString().uppercase(Locale.ROOT)
        val normalized = Normalizer.normalize(upperStr, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        val folded = normalized.firstOrNull() ?: '#'
        val mapped = when (folded) {
            'Æ' -> 'A'
            'Ø' -> 'O'
            'ß' -> 'S'
            else -> folded
        }
        return if (mapped in 'A'..'Z') mapped else '#'
    }
}
