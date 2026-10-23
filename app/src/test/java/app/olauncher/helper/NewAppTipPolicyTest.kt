package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAppTipPolicyTest {
    private fun show(
        seen: Boolean = false, drawer: Boolean = true, new: Boolean = true,
        kb: Boolean = false, focus: Boolean = false, kbShown: Boolean = false,
    ) = NewAppTipPolicy.shouldShow(seen, drawer, new, kb, focus, kbShown)

    @Test fun showsWhenIdle() = assertTrue(show())
    @Test fun neverTwice() = assertFalse(show(seen = true))
    @Test fun notOverKeyboard() = assertFalse(show(kb = true, focus = true, kbShown = true))
    @Test fun waitsForKeyboardToOpen() = assertFalse(show(focus = true))
    @Test fun showsAfterKeyboardClosed() = assertTrue(show(focus = true, kbShown = true))
    @Test fun needsNewApp() = assertFalse(show(new = false))
    @Test fun onlyLaunchDrawer() = assertFalse(show(drawer = false))
}
