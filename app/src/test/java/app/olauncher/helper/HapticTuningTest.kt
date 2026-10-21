package app.olauncher.helper

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticTuningTest {

    @Test
    fun `amplitude maps boundaries and clamps out of range input`() {
        assertNull(HapticTuning.amplitudeFor(0))
        assertEquals(1, HapticTuning.amplitudeFor(1))
        assertEquals(255, HapticTuning.amplitudeFor(100))
        assertNull(HapticTuning.amplitudeFor(-40))
        assertEquals(255, HapticTuning.amplitudeFor(400))
    }

    @Test
    fun `amplitude grows with strength`() {
        assertTrue(HapticTuning.amplitudeFor(30)!! < HapticTuning.amplitudeFor(60)!!)
    }

    @Test
    fun `duration clamps to range`() {
        assertEquals(10, HapticTuning.clampDurationMs(-5))
        assertEquals(10, HapticTuning.clampDurationMs(10))
        assertEquals(200, HapticTuning.clampDurationMs(200))
        assertEquals(200, HapticTuning.clampDurationMs(9999))
    }

    @Test
    fun `defaults are not custom and any change is`() {
        assertFalse(HapticTuning.isCustom(HapticTuning.DEFAULT_STRENGTH, HapticTuning.DEFAULT_DURATION_MS))
        assertTrue(HapticTuning.isCustom(HapticTuning.DEFAULT_STRENGTH + 1, HapticTuning.DEFAULT_DURATION_MS))
        assertTrue(HapticTuning.isCustom(HapticTuning.DEFAULT_STRENGTH, HapticTuning.DEFAULT_DURATION_MS + 1))
    }

    @Test
    fun `plan carries distinguishing strength and duration downstream`() {
        val plan = Haptics.planFor(Haptics.Kind.CONFIRM, true, strength = 37, durationMs = 143, hasAmplitudeControl = true)
        assertEquals(Haptics.Plan.Custom(HapticTuning.amplitudeFor(37)!!, 143), plan)
        val other = Haptics.planFor(Haptics.Kind.CONFIRM, true, strength = 91, durationMs = 61, hasAmplitudeControl = true)
        assertEquals(Haptics.Plan.Custom(HapticTuning.amplitudeFor(91)!!, 61), other)
    }

    @Test
    fun `plan is skipped when disabled for every kind, even with custom sliders`() {
        Haptics.Kind.values().forEach {
            assertEquals(Haptics.Plan.Skip, Haptics.planFor(it, false, 37, 143, true))
        }
    }

    @Test
    fun `plan uses system haptic for default sliders or no amplitude control`() {
        val system = Haptics.Plan.System(HapticFeedbackConstants.CONFIRM)
        assertEquals(system, Haptics.planFor(Haptics.Kind.CONFIRM, true, HapticTuning.DEFAULT_STRENGTH, HapticTuning.DEFAULT_DURATION_MS, true))
        assertEquals(system, Haptics.planFor(Haptics.Kind.CONFIRM, true, 37, 143, false))
    }

    @Test
    fun `strength zero skips the buzz`() {
        assertEquals(Haptics.Plan.Skip, Haptics.planFor(Haptics.Kind.TICK, true, 0, 143, true))
    }
}
