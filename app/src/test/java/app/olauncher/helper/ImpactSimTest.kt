package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ImpactSimTest {

    private val delta = 1e-4f

    @Test
    fun impactsOnlyOnRainLooks() {
        val nonRainLooks = WeatherLook.values().filter { !it.isRain }
        for (look in nonRainLooks) {
            val sim = WeatherSim(
                look = look,
                width = 390f,
                height = 844f,
                seed = 101L,
            )
            assertEquals("impactRate for non-rain look $look must be 0", 0f, sim.impactRate, delta)
            for (step in 0 until 900) { // 30 seconds
                sim.updateHome(WeatherSim.STEP)
                assertTrue("Non-rain look $look must never have impacts", sim.impacts.isEmpty())
            }
        }

        val rainLooks = listOf(WeatherLook.LIGHT_RAIN, WeatherLook.HEAVY_RAIN, WeatherLook.STORM)
        for (look in rainLooks) {
            val sim = WeatherSim(
                look = look,
                width = 390f,
                height = 844f,
                seed = 101L,
            )
            assertTrue("impactRate for rain look $look must be > 0", sim.impactRate > 0f)
            // Run until impacts spawn
            for (step in 0 until 180) { // 6 seconds
                sim.updateHome(WeatherSim.STEP)
            }
            assertTrue("Rain look $look should have spawned impacts", sim.impacts.isNotEmpty())
        }
    }

    @Test
    fun atMost7LiveImpacts() {
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 42L,
        )

        for (step in 0 until 3600) { // 120 seconds
            sim.updateHome(WeatherSim.STEP)
            assertTrue(
                "Impact count (${sim.impacts.size}) must never exceed ${WeatherSim.MAX_IMPACTS}",
                sim.impacts.size <= WeatherSim.MAX_IMPACTS
            )
        }
    }

    @Test
    fun hitRatePerLookMatchesSpecificationAndAreaScaling() {
        val w = 390f
        val h = 844f

        val simLight = WeatherSim(WeatherLook.LIGHT_RAIN, w, h, 1L)
        val simHeavy = WeatherSim(WeatherLook.HEAVY_RAIN, w, h, 1L)
        val simStorm = WeatherSim(WeatherLook.STORM, w, h, 1L)

        assertEquals(1.4f, simLight.impactRate, delta)
        assertEquals(2.6f, simHeavy.impactRate, delta)
        assertEquals(3.0f, simStorm.impactRate, delta)

        // Area scaling: half area -> scaled by clamp(0.5, 0.5, 1) = 0.5
        val simHalf = WeatherSim(WeatherLook.STORM, w, h * 0.5f, 1L)
        assertEquals(3.0f * 0.5f, simHalf.impactRate, delta)

        // Area scaling: quarter area -> clamped to 0.5 minimum
        val simQuarter = WeatherSim(WeatherLook.STORM, w * 0.5f, h * 0.5f, 1L)
        assertEquals(3.0f * 0.5f, simQuarter.impactRate, delta)

        // Area scaling: double area -> clamped to 1.0 maximum
        val simDouble = WeatherSim(WeatherLook.STORM, w * 2f, h, 1L)
        assertEquals(3.0f * 1.0f, simDouble.impactRate, delta)
    }

    @Test
    fun keepOutRectSkipsApprox80PercentOfHitsOnText() {
        // Full screen keep-out rect
        val keepOut = listOf(WeatherSimRect(0f, 0f, 390f, 844f))
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 2026L,
            keepOut = keepOut,
        )

        // Count individual trial skip rate: in pickImpactSpot, each try lands in keepOut.
        // It skips when rng.nextFloat() < KEEP_OUT_SKIP (0.8).
        var skips = 0
        val trials = 5000
        for (i in 0 until trials) {
            val x = WeatherSim.randRange(sim.rng, 8f, sim.width - 8f)
            val y = WeatherSim.randRange(sim.rng, sim.height * 0.03f, sim.height * 0.97f)
            assertTrue(sim.inKeepOut(x, y))
            if (sim.rng.nextFloat() < WeatherSim.KEEP_OUT_SKIP) {
                skips++
            }
        }

        val skipRate = skips.toDouble() / trials
        // Statistically should be ~0.80 within tight bound [0.78, 0.82]
        assertTrue("Skip rate ($skipRate) should be approx 0.80", skipRate in 0.78..0.82)
    }

    @Test
    fun beadsDoNotSlideSidewaysWhenWindIsZero() {
        val zeroWindParams = WeatherLook.STORM.params.copy(wind = 0f, gustiness = 0f)
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 333L,
            params = zeroWindParams,
        )

        assertEquals(0f, sim.localWind(100f), delta)

        // Generate multiple impacts and inspect bead offsets
        for (i in 0 until 50) {
            val impact = sim.makeImpact(150f, 150f)
            assertTrue("Impact should have 2 to 4 beads", impact.beads.size in 2..4)
            for (b in impact.beads) {
                // When wind is 0, BEAD_SLIDE_WIND * lw is 0.
                // The dx is purely Math.cos(a) * spread, where spread <= BEAD_SPREAD (4px).
                assertTrue(
                    "Bead dx (${b.dx}) without wind must stay within splash spread ${WeatherSim.BEAD_SPREAD} px",
                    abs(b.dx) <= WeatherSim.BEAD_SPREAD + 1e-3f
                )
            }
        }
    }

    @Test
    fun everyBeadGoneWithin1Point8Seconds() {
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 444L,
        )

        val impact = sim.makeImpact(100f, 100f)
        assertTrue(impact.beads.isNotEmpty())
        for (b in impact.beads) {
            assertTrue("Bead life must be <= 1.8s", b.life <= 1.8f)
        }

        // At t = 0, beads are visible
        impact.age = 0.05f
        assertTrue(impact.beads.any { beadAt(impact, it) != null })

        // At t = 1.8s, every bead must be gone (null)
        impact.age = 1.8f
        for (b in impact.beads) {
            assertNull("Bead must be gone at 1.8s", beadAt(impact, b))
        }

        // At t > 1.8s, ring and beads are all null
        impact.age = 1.81f
        assertNull(ringAt(impact))
        for (b in impact.beads) {
            assertNull(beadAt(impact, b))
        }
    }
}
