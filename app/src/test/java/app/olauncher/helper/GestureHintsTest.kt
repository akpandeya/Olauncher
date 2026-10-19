package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureHintsTest {
    private val all = Gesture.values().toSet()

    @Test
    fun firstHintIsSwipeRight() {
        assertEquals(Hint(Gesture.SWIPE_RIGHT, 0), GestureHints.next(0, 0L, 10L, all))
    }

    @Test
    fun onlyOnePerDay() {
        assertNull(GestureHints.next(1, 10L, 10L, all))
        assertEquals(Gesture.SWIPE_LEFT, GestureHints.next(1, 10L, 11L, all)?.gesture)
    }

    @Test
    fun skipsDisabledGestures() {
        val enabled = all - Gesture.SWIPE_RIGHT - Gesture.SWIPE_LEFT
        assertEquals(Hint(Gesture.DOUBLE_TAP, 2), GestureHints.next(0, 0L, 10L, enabled))
        assertEquals(Hint(Gesture.LONG_PRESS, 3), GestureHints.next(2 + 1, 10L, 11L, enabled))
    }

    @Test
    fun endsAfterLastHint() {
        assertNull(GestureHints.next(Gesture.values().size, 1L, 99L, all))
        assertNull(GestureHints.next(GestureHints.DONE, 1L, 99L, all))
    }

    @Test
    fun endsWhenRemainingAreDisabled() {
        assertNull(GestureHints.next(3, 1L, 99L, all - Gesture.LONG_PRESS))
        assertNull(GestureHints.next(0, 0L, 10L, emptySet()))
    }
}
