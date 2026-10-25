package app.olauncher.helper

/** Decides whether the A-Z letter strip is shown in the drawer. */
object LetterStripVisibility {
    fun shouldShow(
        stripEnabled: Boolean,
        keyboardVisible: Boolean,
        queryBlank: Boolean,
        hasLetters: Boolean,
    ): Boolean = stripEnabled && !keyboardVisible && queryBlank && hasLetters

    /**
     * Decides whether the one-time hint for dragging the letter strip is shown.
     * Shown once, never again, and not shown when the strip is hidden.
     */
    fun shouldShowHint(
        hintShown: Boolean,
        stripVisible: Boolean,
    ): Boolean = !hintShown && stripVisible

    fun shouldShowHint(
        hintShown: Boolean,
        stripEnabled: Boolean,
        keyboardVisible: Boolean,
        queryBlank: Boolean,
        hasLetters: Boolean,
    ): Boolean = !hintShown && shouldShow(stripEnabled, keyboardVisible, queryBlank, hasLetters)
}
