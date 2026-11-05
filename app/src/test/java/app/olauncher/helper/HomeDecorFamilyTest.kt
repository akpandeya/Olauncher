package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Family detection and animation phase maths for the seasonal home dressing
 * (HomeDecorView). The maths is context-free; the drawing itself needs a device.
 */
class HomeDecorFamilyTest {

    // ---- familyFor ----

    @Test
    fun `seasonal pack ids map to their family prefix`() {
        assertEquals(ThemePackDecor.FAMILY_HALLOWEEN, ThemePackDecor.familyFor("halloween-light"))
        assertEquals(ThemePackDecor.FAMILY_HALLOWEEN, ThemePackDecor.familyFor("halloween-bold-dark"))
        assertEquals(ThemePackDecor.FAMILY_DIWALI, ThemePackDecor.familyFor("diwali-dark"))
        assertEquals(ThemePackDecor.FAMILY_DIWALI, ThemePackDecor.familyFor("diwali-bold-light"))
        assertEquals(ThemePackDecor.FAMILY_CHRISTMAS, ThemePackDecor.familyFor("christmas-bold-light"))
        assertEquals(ThemePackDecor.FAMILY_CHRISTMAS, ThemePackDecor.familyFor("christmas-dark"))
    }

    @Test
    fun `plain packs have no family`() {
        assertNull(ThemePackDecor.familyFor("thumbline-light"))
        assertNull(ThemePackDecor.familyFor("sage-bold-dark"))
        assertNull(ThemePackDecor.familyFor("midnight-dark"))
        assertNull(ThemePackDecor.familyFor("rose-light"))
    }

    @Test
    fun `empty or null ids have no family`() {
        assertNull(ThemePackDecor.familyFor(""))
        assertNull(ThemePackDecor.familyFor(null))
    }

    @Test
    fun `family match is case-insensitive on the prefix`() {
        assertEquals(ThemePackDecor.FAMILY_DIWALI, ThemePackDecor.familyFor("Diwali-Dark"))
    }

    // ---- phase ----

    @Test
    fun `phase wraps elapsed time into 0 until 1`() {
        assertEquals(0f, ThemePackDecor.phase(0f, 3f), 1e-6f)
        assertEquals(0.5f, ThemePackDecor.phase(1.5f, 3f), 1e-6f)
        // One full period wraps back to the start.
        assertEquals(0f, ThemePackDecor.phase(3f, 3f), 1e-6f)
        // Negative delays phase-shift forward, like CSS animation-delay.
        assertEquals(0.5f, ThemePackDecor.phase(0f, 3f, delayS = -1.5f), 1e-6f)
        assertEquals(2f / 3f, ThemePackDecor.phase(1f, 3f, delayS = 1f), 1e-6f)
        // A delay that reaches the full period wraps back to the start.
        assertEquals(0f, ThemePackDecor.phase(2f, 3f, delayS = 1f), 1e-6f)
        assertEquals(0f, ThemePackDecor.phase(0f, 3f), 1e-6f)
    }

    @Test
    fun `phase is zero for a non-positive period`() {
        assertEquals(0f, ThemePackDecor.phase(10f, 0f))
        assertEquals(0f, ThemePackDecor.phase(10f, -1f))
    }

    // ---- twinkle ----

    @Test
    fun `twinkle oscillates between 0_2 and 1`() {
        assertEquals(0.2f, ThemePackDecor.twinkleAlpha(0f), 1e-4f)
        assertEquals(1f, ThemePackDecor.twinkleAlpha(0.5f), 1e-4f)
        assertEquals(0.2f, ThemePackDecor.twinkleAlpha(1f), 1e-4f)
        val mid = ThemePackDecor.twinkleAlpha(0.25f)
        assertTrue("mid-rise alpha $mid in range", mid > 0.2f && mid < 1f)
    }

    // ---- flicker ----

    @Test
    fun `flicker scale and alpha follow the mock keyframes`() {
        assertEquals(0.85f, ThemePackDecor.flickerScaleY(0f), 1e-4f)
        assertEquals(1.1f, ThemePackDecor.flickerScaleY(0.5f), 1e-4f)
        assertEquals(1f, ThemePackDecor.flickerAlpha(0f), 1e-4f)
        assertEquals(0.8f, ThemePackDecor.flickerAlpha(0.5f), 1e-4f)
    }

    // ---- bob / drift ----

    @Test
    fun `bob rises mid-cycle and returns to rest`() {
        assertEquals(0f, ThemePackDecor.bobOffsetDp(0f, 4f), 1e-4f)
        assertEquals(-4f, ThemePackDecor.bobOffsetDp(0.5f, 4f), 1e-4f)
        assertEquals(0f, ThemePackDecor.bobOffsetDp(1f, 4f), 1e-4f)
    }

    @Test
    fun `drift moves left and down mid-cycle`() {
        assertEquals(-7f, ThemePackDecor.driftOffsetXDp(0.5f, 7f), 1e-4f)
        assertEquals(4f, ThemePackDecor.driftOffsetYDp(0.5f, 4f), 1e-4f)
        assertEquals(0f, ThemePackDecor.driftOffsetXDp(0f, 7f), 1e-4f)
    }

    // ---- snow ----

    @Test
    fun `snow progress wraps across the fall period`() {
        assertEquals(0f, ThemePackDecor.snowProgress(0f, 7f, 0f), 1e-6f)
        assertEquals(0.5f, ThemePackDecor.snowProgress(3.5f, 7f, 0f), 1e-6f)
        // A flake delayed by half a period starts half way down.
        assertEquals(0.5f, ThemePackDecor.snowProgress(0f, 7f, -3.5f), 1e-6f)
    }
}
