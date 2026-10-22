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
        assertNull(GestureHints.next(3, 1L, 99L, Gesture.values().toSet().filter { it.ordinal < 3 }.toSet()))
        assertNull(GestureHints.next(0, 0L, 10L, emptySet()))
    }

    @Test
    fun newGesturesFollowOldOnesAndEachShowsOnce() {
        assertEquals(
            listOf(
                Gesture.SWIPE_RIGHT, Gesture.SWIPE_LEFT, Gesture.DOUBLE_TAP, Gesture.LONG_PRESS,
                Gesture.SWIPE_UP, Gesture.SWIPE_DOWN, Gesture.TAP_MUSIC, Gesture.TAP_EVENT,
            ),
            GestureHints.order,
        )
        val shown = mutableListOf<Gesture>()
        var index = 0
        var day = 0L
        while (true) {
            day++
            val hint = GestureHints.next(index, day - 1, day, all) ?: break
            shown += hint.gesture
            index = hint.position + 1
        }
        assertEquals(GestureHints.order, shown)
    }

    @Test
    fun skipsMusicAndEventTipsWhenContextLineIsOff() {
        val enabled = all - Gesture.TAP_MUSIC - Gesture.TAP_EVENT
        assertEquals(Gesture.SWIPE_DOWN, GestureHints.next(5, 1L, 2L, enabled)?.gesture)
        assertNull(GestureHints.next(6, 1L, 2L, enabled))
    }
}
