package app.olauncher.helper

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentionTest {

    private val monday9 = LocalDateTime.of(2026, 10, 19, 9, 0)
    private val mondayDay = Intention.dayOf(monday9)

    @Test
    fun shownSameDay() {
        assertEquals("finish the draft", Intention.current("finish the draft", mondayDay, monday9))
    }

    @Test
    fun goneNextDay() {
        assertNull(Intention.current("finish the draft", mondayDay, LocalDateTime.of(2026, 10, 20, 9, 0)))
    }

    @Test
    fun stillShownAfterMidnightBeforeRollover() {
        assertEquals("x", Intention.current("x", mondayDay, LocalDateTime.of(2026, 10, 20, 3, 59)))
    }

    @Test
    fun goneAtRolloverHour() {
        assertNull(Intention.current("x", mondayDay, LocalDateTime.of(2026, 10, 20, 4, 0)))
    }

    @Test
    fun customRolloverHour() {
        val t = LocalDateTime.of(2026, 10, 20, 5, 0)
        assertEquals("x", Intention.current("x", mondayDay, t, rolloverHour = 6))
    }

    @Test
    fun blankOrNullIsNull() {
        assertNull(Intention.current("   ", mondayDay, monday9))
        assertNull(Intention.current(null, mondayDay, monday9))
        assertNull(Intention.current("", mondayDay, monday9))
    }

    @Test
    fun cleanTrimsAndCaps() {
        assertEquals("hi", Intention.clean("  hi  "))
        assertEquals(60, Intention.clean("a".repeat(100)).length)
        assertEquals("", Intention.clean(null))
    }
}
