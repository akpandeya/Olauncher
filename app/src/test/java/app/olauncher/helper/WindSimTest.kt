package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WindSimTest {

    private val delta = 1e-4f

    @Test
    fun effectiveWindEqualsLookWind() {
        for (look in WeatherLook.values()) {
            val sim = WeatherSim(
                look = look,
                width = 390f,
                height = 844f,
                seed = 100L,
            )
            assertEquals("Wind for $look must equal params.wind", look.params.wind, sim.wind, delta)
        }
    }

    @Test
    fun stormSidewaysDropSpeedNeverExceeds92Point5AndDropsStayOnGlass() {
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 42L,
        )

        // 60 seconds of simulation (1800 steps at 1/30s)
        val maxSidewaysLimit = WeatherSim.MAX_DRIFT + WeatherSim.MAX_WOBBLE // 70 + 22.5 = 92.5
        for (step in 0 until 1800) {
            sim.update(WeatherSim.STEP)
            for (d in sim.drops) {
                val speed = abs(d.sideways)
                assertTrue(
                    "Sideways drop speed ($speed px/s) must never exceed $maxSidewaysLimit px/s",
                    speed <= maxSidewaysLimit + 1e-3f
                )
                assertTrue(
                    "Drop x (${d.x}) must stay inside [r (${d.r}), width - r (${sim.width - d.r})]",
                    d.x >= d.r - 1e-3f && d.x <= sim.width - d.r + 1e-3f
                )
            }
        }
    }

    @Test
    fun clearNightNeverProducesAGust() {
        val sim = WeatherSim(
            look = WeatherLook.CLEAR_NIGHT,
            width = 390f,
            height = 844f,
            seed = 123L,
        )

        assertEquals(0f, sim.wind, delta)
        assertEquals(0f, sim.params.gustiness, delta)

        // Run for 60 seconds
        for (step in 0 until 1800) {
            sim.update(WeatherSim.STEP)
            assertNull("CLEAR_NIGHT must never produce a gust", sim.gust)
            assertEquals("Local wind must be 0", 0f, sim.localWind(100f), delta)
        }
    }

    @Test
    fun localWindNeverAbove1() {
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 555L,
        )

        // Run for 60 seconds and test sample points across the glass width
        val samplePoints = listOf(0f, 50f, 100f, 195f, 300f, 390f)
        for (step in 0 until 1800) {
            sim.update(WeatherSim.STEP)
            for (x in samplePoints) {
                val lw = sim.localWind(x)
                assertTrue("Local wind ($lw) at x=$x must never exceed 1.0", lw <= 1.0f + 1e-6f)
                assertTrue("Local wind ($lw) at x=$x must be >= 0", lw >= 0f)
            }
        }
    }
}
