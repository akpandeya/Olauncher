package app.olauncher.data.store

/**
 * Turns the pending picks of a capture surface into the store writes they imply (#523).
 * Context-free so both the overlay and the reader run one save path: the surface owns the
 * store, the look-up queue and the marks, this object only plans the items.
 */
object CaptureSave {

    /** One store write: the stripped token, the sentence context, and the words to mark saved. */
    data class SaveItem(val text: String, val context: String, val words: List<ScreenWord>)

    /**
     * One item per pending pick, in [pending] order. The token is the pick's first word
     * stripped like the store does ("Regierung." → "Regierung"); the context is
     * [ContextSentence.around] over the pick's whole first..last word span of the
     * paragraph [paragraphOf] returns for the first word.
     */
    fun plan(pending: List<CapturePick>, paragraphOf: (ScreenWord) -> String): List<SaveItem> =
        pending.map { pick ->
            val words = pick.words
            val first = words.first()
            val last = words.last()
            SaveItem(
                text = SavedMarks.tokenOf(first),
                context = ContextSentence.around(
                    paragraphOf(first),
                    first.charRange.first,
                    last.charRange.last + 1,
                ),
                words = words,
            )
        }
}
