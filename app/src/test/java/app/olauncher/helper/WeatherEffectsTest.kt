package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherEffectsTest {

    @Test
    fun clearCodesGiveSun() {
        assertEquals(WeatherEffect.SUN, WeatherEffects.forCode(0))
        assertEquals(WeatherEffect.SUN, WeatherEffects.forCode(1))
    }

    @Test
    fun cloudyCodesGiveClouds() {
        assertEquals(WeatherEffect.CLOUDS, WeatherEffects.forCode(2))
        assertEquals(WeatherEffect.CLOUDS, WeatherEffects.forCode(3))
    }

    @Test
    fun fogCodesGiveFog() {
        assertEquals(WeatherEffect.FOG, WeatherEffects.forCode(45))
        assertEquals(WeatherEffect.FOG, WeatherEffects.forCode(48))
    }

    @Test
    fun rainDrizzleAndShowersGiveRain() {
        listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82).forEach { code ->
            assertEquals("code $code", WeatherEffect.RAIN, WeatherEffects.forCode(code))
        }
    }

    @Test
    fun snowCodesGiveSnow() {
        listOf(71, 73, 75, 77, 85, 86).forEach { code ->
            assertEquals("code $code", WeatherEffect.SNOW, WeatherEffects.forCode(code))
        }
    }

    @Test
    fun thunderstormGivesStorm() {
        listOf(95, 96, 99).forEach { code ->
            assertEquals("code $code", WeatherEffect.STORM, WeatherEffects.forCode(code))
        }
    }

    @Test
    fun unknownCodeGivesNone() {
        assertEquals(WeatherEffect.NONE, WeatherEffects.forCode(-1))
        assertEquals(WeatherEffect.NONE, WeatherEffects.forCode(42))
    }

    @Test
    fun everyKnownWmoCodeMapsToAnEffect() {
        val known = listOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)
        known.forEach { code ->
            assertTrue("code $code", WeatherEffects.forCode(code) != WeatherEffect.NONE)
        }
    }

    @Test
    fun offOrMissingCodeSelectsNone() {
        assertEquals(WeatherEffect.NONE, WeatherEffects.select(enabled = false, code = 63))
        assertEquals(WeatherEffect.NONE, WeatherEffects.select(enabled = true, code = null))
        assertEquals(WeatherEffect.RAIN, WeatherEffects.select(enabled = true, code = 63))
    }

    @Test
    fun dayAndNightTintsDiffer() {
        WeatherEffect.values().filter { it != WeatherEffect.NONE }.forEach { effect ->
            val day = WeatherEffects.tint(effect, isDay = true)
            val night = WeatherEffects.tint(effect, isDay = false)
            assertNotEquals("$effect day vs night", day.primary, night.primary)
        }
    }

    @Test
    fun stormHasDistinctFlashColour() {
        val storm = WeatherEffects.tint(WeatherEffect.STORM, isDay = true)
        assertNotEquals(storm.primary, storm.secondary)
        val rain = WeatherEffects.tint(WeatherEffect.RAIN, isDay = true)
        assertEquals(rain.primary, rain.secondary)
    }

    @Test
    fun frameIntervalCapsAtThirtyFps() {
        assertEquals(30, WeatherEffects.MAX_FPS)
        assertTrue(WeatherEffects.FRAME_INTERVAL_MS >= 33L)
        assertEquals(33L, WeatherEffects.FRAME_INTERVAL_MS)
    }
}
