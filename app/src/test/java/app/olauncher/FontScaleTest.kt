package app.olauncher

import app.olauncher.helper.MAX_EFFECTIVE_FONT_SCALE
import app.olauncher.helper.effectiveFontScale
import org.junit.Assert.assertEquals
import org.junit.Test

class FontScaleTest {
    @Test
    fun defaultsStayAtOne() {
        assertEquals(1.0f, effectiveFontScale(1.0f, 1.0f), 0.001f)
    }

    @Test
    fun systemAndAppScalesMultiply() {
        assertEquals(1.43f, effectiveFontScale(1.3f, 1.1f), 0.001f)
    }

    @Test
    fun resultIsCapped() {
        assertEquals(1.5f, effectiveFontScale(2.0f, 1.5f), 0.001f)
        assertEquals(MAX_EFFECTIVE_FONT_SCALE, effectiveFontScale(2.0f, 1.5f), 0.001f)
    }
}
