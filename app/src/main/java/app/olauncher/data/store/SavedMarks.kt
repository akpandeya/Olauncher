package app.olauncher.data.store

/**
 * One canonical key for saved words across the capture UIs. Saves store the stripped
 * token ("Regierung"), while a screen word keeps its raw text ("Regierung.") — both
 * sides key through [keyOf], so a saved word always finds its dot or underline.
 */
object SavedMarks {

    /** The pickable token of a screen word, stripped like the reader's choices. */
    fun tokenOf(word: ScreenWord): String =
        WordCapture.tokenize(word.text).firstOrNull() ?: word.text

    /** The saved-word key of a screen word: its token, lowercased. */
    fun keyOf(word: ScreenWord): String = tokenOf(word).lowercase()
}
