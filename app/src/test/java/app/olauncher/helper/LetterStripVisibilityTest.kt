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
}
