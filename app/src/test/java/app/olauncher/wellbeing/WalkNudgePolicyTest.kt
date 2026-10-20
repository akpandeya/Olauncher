package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkNudgePolicyTest {

    private val start = 1_000_000L
    private val end = 4_600_000L

    @Test
    fun `499 steps posts`() {
        assertFalse(WalkNudgePolicy.shouldSkip(499, emptyList(), start, end))
    }

    @Test
    fun `500 steps skips`() {
        assertTrue(WalkNudgePolicy.shouldSkip(500, emptyList(), start, end))
    }

    @Test
    fun `exercise overlapping window skips`() {
        val session = ExerciseSpan(start - 600_000L, start + 60_000L)
        assertTrue(WalkNudgePolicy.shouldSkip(10, listOf(session), start, end))
    }

    @Test
    fun `exercise ended before window posts`() {
        val session = ExerciseSpan(start - 900_000L, start - 1L)
        assertFalse(WalkNudgePolicy.shouldSkip(10, listOf(session), start, end))
    }

    @Test
    fun `read failure posts`() {
        assertFalse(WalkNudgePolicy.shouldSkip(null, null, start, end))
    }

    @Test
    fun `today line with missing parts leaves them out`() {
        val line = HealthSummaryFormatting.formatLine(HealthSummary(steps = 1200, sleepMinutes = null, restingHeartRate = 0, exerciseMinutes = 30))
        assertEquals("1,200 steps · Exercise 30 min", line)
        assertNull(HealthSummaryFormatting.formatLine(HealthSummary()))
    }
}
