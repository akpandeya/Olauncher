package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnceGateTest {

    @Test
    fun `first ask per key is true and later asks are false`() {
        val gate = OnceGate()
        assertTrue(gate.firstTime("notes.progress"))
        assertFalse(gate.firstTime("notes.progress"))
        assertFalse(gate.firstTime("notes.progress"))
    }

    @Test
    fun `keys are independent`() {
        val gate = OnceGate()
        assertTrue(gate.firstTime("a"))
        assertTrue(gate.firstTime("b"))
        assertFalse(gate.firstTime("a"))
    }

    @Test
    fun `a fresh gate allows every key again`() {
        assertTrue(OnceGate().firstTime("a"))
        assertTrue(OnceGate().firstTime("a"))
    }
}
