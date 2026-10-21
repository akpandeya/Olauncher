package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTickTest {
    @Test fun allOffNeedsNoTick() = assertFalse(HomeTick.needsTick(false, false, false, false))
    @Test fun homeSetsAlone() = assertTrue(HomeTick.needsTick(true, false, false, false))
    @Test fun wordsClockAlone() = assertTrue(HomeTick.needsTick(false, true, false, false))
    @Test fun contextLineAlone() = assertTrue(HomeTick.needsTick(false, false, true, false))
    @Test fun musicPulseAlone() = assertTrue(HomeTick.needsTick(false, false, false, true))

    @Test fun nullLastDayIsNotAChange() = assertFalse(HomeTick.dayChanged(null, 20_000L))
    @Test fun sameDayIsNotAChange() = assertFalse(HomeTick.dayChanged(20_123L, 20_123L))
    @Test fun nextDayIsAChange() = assertTrue(HomeTick.dayChanged(20_123L, 20_124L))
}
