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

    @Test
    fun `stagger delay returns 0 when animators disabled`() {
        assertEquals(0L, MotionPolicy.staggerDelayMs(0, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.staggerDelayMs(1, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.staggerDelayMs(5, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.staggerDelayMs(5, stepMs = 50L, animatorsEnabled = false))
    }

    @Test
    fun `stagger delay returns 0 for index 0 or negative index`() {
        assertEquals(0L, MotionPolicy.staggerDelayMs(0, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.staggerDelayMs(-1, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.staggerDelayMs(-5, animatorsEnabled = true))
    }

    @Test
    fun `stagger delay scales linearly with index when animators enabled`() {
        assertEquals(30L, MotionPolicy.staggerDelayMs(1, animatorsEnabled = true))
        assertEquals(60L, MotionPolicy.staggerDelayMs(2, animatorsEnabled = true))
        assertEquals(90L, MotionPolicy.staggerDelayMs(3, animatorsEnabled = true))
        assertEquals(120L, MotionPolicy.staggerDelayMs(4, animatorsEnabled = true))
    }

    @Test
    fun `stagger delay uses custom step within limits`() {
        assertEquals(25L, MotionPolicy.staggerDelayMs(1, animatorsEnabled = true, stepMs = 25L))
        assertEquals(50L, MotionPolicy.staggerDelayMs(2, animatorsEnabled = true, stepMs = 25L))
    }

    @Test
    fun `stagger step is clamped to MAX_DURATION_MS`() {
        val clampedDelay = MotionPolicy.staggerDelayMs(2, stepMs = 200L, animatorsEnabled = true)
        assertEquals(2 * MotionPolicy.MAX_DURATION_MS, clampedDelay)
    }

    @Test
    fun `motion constants are within limits`() {
        assertTrue(MotionPolicy.STAGGER_STEP_MS <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.PRESS_DURATION_MS <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.FLIP_HALF_DURATION_MS * 2 <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.PRESS_SCALE in 0.9f..0.99f)
    }
}
