package app.olauncher.data.store

/**
 * One pending/saveable pick in the capture surfaces (#523). The circle-to-search overlay
 * and the full-screen reader share one pending set of picks, so a selection made in one
 * surface survives the switch to the other.
 *
 * A pick is identified by [words]; a single tapped or circled word is a [Word]. A
 * multi-word pick (split verbs) arrives in a later step — the [words] accessor already
 * models the multi-word shape so the sealed API stays stable.
 */
sealed class CapturePick {

    /** Screen words to mark for this pick, in reading order. */
    val words: List<ScreenWord> get() = when (this) {
        is Word -> listOf(word)
    }

    /** One pending/saveable pick in the capture surfaces (#523). */
    data class Word(val word: ScreenWord) : CapturePick()
}
