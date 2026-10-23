package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterStripSizingTest {
    @Test fun keepsMaxWhenTall() = assertEquals(40f, LetterStripSizing.textSizePx(3000, 27, 40f), 0f)
    @Test fun shrinksWhenShort() {
        val s = LetterStripSizing.textSizePx(300, 27, 40f)
        assertTrue(s < 10f)
        assertTrue(s * 1.2f * 27 <= 300f + 0.01f)
    }
    @Test fun unknownHeightKeepsMax() = assertEquals(40f, LetterStripSizing.textSizePx(0, 27, 40f), 0f)
}
