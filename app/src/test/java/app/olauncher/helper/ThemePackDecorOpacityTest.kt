package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The decor opacity setting maths ([ThemePackDecor.clampOpacity], [ThemePackDecor.opacityFraction]):
 * a percent clamped to 0..100 and the 0..1 view alpha Home applies from it.
 */
class ThemePackDecorOpacityTest {

    @Test
    fun `default percent maps to a fully opaque overlay`() {
        assertEquals(100, ThemePackDecor.DECOR_OPACITY_DEFAULT)
        assertEquals(1f, ThemePackDecor.opacityFraction(ThemePackDecor.DECOR_OPACITY_DEFAULT), 1e-6f)
    }

    @Test
    fun `opacity fraction scales linearly inside the range`() {
        assertEquals(0f, ThemePackDecor.opacityFraction(0), 1e-6f)
        assertEquals(0.5f, ThemePackDecor.opacityFraction(50), 1e-6f)
        assertEquals(1f, ThemePackDecor.opacityFraction(100), 1e-6f)
    }

    @Test
    fun `out of range percents clamp`() {
        assertEquals(0, ThemePackDecor.clampOpacity(-1))
        assertEquals(0f, ThemePackDecor.opacityFraction(-1), 1e-6f)
        assertEquals(0f, ThemePackDecor.opacityFraction(Int.MIN_VALUE), 1e-6f)
        assertEquals(100, ThemePackDecor.clampOpacity(150))
        assertEquals(1f, ThemePackDecor.opacityFraction(150), 1e-6f)
        assertEquals(1f, ThemePackDecor.opacityFraction(Int.MAX_VALUE), 1e-6f)
    }
}
