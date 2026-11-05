package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

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

    // ---- diwali rockets ----

    @Test
    fun `auto salvo roll split matches the mock`() {
        // 55% one rocket, 30% two, 15% three (mock: roll < 0.55 ? 1 : roll < 0.85 ? 2 : 3).
        assertEquals(1, ThemePackDecor.salvoSizeFor(0.0f))
        assertEquals(1, ThemePackDecor.salvoSizeFor(0.54f))
        assertEquals(2, ThemePackDecor.salvoSizeFor(0.55f))
        assertEquals(2, ThemePackDecor.salvoSizeFor(0.56f))
        // The mock's second cut is 0.85, so 0.86 already rolls into the three-rocket salvo.
        assertEquals(3, ThemePackDecor.salvoSizeFor(0.86f))
        assertEquals(3, ThemePackDecor.salvoSizeFor(0.87f))
        assertEquals(3, ThemePackDecor.salvoSizeFor(1.0f))
    }

    @Test
    fun `auto salvo rolls outside 0 until 1 clamp to the ends`() {
        assertEquals(1, ThemePackDecor.salvoSizeFor(-0.7f))
        assertEquals(1, ThemePackDecor.salvoSizeFor(Float.NEGATIVE_INFINITY))
        assertEquals(3, ThemePackDecor.salvoSizeFor(1.2f))
        assertEquals(3, ThemePackDecor.salvoSizeFor(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `evening salvo bands skew bigger than daytime`() {
        // Evening: 25% one, 40% two, 35% three.
        assertEquals(1, ThemePackDecor.salvoSizeFor(0.0f, evening = true))
        assertEquals(1, ThemePackDecor.salvoSizeFor(0.24f, evening = true))
        assertEquals(2, ThemePackDecor.salvoSizeFor(0.25f, evening = true))
        assertEquals(2, ThemePackDecor.salvoSizeFor(0.64f, evening = true))
        assertEquals(3, ThemePackDecor.salvoSizeFor(0.65f, evening = true))
        assertEquals(3, ThemePackDecor.salvoSizeFor(1.0f, evening = true))
        // The same roll that gives one rocket by day gives two in the evening.
        assertEquals(1, ThemePackDecor.salvoSizeFor(0.30f))
        assertEquals(2, ThemePackDecor.salvoSizeFor(0.30f, evening = true))
        // Clamps still hold in the evening branch.
        assertEquals(1, ThemePackDecor.salvoSizeFor(-1f, evening = true))
        assertEquals(3, ThemePackDecor.salvoSizeFor(2f, evening = true))
    }

    @Test
    fun `evening hours are 19 to 23 inclusive`() {
        assertTrue(ThemePackDecor.isEveningHour(19))
        assertTrue(ThemePackDecor.isEveningHour(21))
        assertTrue(ThemePackDecor.isEveningHour(23))
        assertFalse(ThemePackDecor.isEveningHour(18))
        assertFalse(ThemePackDecor.isEveningHour(0))
        assertFalse(ThemePackDecor.isEveningHour(6))
        assertFalse(ThemePackDecor.isEveningHour(12))
    }

    @Test
    fun `celebration salvo is always 3 to 5 rockets`() {
        val rng = Random(42)
        repeat(200) {
            val size = ThemePackDecor.celebrationSalvoSize(rng)
            assertTrue("salvo size $size not in 3..5", size in 3..5)
        }
        // Deterministic given the seed, so a regression is caught exactly.
        assertEquals(3, ThemePackDecor.celebrationSalvoSize(Random(0)))
    }

    @Test
    fun `rocket sky cap and salvo cadence constants`() {
        assertEquals(5, ThemePackDecor.MAX_LIVE_BOOMS)
        assertEquals(7000L, ThemePackDecor.ROCKET_SALVO_PERIOD_MS)
    }
}
