package app.olauncher.data.store

/**
 * One canonical key for saved words across the capture UIs. Saves store the stripped
 * token ("Regierung"), while a screen word keeps its raw text ("Regierung.") — both
 * sides key through [keyOf], so a saved word always finds its dot or underline.
 *
 * A [CapturePick.Word] keys like its word; a [CapturePick.SplitVerb] keys like its
 * joined lemma ("aufnehmen"), because that is the text a split save stores.
 */
object SavedMarks {

    /** The pickable token of a screen word, stripped like the reader's choices. */
    fun tokenOf(word: ScreenWord): String =
        WordCapture.tokenize(word.text).firstOrNull() ?: word.text

    /** The saved-word key of a screen word: its token, lowercased. */
    fun keyOf(word: ScreenWord): String = tokenOf(word).lowercase()

    /** The stored text of a pick: a word's token, or a split verb's joined lemma. */
    fun tokenOf(pick: CapturePick): String = when (pick) {
        is CapturePick.Word -> tokenOf(pick.word)
        is CapturePick.SplitVerb -> pick.lemma
    }

    /** The saved-word key of a pick: a word's token, or a split verb's lowercased lemma. */
    fun keyOf(pick: CapturePick): String = when (pick) {
        is CapturePick.Word -> keyOf(pick.word)
        is CapturePick.SplitVerb -> pick.lemma.lowercase()
    }
}
