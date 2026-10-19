package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionPolicyTest {

    @Test
    fun `returns base duration when animators are enabled`() {
        assertEquals(120L, MotionPolicy.durationMs(120L, animatorsEnabled = true))
        assertEquals(150L, MotionPolicy.durationMs(150L, animatorsEnabled = true))
        assertEquals(50L, MotionPolicy.durationMs(50L, animatorsEnabled = true))
    }

    @Test
    fun `returns 0 when animators are disabled`() {
        assertEquals(0L, MotionPolicy.durationMs(120L, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.durationMs(150L, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.durationMs(50L, animatorsEnabled = false))
    }

    @Test
    fun `returns 0 for zero base duration`() {
        assertEquals(0L, MotionPolicy.durationMs(0L, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.durationMs(0L, animatorsEnabled = false))
    }

    @Test
    fun `clamps negative duration to 0`() {
        assertEquals(0L, MotionPolicy.durationMs(-10L, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.durationMs(-10L, animatorsEnabled = false))
    }

    @Test
    fun `handles Int overload correctly`() {
        assertEquals(120L, MotionPolicy.durationMs(120, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.durationMs(120, animatorsEnabled = false))
    }

    @Test
    fun `drawer and today motion durations do not exceed 150 ms`() {
        assertTrue(MotionPolicy.DRAWER_DURATION_MS <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.TODAY_DURATION_MS <= MotionPolicy.MAX_DURATION_MS)
        assertEquals(120L, MotionPolicy.DRAWER_DURATION_MS)
        assertEquals(120L, MotionPolicy.TODAY_DURATION_MS)
        assertEquals(150L, MotionPolicy.MAX_DURATION_MS)
    }
}
