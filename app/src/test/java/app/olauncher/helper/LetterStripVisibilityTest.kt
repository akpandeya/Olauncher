package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterStripVisibilityTest {
    private fun show(
        enabled: Boolean = true,
        keyboard: Boolean = false,
        blank: Boolean = true,
        letters: Boolean = true,
    ) = LetterStripVisibility.shouldShow(enabled, keyboard, blank, letters)

    @Test fun shownWhenEnabledAndKeyboardClosed() = assertTrue(show())
    @Test fun hiddenWhileKeyboardOpen() = assertFalse(show(keyboard = true))
    @Test fun hiddenWhenSettingOff() = assertFalse(show(enabled = false))
    @Test fun hiddenWhenSettingOffAndKeyboardClosed() = assertFalse(show(enabled = false, keyboard = false))
    @Test fun hiddenWhenQueryNotBlank() = assertFalse(show(blank = false))
    @Test fun hiddenWhenNoLetters() = assertFalse(show(letters = false))

    private fun showHint(
        hintShown: Boolean = false,
        stripVisible: Boolean = true,
    ) = LetterStripVisibility.shouldShowHint(hintShown, stripVisible)

    private fun showHintFull(
        hintShown: Boolean = false,
        enabled: Boolean = true,
        keyboard: Boolean = false,
        blank: Boolean = true,
        letters: Boolean = true,
    ) = LetterStripVisibility.shouldShowHint(hintShown, enabled, keyboard, blank, letters)

    @Test fun hintShownWhenNotYetSeenAndStripVisible() = assertTrue(showHint())
    @Test fun hintNotShownWhenAlreadySeen() = assertFalse(showHint(hintShown = true))
    @Test fun hintNotShownWhenStripHidden() = assertFalse(showHint(stripVisible = false))
    @Test fun hintNotShownWhenAlreadySeenAndStripHidden() = assertFalse(showHint(hintShown = true, stripVisible = false))

    @Test fun hintFullShownWhenNotSeenAndStripVisible() = assertTrue(showHintFull())
    @Test fun hintFullHiddenWhenAlreadySeen() = assertFalse(showHintFull(hintShown = true))
    @Test fun hintFullHiddenWhenKeyboardOpen() = assertFalse(showHintFull(keyboard = true))
    @Test fun hintFullHiddenWhenDisabled() = assertFalse(showHintFull(enabled = false))
    @Test fun hintFullHiddenWhenQueryNotBlank() = assertFalse(showHintFull(blank = false))
    @Test fun hintFullHiddenWhenNoLetters() = assertFalse(showHintFull(letters = false))
}
