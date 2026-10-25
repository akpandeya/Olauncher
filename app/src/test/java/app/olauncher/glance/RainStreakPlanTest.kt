package app.olauncher.glance

import app.olauncher.helper.WeatherLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RainStreakPlanTest {

    private val nonRainLooks = listOf(
        WeatherLook.CLEAR,
        WeatherLook.PARTLY,
        WeatherLook.OVERCAST,
        WeatherLook.SNOW,
        WeatherLook.FOG,
        WeatherLook.CLEAR_NIGHT,
    )

    private val rainLooks = listOf(
        WeatherLook.LIGHT_RAIN,
        WeatherLook.HEAVY_RAIN,
        WeatherLook.STORM,
    )

    private val testSizes = listOf(
        320f to 200f,
        412f to 260f,
        800f to 600f, // large size
    )

    @Test
    fun `no streaks on the six non-rain looks`() {
        for (look in nonRainLooks) {
            val plan = RainStreakPlan.plan(look, 360f, 200f, 42L)
            assertEquals("Look $look must produce 0 total streaks", 0, plan.streaks)
            assertEquals("Look $look far layer count must be 0", 0, plan.far.count)
            assertEquals("Look $look mid layer count must be 0", 0, plan.mid.count)
            assertEquals("Look $look near layer count must be 0", 0, plan.near.count)
            assertTrue("Look $look far streaks must be empty", plan.far.streaks.isEmpty())
            assertTrue("Look $look mid streaks must be empty", plan.mid.streaks.isEmpty())
            assertTrue("Look $look near streaks must be empty", plan.near.streaks.isEmpty())
            assertTrue("Look $look ripples must be empty", plan.ripples.isEmpty())
            assertEquals(0f, plan.travel, 0.001f)
            assertEquals(0f, plan.fall, 0.001f)
            assertEquals(0f, plan.far.dx, 0.001f)
            assertEquals(0f, plan.far.tilt, 0.001f)
        }
    }

    @Test
    fun `null look or invalid dimensions give empty plan`() {
        val nullPlan = RainStreakPlan.plan(null, 360f, 200f, 42L)
        assertEquals(0, nullPlan.streaks)
        assertTrue(nullPlan.ripples.isEmpty())

        val zeroWidthPlan = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 0f, 200f, 42L)
        assertEquals(0, zeroWidthPlan.streaks)

        val zeroHeightPlan = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 360f, 0f, 42L)
        assertEquals(0, zeroHeightPlan.streaks)

        val negativeWidthPlan = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, -10f, 200f, 42L)
        assertEquals(0, negativeWidthPlan.streaks)

        val negativeHeightPlan = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 360f, -20f, 42L)
        assertEquals(0, negativeHeightPlan.streaks)
    }

    @Test
    fun `total streaks leq 90 for every rain look at all test sizes`() {
        for (look in rainLooks) {
            for ((w, h) in testSizes) {
                val plan = RainStreakPlan.plan(look, w, h, 42L)
                assertTrue("Look $look at ${w}x$h streaks ${plan.streaks} must be <= 90", plan.streaks <= 90)
                assertEquals(
                    "Total streaks must equal sum of layer counts",
                    plan.streaks,
                    plan.far.count + plan.mid.count + plan.near.count,
                )
                assertEquals(plan.far.count, plan.far.streaks.size)
                assertEquals(plan.mid.count, plan.mid.streaks.size)
                assertEquals(plan.near.count, plan.near.streaks.size)
            }
        }
    }

    @Test
    fun `near layer longer, faster and more opaque than far`() {
        for (look in rainLooks) {
            val plan = RainStreakPlan.plan(look, 360f, 200f, 42L)

            // Length: near (40) > far (12)
            assertTrue("Near length must exceed far length", plan.near.length > plan.far.length)
            assertEquals(40f, plan.near.length, 0.001f)
            assertEquals(12f, plan.far.length, 0.001f)

            // Width: near (2) > far (1)
            assertTrue("Near width must exceed far width", plan.near.width > plan.far.width)
            assertEquals(2f, plan.near.width, 0.001f)
            assertEquals(1f, plan.far.width, 0.001f)

            // Opacity and opacity rank: near > far
            assertTrue("Near opacity must exceed far opacity", plan.near.opacity > plan.far.opacity)
            assertTrue("Near opacity rank must exceed far opacity rank", plan.near.opacityRank > plan.far.opacityRank)

            // Speed: travel / mean dur. Near must be faster than far (mean dur near < mean dur far)
            val farMeanDur = plan.far.streaks.map { it.dur }.average()
            val nearMeanDur = plan.near.streaks.map { it.dur }.average()
            val farSpeed = plan.travel / farMeanDur
            val nearSpeed = plan.travel / nearMeanDur

            assertTrue("Near layer speed ($nearSpeed) must be faster than far layer speed ($farSpeed)", nearSpeed > farSpeed)
            assertTrue("Near mean duration ($nearMeanDur) must be shorter than far mean duration ($farMeanDur)", nearMeanDur < farMeanDur)
        }
    }

    @Test
    fun `dx and tilt 0 on wind-0 looks and follow look wind on rain looks`() {
        // Wind-0 look (CLEAR_NIGHT has wind 0f)
        val clearNightWind = rainWind(WeatherLook.CLEAR_NIGHT, 1f, 340f)
        assertEquals(0f, clearNightWind.far.dx, 0.001f)
        assertEquals(0f, clearNightWind.far.tilt, 0.001f)
        assertEquals(0f, clearNightWind.mid.dx, 0.001f)
        assertEquals(0f, clearNightWind.mid.tilt, 0.001f)
        assertEquals(0f, clearNightWind.near.dx, 0.001f)
        assertEquals(0f, clearNightWind.near.tilt, 0.001f)

        // Non-rain plan dx and tilt are 0
        val clearNightPlan = RainStreakPlan.plan(WeatherLook.CLEAR_NIGHT, 360f, 200f, 42L)
        assertEquals(0f, clearNightPlan.far.dx, 0.001f)
        assertEquals(0f, clearNightPlan.far.tilt, 0.001f)

        // Light rain: wind = 0.5
        val lightPlan = RainStreakPlan.plan(WeatherLook.LIGHT_RAIN, 360f, 200f, 42L)
        assertEquals(-55f, lightPlan.far.dx, 0.001f)
        assertEquals(-67.5f, lightPlan.mid.dx, 0.001f)
        assertEquals(-87.5f, lightPlan.near.dx, 0.001f)
        assertEquals(9.19f, lightPlan.far.tilt, 0.001f)
        assertEquals(11.23f, lightPlan.mid.tilt, 0.001f)
        assertEquals(14.43f, lightPlan.near.tilt, 0.001f)

        // Heavy rain: wind = 1.0
        val heavyPlan = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 360f, 200f, 42L)
        assertEquals(-110f, heavyPlan.far.dx, 0.001f)
        assertEquals(-135f, heavyPlan.mid.dx, 0.001f)
        assertEquals(-175f, heavyPlan.near.dx, 0.001f)
        assertEquals(17.93f, heavyPlan.far.tilt, 0.001f)
        assertEquals(21.66f, heavyPlan.mid.tilt, 0.001f)
        assertEquals(27.24f, heavyPlan.near.tilt, 0.001f)
    }

    @Test
    fun `heavy rain slant greater than light rain slant`() {
        val lightPlan = RainStreakPlan.plan(WeatherLook.LIGHT_RAIN, 360f, 200f, 42L)
        val heavyPlan = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 360f, 200f, 42L)

        assertTrue(
            "Heavy far tilt (${heavyPlan.far.tilt}) must be > light far tilt (${lightPlan.far.tilt})",
            heavyPlan.far.tilt > lightPlan.far.tilt,
        )
        assertTrue(
            "Heavy mid tilt (${heavyPlan.mid.tilt}) must be > light mid tilt (${lightPlan.mid.tilt})",
            heavyPlan.mid.tilt > lightPlan.mid.tilt,
        )
        assertTrue(
            "Heavy near tilt (${heavyPlan.near.tilt}) must be > light near tilt (${lightPlan.near.tilt})",
            heavyPlan.near.tilt > lightPlan.near.tilt,
        )
    }

    @Test
    fun `same seed produces equal plan, different seed produces different positions`() {
        val planA1 = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 360f, 200f, 12345L)
        val planA2 = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 360f, 200f, 12345L)
        assertEquals(planA1, planA2)

        val planB = RainStreakPlan.plan(WeatherLook.HEAVY_RAIN, 360f, 200f, 98765L)
        assertNotEquals(planA1, planB)
        assertNotEquals(
            planA1.far.streaks.map { it.left },
            planB.far.streaks.map { it.left },
        )
    }

    @Test
    fun `ripple count leq 10 and every ripple within mock ranges`() {
        for (look in rainLooks) {
            for ((w, h) in testSizes) {
                val plan = RainStreakPlan.plan(look, w, h, 42L)
                assertTrue("Ripple count ${plan.ripples.size} must be <= 10", plan.ripples.size <= 10)

                val expectedDur = round2(1.2 * Math.pow(look.params.fallSpeed.toDouble(), -0.36))

                for (ripple in plan.ripples) {
                    assertEquals("Ripple dur must match 1.2 * fallSpeed^-0.36", expectedDur, ripple.dur, 0.001f)
                    assertTrue("Ripple left (${ripple.left}) must be in 5..95 %", ripple.left in 5f..95f)
                    assertTrue("Ripple bottom (${ripple.bottom}) must be in 2..10 dp", ripple.bottom in 2f..10f)
                    assertTrue("Ripple delay (${ripple.delay}) must be <= 0", ripple.delay <= 0f)
                }
            }
        }
    }

    @Test
    fun `every streak delay is leq 0 and left is within -5 to 115`() {
        for (look in rainLooks) {
            for ((w, h) in testSizes) {
                val plan = RainStreakPlan.plan(look, w, h, 42L)
                val allStreaks = plan.far.streaks + plan.mid.streaks + plan.near.streaks
                for (streak in allStreaks) {
                    assertTrue("Streak delay (${streak.delay}) must be <= 0", streak.delay <= 0f)
                    assertTrue("Streak left (${streak.left}) must be in -5..115", streak.left in -5f..115f)
                    assertTrue("Streak dur (${streak.dur}) must be > 0", streak.dur > 0f)
                }
            }
        }
    }

    @Test
    fun `mock reference case matches node values exactly`() {
        val plan = RainStreakPlan.plan(WeatherLook.LIGHT_RAIN, 360f, 200f, 42L)
        assertEquals(340f, plan.travel, 0.001f)
        assertEquals(280f, plan.fall, 0.001f)
        assertEquals(33, plan.streaks)
        assertEquals(18, plan.far.count)
        assertEquals(10, plan.mid.count)
        assertEquals(5, plan.near.count)
        assertEquals(4, plan.ripples.size)

        // First far streak from mock: { left: 48.79, dur: 2.29, delay: -1.95 }
        val firstFar = plan.far.streaks[0]
        assertEquals(48.79f, firstFar.left, 0.001f)
        assertEquals(2.29f, firstFar.dur, 0.001f)
        assertEquals(-1.95f, firstFar.delay, 0.001f)

        // First ripple from mock: { left: 47.79, bottom: 9, dur: 1.2, delay: -0.16 }
        val firstRipple = plan.ripples[0]
        assertEquals(47.79f, firstRipple.left, 0.001f)
        assertEquals(9f, firstRipple.bottom, 0.001f)
        assertEquals(1.2f, firstRipple.dur, 0.001f)
        assertEquals(-0.16f, firstRipple.delay, 0.001f)
    }

    @Test
    fun `constants match mock specification`() {
        assertEquals(90, MAX_STREAKS)
        assertEquals(10, MAX_RIPPLES)
        assertEquals(0.7f, RAIN_OPACITY, 0.001f)
        assertEquals(360f * 200f, RAIN_REF_AREA, 0.001f)
        assertEquals(350f, RAIN_TRAVEL_REF, 0.001f)
        assertEquals(80f, RAIN_EXTRA_FALL, 0.001f)

        assertEquals(50, RAIN_LAYERS_FAR.max)
        assertEquals(2.3f, RAIN_LAYERS_FAR.duration, 0.001f)
        assertEquals(0.3f, RAIN_LAYERS_FAR.jitter, 0.001f)
        assertEquals(110f, RAIN_LAYERS_FAR.maxDrift, 0.001f)
        assertEquals(1f, RAIN_LAYERS_FAR.width, 0.001f)
        assertEquals(12f, RAIN_LAYERS_FAR.length, 0.001f)
        assertEquals(0.40f, RAIN_LAYERS_FAR.opacity, 0.001f)

        assertEquals(28, RAIN_LAYERS_MID.max)
        assertEquals(1.35f, RAIN_LAYERS_MID.duration, 0.001f)
        assertEquals(0.08f, RAIN_LAYERS_MID.jitter, 0.001f)
        assertEquals(135f, RAIN_LAYERS_MID.maxDrift, 0.001f)
        assertEquals(1.5f, RAIN_LAYERS_MID.width, 0.001f)
        assertEquals(22f, RAIN_LAYERS_MID.length, 0.001f)
        assertEquals(0.70f, RAIN_LAYERS_MID.opacity, 0.001f)

        assertEquals(13, RAIN_LAYERS_NEAR.max)
        assertEquals(0.72f, RAIN_LAYERS_NEAR.duration, 0.001f)
        assertEquals(0.07f, RAIN_LAYERS_NEAR.jitter, 0.001f)
        assertEquals(175f, RAIN_LAYERS_NEAR.maxDrift, 0.001f)
        assertEquals(2f, RAIN_LAYERS_NEAR.width, 0.001f)
        assertEquals(40f, RAIN_LAYERS_NEAR.length, 0.001f)
        assertEquals(0.90f, RAIN_LAYERS_NEAR.opacity, 0.001f)
    }
}
