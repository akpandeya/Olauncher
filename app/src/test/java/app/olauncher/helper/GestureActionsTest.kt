package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureActionsTest {
    private fun resolve(slot: GestureSlot, stored: GestureAction, lockModeOn: Boolean = false, hasApp: Boolean = false) =
        GestureActions.resolve(slot, stored, lockModeOn, hasApp)

    @Test
    fun `default double tap locks only when lock mode is on`() {
        assertEquals(GestureAction.LOCK, resolve(GestureSlot.DOUBLE_TAP, GestureAction.DEFAULT, lockModeOn = true))
        assertEquals(GestureAction.NOTHING, resolve(GestureSlot.DOUBLE_TAP, GestureAction.DEFAULT, lockModeOn = false))
    }

    @Test
    fun `default clock and date taps keep their built-in behaviour`() {
        for (lock in listOf(true, false)) {
            assertEquals(GestureAction.DEFAULT, resolve(GestureSlot.CLOCK_TAP, GestureAction.DEFAULT, lockModeOn = lock))
            assertEquals(GestureAction.DEFAULT, resolve(GestureSlot.DATE_TAP, GestureAction.DEFAULT, lockModeOn = lock))
        }
    }

    @Test
    fun `chosen action wins on every slot and ignores lock mode`() {
        for (slot in GestureSlot.values()) {
            assertEquals(GestureAction.TORCH, resolve(slot, GestureAction.TORCH, lockModeOn = true))
            assertEquals(GestureAction.NEXT_TRACK, resolve(slot, GestureAction.NEXT_TRACK))
            assertEquals(GestureAction.PREVIOUS_TRACK, resolve(slot, GestureAction.PREVIOUS_TRACK))
            assertEquals(GestureAction.NOTHING, resolve(slot, GestureAction.NOTHING, lockModeOn = true))
            if (slot != GestureSlot.DOUBLE_TAP) assertEquals(GestureAction.LOCK, resolve(slot, GestureAction.LOCK, lockModeOn = false))
        }
    }

    @Test
    fun `double tap lock choice follows the lock switch`() {
        assertEquals(GestureAction.LOCK, resolve(GestureSlot.DOUBLE_TAP, GestureAction.LOCK, lockModeOn = true))
        assertEquals(GestureAction.NOTHING, resolve(GestureSlot.DOUBLE_TAP, GestureAction.LOCK, lockModeOn = false))
    }

    @Test
    fun `open app needs a saved app`() {
        assertEquals(GestureAction.OPEN_APP, resolve(GestureSlot.CLOCK_TAP, GestureAction.OPEN_APP, hasApp = true))
        assertEquals(GestureAction.NOTHING, resolve(GestureSlot.CLOCK_TAP, GestureAction.OPEN_APP, hasApp = false))
    }

    @Test
    fun `missing or unknown stored value means default`() {
        assertEquals(GestureAction.DEFAULT, GestureActions.fromKey(null))
        assertEquals(GestureAction.DEFAULT, GestureActions.fromKey("???"))
    }

    @Test
    fun `every action round-trips through its key and keys are unique`() {
        for (action in GestureAction.values()) assertEquals(action, GestureActions.fromKey(action.key))
        assertEquals(GestureAction.values().size, GestureAction.values().map { it.key }.toSet().size)
    }

    @Test
    fun `menu lists default first and every action once`() {
        for (slot in GestureSlot.values()) {
            val options = GestureActions.options(slot)
            assertEquals(GestureAction.DEFAULT, options.first())
            assertEquals(GestureAction.values().toList(), options)
        }
    }

    @Test
    fun `only lock needs the accessibility service`() {
        assertTrue(GestureActions.needsAccessibilityService(GestureAction.LOCK))
        for (action in GestureAction.values().filter { it != GestureAction.LOCK }) {
            assertFalse(GestureActions.needsAccessibilityService(action))
        }
    }

    @Test
    fun `slot keys are unique`() {
        assertEquals(GestureSlot.values().size, GestureSlot.values().map { it.key }.toSet().size)
    }
}
