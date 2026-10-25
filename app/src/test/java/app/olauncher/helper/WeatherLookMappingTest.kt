package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLookMappingTest {

    @Test
    fun clearCodesMapByDayAndNight() {
        assertEquals(WeatherLook.CLEAR, WeatherLooks.forForecast(0, isDay = true))
        assertEquals(WeatherLook.CLEAR_NIGHT, WeatherLooks.forForecast(0, isDay = false))
    }

    @Test
    fun mainlyClearMapsToPartlyByDayAndNightByNight() {
        assertEquals(WeatherLook.PARTLY, WeatherLooks.forForecast(1, isDay = true))
        assertEquals(WeatherLook.CLEAR_NIGHT, WeatherLooks.forForecast(1, isDay = false))
    }

    @Test
    fun partlyCloudyMapsToPartlyByDayAndOvercastByNight() {
        assertEquals(WeatherLook.PARTLY, WeatherLooks.forForecast(2, isDay = true))
        assertEquals(WeatherLook.OVERCAST, WeatherLooks.forForecast(2, isDay = false))
    }

    @Test
    fun overcastMapsToOvercastDayAndNight() {
        assertEquals(WeatherLook.OVERCAST, WeatherLooks.forForecast(3, isDay = true))
        assertEquals(WeatherLook.OVERCAST, WeatherLooks.forForecast(3, isDay = false))
    }

    @Test
    fun fogCodesMapToFogDayAndNight() {
        listOf(45, 48).forEach { code ->
            assertEquals("code $code by day", WeatherLook.FOG, WeatherLooks.forForecast(code, isDay = true))
            assertEquals("code $code by night", WeatherLook.FOG, WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun lightRainCodesMapToLightRainDayAndNight() {
        listOf(51, 53, 55, 56, 57, 61).forEach { code ->
            assertEquals("code $code by day", WeatherLook.LIGHT_RAIN, WeatherLooks.forForecast(code, isDay = true))
            assertEquals("code $code by night", WeatherLook.LIGHT_RAIN, WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun heavyRainCodesMapToHeavyRainDayAndNight() {
        listOf(63, 65, 66, 67, 80, 81, 82).forEach { code ->
            assertEquals("code $code by day", WeatherLook.HEAVY_RAIN, WeatherLooks.forForecast(code, isDay = true))
            assertEquals("code $code by night", WeatherLook.HEAVY_RAIN, WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun snowCodesMapToSnowDayAndNight() {
        listOf(71, 73, 75, 77, 85, 86).forEach { code ->
            assertEquals("code $code by day", WeatherLook.SNOW, WeatherLooks.forForecast(code, isDay = true))
            assertEquals("code $code by night", WeatherLook.SNOW, WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun stormCodesMapToStormDayAndNight() {
        listOf(95, 96, 99).forEach { code ->
            assertEquals("code $code by day", WeatherLook.STORM, WeatherLooks.forForecast(code, isDay = true))
            assertEquals("code $code by night", WeatherLook.STORM, WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun unknownCodesReturnNull() {
        listOf(-1, 4, 10, 100, 42, Int.MIN_VALUE, Int.MAX_VALUE).forEach { code ->
            assertNull("code $code by day", WeatherLooks.forForecast(code, isDay = true))
            assertNull("code $code by night", WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun everyCodeHandledByWeatherEffectsGetsNonNullLook() {
        val knownCodes = listOf(
            0, 1, 2, 3, 45, 48,
            51, 53, 55, 56, 57, 61,
            63, 65, 66, 67, 80, 81, 82,
            71, 73, 75, 77, 85, 86,
            95, 96, 99
        )
        for (code in knownCodes) {
            assertTrue("WeatherEffects specFor must handle $code", WeatherEffects.specFor(code).effect != WeatherEffect.NONE)
            assertNotNull("WeatherLooks must have look for code $code by day", WeatherLooks.forForecast(code, isDay = true))
            assertNotNull("WeatherLooks must have look for code $code by night", WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun eachHandledCodeMatchesMockLookForForecastDayAndNight() {
        val expectedDay = mapOf(
            0 to WeatherLook.CLEAR,
            1 to WeatherLook.PARTLY,
            2 to WeatherLook.PARTLY,
            3 to WeatherLook.OVERCAST,
            45 to WeatherLook.FOG, 48 to WeatherLook.FOG,
            51 to WeatherLook.LIGHT_RAIN, 53 to WeatherLook.LIGHT_RAIN, 55 to WeatherLook.LIGHT_RAIN,
            56 to WeatherLook.LIGHT_RAIN, 57 to WeatherLook.LIGHT_RAIN, 61 to WeatherLook.LIGHT_RAIN,
            63 to WeatherLook.HEAVY_RAIN, 65 to WeatherLook.HEAVY_RAIN, 66 to WeatherLook.HEAVY_RAIN,
            67 to WeatherLook.HEAVY_RAIN, 80 to WeatherLook.HEAVY_RAIN, 81 to WeatherLook.HEAVY_RAIN, 82 to WeatherLook.HEAVY_RAIN,
            71 to WeatherLook.SNOW, 73 to WeatherLook.SNOW, 75 to WeatherLook.SNOW, 77 to WeatherLook.SNOW,
            85 to WeatherLook.SNOW, 86 to WeatherLook.SNOW,
            95 to WeatherLook.STORM, 96 to WeatherLook.STORM, 99 to WeatherLook.STORM,
        )

        val expectedNight = mapOf(
            0 to WeatherLook.CLEAR_NIGHT,
            1 to WeatherLook.CLEAR_NIGHT,
            2 to WeatherLook.OVERCAST,
            3 to WeatherLook.OVERCAST,
            45 to WeatherLook.FOG, 48 to WeatherLook.FOG,
            51 to WeatherLook.LIGHT_RAIN, 53 to WeatherLook.LIGHT_RAIN, 55 to WeatherLook.LIGHT_RAIN,
            56 to WeatherLook.LIGHT_RAIN, 57 to WeatherLook.LIGHT_RAIN, 61 to WeatherLook.LIGHT_RAIN,
            63 to WeatherLook.HEAVY_RAIN, 65 to WeatherLook.HEAVY_RAIN, 66 to WeatherLook.HEAVY_RAIN,
            67 to WeatherLook.HEAVY_RAIN, 80 to WeatherLook.HEAVY_RAIN, 81 to WeatherLook.HEAVY_RAIN, 82 to WeatherLook.HEAVY_RAIN,
            71 to WeatherLook.SNOW, 73 to WeatherLook.SNOW, 75 to WeatherLook.SNOW, 77 to WeatherLook.SNOW,
            85 to WeatherLook.SNOW, 86 to WeatherLook.SNOW,
            95 to WeatherLook.STORM, 96 to WeatherLook.STORM, 99 to WeatherLook.STORM,
        )

        for ((code, look) in expectedDay) {
            assertEquals("code $code by day per mock", look, WeatherLooks.forForecast(code, isDay = true))
        }

        for ((code, look) in expectedNight) {
            assertEquals("code $code by night per mock", look, WeatherLooks.forForecast(code, isDay = false))
        }
    }

    @Test
    fun differentCodeGivesDifferentLookOnNextCallWithoutManualSelection() {
        assertEquals(WeatherLook.CLEAR, WeatherLooks.forForecast(0, isDay = true))
        assertEquals(WeatherLook.OVERCAST, WeatherLooks.forForecast(3, isDay = true))
        assertEquals(WeatherLook.LIGHT_RAIN, WeatherLooks.forForecast(61, isDay = true))
        assertEquals(WeatherLook.HEAVY_RAIN, WeatherLooks.forForecast(65, isDay = true))
        assertEquals(WeatherLook.SNOW, WeatherLooks.forForecast(71, isDay = true))
        assertEquals(WeatherLook.STORM, WeatherLooks.forForecast(95, isDay = true))
        assertEquals(WeatherLook.CLEAR_NIGHT, WeatherLooks.forForecast(0, isDay = false))
        assertEquals(WeatherLook.FOG, WeatherLooks.forForecast(45, isDay = true))
        assertNull(WeatherLooks.forForecast(999, isDay = true))
    }
}
