package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenTimeTest {
    @Test fun minutesAndSeconds() {
        assertEquals("1:20", SpokenTime.format(80_000L))
        assertEquals("3:40", SpokenTime.format(220_999L))
    }

    @Test fun zeroAndNegative() {
        assertEquals("0:00", SpokenTime.format(0L))
        assertEquals("0:00", SpokenTime.format(-5_000L))
    }

    @Test fun hours() {
        assertEquals("1:02:03", SpokenTime.format(3_723_000L))
    }
}
