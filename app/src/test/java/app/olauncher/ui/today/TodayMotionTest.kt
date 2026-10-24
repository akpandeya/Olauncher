package app.olauncher.ui.today

import app.olauncher.helper.MotionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayMotionTest {

    @Test
    fun `stagger delay returns zero when system animations are off`() {
        for (i in 0..10) {
            assertEquals(0L, MotionPolicy.staggerDelayMs(i, animatorsEnabled = false))
        }
    }

    @Test
    fun `stagger delay returns zero for negative and zero indices`() {
        assertEquals(0L, MotionPolicy.staggerDelayMs(-10, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.staggerDelayMs(-1, animatorsEnabled = true))
        assertEquals(0L, MotionPolicy.staggerDelayMs(0, animatorsEnabled = true))
    }

    @Test
    fun `stagger delay per index increases by step when animations are on`() {
        val step = MotionPolicy.STAGGER_STEP_MS
        assertTrue(step > 0L)
        assertTrue(step <= MotionPolicy.MAX_DURATION_MS)

        for (i in 1..5) {
            assertEquals(i * step, MotionPolicy.staggerDelayMs(i, animatorsEnabled = true))
        }
    }

    @Test
    fun `stagger delay respects custom step clamped to max duration`() {
        assertEquals(40L, MotionPolicy.staggerDelayMs(2, animatorsEnabled = true, stepMs = 20L))
        // Clamped at MAX_DURATION_MS (150L)
        assertEquals(300L, MotionPolicy.staggerDelayMs(2, animatorsEnabled = true, stepMs = 300L))
    }

    @Test
    fun `all motion constants are within MotionPolicy limits`() {
        assertTrue(MotionPolicy.TODAY_DURATION_MS <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.STAGGER_STEP_MS <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.PRESS_DURATION_MS <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.FLIP_HALF_DURATION_MS * 2 <= MotionPolicy.MAX_DURATION_MS)
        assertTrue(MotionPolicy.PRESS_SCALE > 0.9f && MotionPolicy.PRESS_SCALE < 1.0f)
    }

    @Test
    fun `motion durations are zero when animations are off`() {
        assertEquals(0L, MotionPolicy.durationMs(MotionPolicy.TODAY_DURATION_MS, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.durationMs(MotionPolicy.PRESS_DURATION_MS, animatorsEnabled = false))
        assertEquals(0L, MotionPolicy.durationMs(MotionPolicy.FLIP_HALF_DURATION_MS, animatorsEnabled = false))
    }
}
