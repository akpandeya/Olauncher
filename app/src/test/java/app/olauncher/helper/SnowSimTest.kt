package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class SnowSimTest {

    @Test
    fun flakeCountMatchesDensityForSnowCodes() {
        val cases = mapOf(
            71 to 0.3f,
            73 to 0.6f,
            75 to 0.9f,
            77 to 0.9f,
            85 to 0.9f,
            86 to 0.9f,
        )

        for ((code, expectedDensity) in cases) {
            val expectedFlakes = (expectedDensity * WeatherSim.FLAKES_AT_FULL_DENSITY).roundToInt()
            val simDay = WeatherSim.createForForecast(
                code = code,
                isDay = true,
                width = 390f,
                height = 844f,
                seed = 100L,
            )
            assertNotNull("sim for code $code must not be null", simDay)
            assertEquals("Flake count for code $code (day)", expectedFlakes, simDay!!.flakes.size)

            val simNight = WeatherSim.createForForecast(
                code = code,
                isDay = false,
                width = 390f,
                height = 844f,
                seed = 100L,
            )
            assertNotNull("sim for code $code must not be null", simNight)
            assertEquals("Flake count for code $code (night)", expectedFlakes, simNight!!.flakes.size)
        }
    }

    @Test
    fun settledFlakesNeverExceed40AndFlakesWrapSideways() {
        val sim = WeatherSim.createForForecast(
            code = 75,
            isDay = true,
            width = 390f,
            height = 844f,
            seed = 4242L,
        )!!

        // Run for 60 seconds (1800 steps at 1/30s)
        for (step in 0 until 1800) {
            sim.update(WeatherSim.STEP)
            val settledCount = sim.flakes.count { it.settled }
            assertTrue(
                "Settled flakes ($settledCount) must never exceed ${WeatherSim.MAX_SETTLED}",
                settledCount <= WeatherSim.MAX_SETTLED
            )
            for (f in sim.flakes) {
                assertTrue(
                    "Flake x (${f.x}) must wrap and stay on glass [0, ${sim.width}]",
                    f.x >= 0f && f.x <= sim.width
                )
            }
        }
    }
}
