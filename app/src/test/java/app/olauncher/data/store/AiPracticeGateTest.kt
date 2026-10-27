package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard in front of every AI practice network call (#602, 9.9): the feature
 * switch, the key and the model decide. With the switch off no call is made, ever.
 */
class AiPracticeGateTest {

    private val model = "glm-5.3-flash"

    @Test
    fun `runs only when enabled, keyed and modelled`() {
        assertTrue(AiPracticeGate.canRun(enabled = true, hasKey = true, model = model))
    }

    @Test
    fun `every other combination is blocked`() {
        assertFalse(AiPracticeGate.canRun(enabled = false, hasKey = true, model = model))
        assertFalse(AiPracticeGate.canRun(enabled = true, hasKey = false, model = model))
        assertFalse(AiPracticeGate.canRun(enabled = false, hasKey = false, model = model))
    }

    @Test
    fun `a blank or whitespace model blocks even when enabled and keyed`() {
        assertFalse(AiPracticeGate.canRun(enabled = true, hasKey = true, model = ""))
        assertFalse(AiPracticeGate.canRun(enabled = true, hasKey = true, model = "   "))
    }

    @Test
    fun `the blocking reason names what is missing`() {
        assertEquals(AiPracticeGate.Block.DISABLED, AiPracticeGate.block(false, true, model))
        assertEquals(AiPracticeGate.Block.NO_KEY, AiPracticeGate.block(true, false, model))
        assertEquals(AiPracticeGate.Block.NO_MODEL, AiPracticeGate.block(true, true, ""))
        assertEquals(null, AiPracticeGate.block(true, true, model))
        // Disabled wins: with the switch off, nothing else matters.
        assertEquals(AiPracticeGate.Block.DISABLED, AiPracticeGate.block(false, false, ""))
    }
}
