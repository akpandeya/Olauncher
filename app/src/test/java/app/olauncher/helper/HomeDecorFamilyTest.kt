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

    // ---- diwali shockwave physics ----

    @Test
    fun `ring radius grows linearly from the spawn radius`() {
        // Exact at birth and mid-life (mock: rad = 40 + age * 620).
        assertEquals(40f, ThemePackDecor.ringRadiusAt(0), 1e-4f)
        assertEquals(40f + 620f * 0.7f, ThemePackDecor.ringRadiusAt(700), 1e-2f)
    }

    @Test
    fun `ring stroke fades and thins over its life`() {
        assertEquals(0.5f, ThemePackDecor.ringStrokeAlphaAt(0), 1e-4f)
        assertEquals(0f, ThemePackDecor.ringStrokeAlphaAt(ThemePackDecor.SHOCKWAVE_LIFE_MS), 1e-4f)
        assertEquals(2.5f, ThemePackDecor.ringStrokeWidthAt(0), 1e-4f)
        assertEquals(1f, ThemePackDecor.ringStrokeWidthAt(ThemePackDecor.SHOCKWAVE_LIFE_MS), 1e-4f)
    }

    @Test
    fun `front crossing hits only the band swept this frame`() {
        val radNow = 474f
        val radPrev = 454f
        // Exactly on the new front: hit.
        assertTrue(ThemePackDecor.hitBetween(474f, radNow, radPrev))
        // Just behind the previous front (already passed on an earlier frame): no hit.
        assertFalse(ThemePackDecor.hitBetween(454f, radNow, radPrev))
        // Behind the spawn radius (inside the ring from birth): no hit.
        assertFalse(ThemePackDecor.hitBetween(10f, 60f, 40f))
        // Ahead of the front: no hit.
        assertFalse(ThemePackDecor.hitBetween(500f, radNow, radPrev))
    }

    @Test
    fun `diya flare decays exponentially and lifts the glow`() {
        assertEquals(1f, ThemePackDecor.flareDecay(1f, 0f), 1e-6f)
        assertEquals(kotlin.math.exp(-3.2f), ThemePackDecor.flareDecay(1f, 1f), 1e-4f)
        // Glow alpha rises from the idle base toward ~0.72 at full flare.
        assertEquals(0.18f, ThemePackDecor.flareGlowAlpha(0.18f, 0f), 1e-6f)
        assertEquals(0.68f, ThemePackDecor.flareGlowAlpha(0.18f, 1f), 1e-6f)
        // Glow radius scales with the idle flicker plus the flare boost (mock: 1 + 1.1 flare).
        assertEquals(1f, ThemePackDecor.flareGlowScale(1f, 0f), 1e-6f)
        assertEquals(2.1f, ThemePackDecor.flareGlowScale(1f, 1f), 1e-6f)
        assertEquals(1.296f, ThemePackDecor.flareGlowScale(0.9f, 0.4f), 1e-4f)
    }

    @Test
    fun `rangoli scatter springs home and settles`() {
        // An impulse-scattered dot springs back: five simulated seconds land it at rest at home.
        var state = ThemePackDecor.ScatterState(10f, -6f, 80f, -50f)
        val dt = 1f / 30f
        repeat(150) {
            state = ThemePackDecor.scatterSpringStep(state.offsetX, state.offsetY, state.vx, state.vy, dt)
        }
        assertEquals(0f, state.offsetX, 0.1f)
        assertEquals(0f, state.offsetY, 0.1f)
        assertEquals(0f, state.vx, 0.1f)
        assertEquals(0f, state.vy, 0.1f)
    }

    @Test
    fun `rangoli scatter offset never leaves the clamp`() {
        // A violent impulse must stay clamped (belt-and-braces guard against runaway state).
        var state = ThemePackDecor.scatterSpringStep(0f, 0f, 5000f, -5000f, 1f / 30f)
        val offset = kotlin.math.hypot(state.offsetX, state.offsetY)
        assertTrue("offset $offset exceeded the clamp", offset <= ThemePackDecor.RANGOLI_SCATTER_MAX_DP + 1e-4f)
    }

    @Test
    fun `clock spring settles from a kick and never exceeds the clamp`() {
        // Kick of 120 deg/s, stepped at 30fps for five simulated seconds.
        var angle = 0f
        var vel = 120f
        var maxAbs = 0f
        val dt = 1f / 30f
        repeat(150) {
            val state = ThemePackDecor.clockSpringStep(angle, vel, dt)
            angle = state.angleDeg
            vel = state.angularVelDegS
            maxAbs = maxOf(maxAbs, kotlin.math.abs(angle))
            assertTrue("angle $angle exceeded the clamp", kotlin.math.abs(angle) <= ThemePackDecor.CLOCK_WOBBLE_MAX_DEG + 1e-4f)
        }
        assertTrue("did not settle, angle $angle", kotlin.math.abs(angle) < 0.1f)
        assertTrue("spring never moved (max $maxAbs)", maxAbs > 0.5f)
    }

    @Test
    fun `clock kick direction follows the blast side and lands in 70 to 120`() {
        // Clock left of the blast kicks negative (mock: cx < r.x ? -1 : 1).
        assertEquals(-100f, ThemePackDecor.clockKickDegS(100f, 200f, 0.6f), 1e-4f)
        assertEquals(100f, ThemePackDecor.clockKickDegS(200f, 100f, 0.6f), 1e-4f)
        // Roll 0..1 maps to magnitude 70..120, either sign.
        assertEquals(-70f, ThemePackDecor.clockKickDegS(0f, 10f, 0f), 1e-4f)
        assertEquals(-120f, ThemePackDecor.clockKickDegS(0f, 10f, 1f), 1e-4f)
        assertEquals(70f, ThemePackDecor.clockKickDegS(10f, 0f, 0f), 1e-4f)
        assertEquals(120f, ThemePackDecor.clockKickDegS(10f, 0f, 1f), 1e-4f)
    }

    @Test
    fun `ember gains gravity over one step`() {
        // From rest, 100ms of gravity 300dp/s^2: vy 30, y 3.
        val state = ThemePackDecor.emberStep(10f, 5f, 0f, 0f, 0.1f, floorY = 1000f)
        assertEquals(10f, state.x, 1e-4f)
        assertEquals(5f + 3f, state.y, 1e-4f)
        assertEquals(30f, state.vy, 1e-4f)
        assertEquals(0f, state.vx, 1e-4f)
    }

    @Test
    fun `ember bounces off the floor with restitution`() {
        // Fast fall: clamped to the floor, vy negated * 0.45, vx damped (mock stepEmbers).
        val state = ThemePackDecor.emberStep(0f, 1010f, 10f, 40f, 0.1f, floorY = 1000f)
        assertEquals(1000f, state.y, 1e-4f)
        assertEquals(-(40f + 300f * 0.1f) * 0.45f, state.vy, 1e-4f)
        assertEquals(8f, state.vx, 1e-4f)
    }

    @Test
    fun `slow embers settle on the floor instead of bouncing`() {
        // A bounce slower than the settle threshold parks the ember (vy 0) to roll out its life.
        val state = ThemePackDecor.emberStep(0f, 1000.5f, 10f, 5f, 0.1f, floorY = 1000f)
        assertEquals(1000f, state.y, 1e-4f)
        assertEquals(0f, state.vy, 1e-6f)
        assertEquals(8f, state.vx, 1e-4f)
    }

    @Test
    fun `ember alpha twinkles while fading out`() {
        // Born twinkling between 0.2 and 1, gone at end of life.
        val alpha = ThemePackDecor.emberAlpha(lifeS = 0f, ttlS = 3f, timeS = 0.1f, phase = 0f)
        assertTrue("alpha $alpha out of the twinkle band", alpha in 0.2f..1f)
        assertEquals(0f, ThemePackDecor.emberAlpha(3f, 3f, 0.1f, 0f), 1e-6f)
        assertEquals(0f, ThemePackDecor.emberAlpha(1f, 0f, 0.1f, 0f), 1e-6f)
    }

    @Test
    fun `ember count per detonation is 12 to 18 and capped stock is 90`() {
        assertEquals(12, ThemePackDecor.emberCountFor(0f))
        assertEquals(15, ThemePackDecor.emberCountFor(0.5f))
        assertEquals(18, ThemePackDecor.emberCountFor(1f))
        assertEquals(12, ThemePackDecor.emberCountFor(-2f))
        assertEquals(18, ThemePackDecor.emberCountFor(2f))
        assertEquals(90, ThemePackDecor.EMBER_CAP)
    }
}
