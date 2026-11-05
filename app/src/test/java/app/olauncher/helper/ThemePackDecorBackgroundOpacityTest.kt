package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pack background opacity setting maths ([ThemePackDecor.clampBackgroundOpacity],
 * [ThemePackDecor.backgroundAlphaFraction]): a percent clamped to 0..100 and the 0..1
 * scrim alpha Home draws the pack background with.
 */
class ThemePackDecorBackgroundOpacityTest {

    @Test
    fun `default percent maps to a fully opaque scrim`() {
        assertEquals(100, ThemePackDecor.BACKGROUND_OPACITY_DEFAULT)
        assertEquals(1f, ThemePackDecor.backgroundAlphaFraction(ThemePackDecor.BACKGROUND_OPACITY_DEFAULT), 1e-6f)
    }

    @Test
    fun `alpha fraction scales linearly inside the range`() {
        assertEquals(0f, ThemePackDecor.backgroundAlphaFraction(0), 1e-6f)
        assertEquals(0.5f, ThemePackDecor.backgroundAlphaFraction(50), 1e-6f)
        assertEquals(1f, ThemePackDecor.backgroundAlphaFraction(100), 1e-6f)
    }

    @Test
    fun `out of range percents clamp`() {
        assertEquals(0, ThemePackDecor.clampBackgroundOpacity(-1))
        assertEquals(0f, ThemePackDecor.backgroundAlphaFraction(-1), 1e-6f)
        assertEquals(0f, ThemePackDecor.backgroundAlphaFraction(Int.MIN_VALUE), 1e-6f)
        assertEquals(100, ThemePackDecor.clampBackgroundOpacity(150))
        assertEquals(1f, ThemePackDecor.backgroundAlphaFraction(150), 1e-6f)
        assertEquals(1f, ThemePackDecor.backgroundAlphaFraction(Int.MAX_VALUE), 1e-6f)
    }
}
