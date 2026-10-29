package app.olauncher.data.store

/**
 * One pending/saveable pick in the capture surfaces (#523). The circle-to-search overlay
 * and the full-screen reader share one pending set of picks, so a selection made in one
 * surface survives the switch to the other.
 *
 * A pick is identified by [words]; a single tapped or circled word is a [Word], a split
 * separable verb ("nehme … auf") is a [SplitVerb] saved under its joined lemma.
 */
sealed class CapturePick {

    /** Screen words to mark for this pick, in reading order. */
    val words: List<ScreenWord> get() = when (this) {
        is Word -> listOf(word)
        is SplitVerb -> listOf(verb, particle)
    }

    /** One pending/saveable pick in the capture surfaces (#523). */
    data class Word(val word: ScreenWord) : CapturePick()

    /** A separable verb split in prose ("nehme … auf"), saved as [lemma] ("aufnehmen"). */
    data class SplitVerb(val verb: ScreenWord, val particle: ScreenWord, val lemma: String) : CapturePick()
}
