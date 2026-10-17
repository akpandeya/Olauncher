package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutIconStyleTest {

    @Test
    fun `constants have expected values`() {
        assertEquals(0, ShortcutIconStyle.ORIGINAL)
        assertEquals(1, ShortcutIconStyle.MONOCHROME)
        assertEquals(2, ShortcutIconStyle.TEXT_COLOUR)
        assertEquals(ShortcutIconStyle.ORIGINAL, ShortcutIconStyle.DEFAULT)
        assertEquals(ShortcutIconStyle.ORIGINAL, ShortcutIconStyle.MIN)
        assertEquals(ShortcutIconStyle.TEXT_COLOUR, ShortcutIconStyle.MAX)
        assertEquals(0.85f, ShortcutIconStyle.FALLBACK_ALPHA, 0.001f)
        assertEquals(217, ShortcutIconStyle.FALLBACK_ALPHA_INT)
    }

    @Test
    fun `clamp coerces negative values to ORIGINAL`() {
        assertEquals(ShortcutIconStyle.ORIGINAL, ShortcutIconStyle.clamp(Int.MIN_VALUE))
        assertEquals(ShortcutIconStyle.ORIGINAL, ShortcutIconStyle.clamp(-100))
        assertEquals(ShortcutIconStyle.ORIGINAL, ShortcutIconStyle.clamp(-1))
    }

    @Test
    fun `clamp coerces values above TEXT_COLOUR to TEXT_COLOUR`() {
        assertEquals(ShortcutIconStyle.TEXT_COLOUR, ShortcutIconStyle.clamp(3))
        assertEquals(ShortcutIconStyle.TEXT_COLOUR, ShortcutIconStyle.clamp(10))
        assertEquals(ShortcutIconStyle.TEXT_COLOUR, ShortcutIconStyle.clamp(Int.MAX_VALUE))
    }

    @Test
    fun `clamp preserves valid styles`() {
        assertEquals(ShortcutIconStyle.ORIGINAL, ShortcutIconStyle.clamp(ShortcutIconStyle.ORIGINAL))
        assertEquals(ShortcutIconStyle.MONOCHROME, ShortcutIconStyle.clamp(ShortcutIconStyle.MONOCHROME))
        assertEquals(ShortcutIconStyle.TEXT_COLOUR, ShortcutIconStyle.clamp(ShortcutIconStyle.TEXT_COLOUR))
    }

    @Test
    fun `resolveTreatment returns ORIGINAL when style is ORIGINAL regardless of monochrome layer`() {
        assertEquals(
            ShortcutIconStyle.Treatment.ORIGINAL,
            ShortcutIconStyle.resolveTreatment(ShortcutIconStyle.ORIGINAL, hasMonochromeLayer = true)
        )
        assertEquals(
            ShortcutIconStyle.Treatment.ORIGINAL,
            ShortcutIconStyle.resolveTreatment(ShortcutIconStyle.ORIGINAL, hasMonochromeLayer = false)
        )
    }

    @Test
    fun `resolveTreatment for MONOCHROME uses MONOCHROME_LAYER or FALLBACK_GRAYSCALE`() {
        assertEquals(
            ShortcutIconStyle.Treatment.MONOCHROME_LAYER,
            ShortcutIconStyle.resolveTreatment(ShortcutIconStyle.MONOCHROME, hasMonochromeLayer = true)
        )
        assertEquals(
            ShortcutIconStyle.Treatment.FALLBACK_GRAYSCALE,
            ShortcutIconStyle.resolveTreatment(ShortcutIconStyle.MONOCHROME, hasMonochromeLayer = false)
        )
    }

    @Test
    fun `resolveTreatment for TEXT_COLOUR uses MONOCHROME_LAYER or FALLBACK_GRAYSCALE_ALPHA`() {
        assertEquals(
            ShortcutIconStyle.Treatment.MONOCHROME_LAYER,
            ShortcutIconStyle.resolveTreatment(ShortcutIconStyle.TEXT_COLOUR, hasMonochromeLayer = true)
        )
        assertEquals(
            ShortcutIconStyle.Treatment.FALLBACK_GRAYSCALE_ALPHA,
            ShortcutIconStyle.resolveTreatment(ShortcutIconStyle.TEXT_COLOUR, hasMonochromeLayer = false)
        )
    }

    @Test
    fun `resolveTreatment clamps out of range styles`() {
        assertEquals(
            ShortcutIconStyle.Treatment.ORIGINAL,
            ShortcutIconStyle.resolveTreatment(-1, hasMonochromeLayer = true)
        )
        assertEquals(
            ShortcutIconStyle.Treatment.MONOCHROME_LAYER,
            ShortcutIconStyle.resolveTreatment(99, hasMonochromeLayer = true)
        )
        assertEquals(
            ShortcutIconStyle.Treatment.FALLBACK_GRAYSCALE_ALPHA,
            ShortcutIconStyle.resolveTreatment(99, hasMonochromeLayer = false)
        )
    }

    @Test
    fun `alphaForTreatment returns 255 except for FALLBACK_GRAYSCALE_ALPHA which returns 217`() {
        assertEquals(255, ShortcutIconStyle.alphaForTreatment(ShortcutIconStyle.Treatment.ORIGINAL))
        assertEquals(255, ShortcutIconStyle.alphaForTreatment(ShortcutIconStyle.Treatment.MONOCHROME_LAYER))
        assertEquals(255, ShortcutIconStyle.alphaForTreatment(ShortcutIconStyle.Treatment.FALLBACK_GRAYSCALE))
        assertEquals(217, ShortcutIconStyle.alphaForTreatment(ShortcutIconStyle.Treatment.FALLBACK_GRAYSCALE_ALPHA))
    }
}
