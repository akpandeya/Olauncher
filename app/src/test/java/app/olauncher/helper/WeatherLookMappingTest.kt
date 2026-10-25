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
}
