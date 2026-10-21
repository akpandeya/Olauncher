package app.olauncher.wellbeing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NudgeSchedulerTest {
    @Test fun wantedWhenThresholdSetAndAccessGranted() =
        assertTrue(NudgeScheduler.socialJobWanted(30, true))

    @Test fun notWantedWithoutAccess() =
        assertFalse(NudgeScheduler.socialJobWanted(30, false))

    @Test fun notWantedWhenThresholdZero() =
        assertFalse(NudgeScheduler.socialJobWanted(0, true))

    @Test fun notWantedWhenThresholdNegative() =
        assertFalse(NudgeScheduler.socialJobWanted(-5, true))
}
