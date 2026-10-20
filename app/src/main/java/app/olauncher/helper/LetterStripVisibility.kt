package app.olauncher.helper

/** Decides whether the A-Z letter strip is shown in the drawer. */
object LetterStripVisibility {
    fun shouldShow(
        stripEnabled: Boolean,
        keyboardVisible: Boolean,
        queryBlank: Boolean,
        hasLetters: Boolean,
    ): Boolean = stripEnabled && !keyboardVisible && queryBlank && hasLetters
}
