package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun rainIntensityPerCode() {
        listOf(51, 61, 80).forEach { assertEquals("code $it", Intensity.LIGHT, WeatherEffects.specFor(it).intensity) }
        listOf(53, 63, 81).forEach { assertEquals("code $it", Intensity.MODERATE, WeatherEffects.specFor(it).intensity) }
        listOf(55, 65, 82).forEach { assertEquals("code $it", Intensity.HEAVY, WeatherEffects.specFor(it).intensity) }
    }

    @Test
    fun freezingFlagOnlyForFreezingRainCodes() {
        listOf(56, 57, 66, 67).forEach { assertTrue("code $it", WeatherEffects.specFor(it).freezing) }
        listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95).forEach { assertFalse("code $it", WeatherEffects.specFor(it).freezing) }
        assertEquals(Intensity.LIGHT, WeatherEffects.specFor(56).intensity)
        assertEquals(Intensity.HEAVY, WeatherEffects.specFor(67).intensity)
    }

    @Test
    fun hailFlagOnlyFor96And99() {
        assertTrue(WeatherEffects.specFor(96).hail)
        assertTrue(WeatherEffects.specFor(99).hail)
        assertFalse(WeatherEffects.specFor(95).hail)
        assertFalse(WeatherEffects.specFor(65).hail)
    }

    @Test
    fun unknownCodeSpecIsNone() {
        assertEquals(WeatherEffect.NONE, WeatherEffects.specFor(-1).effect)
        assertEquals(WeatherEffect.NONE, WeatherEffects.specFor(42).effect)
        assertEquals(WeatherEffect.NONE, WeatherEffects.selectSpec(false, 63).effect)
        assertEquals(WeatherEffect.NONE, WeatherEffects.selectSpec(true, null).effect)
        assertEquals(Intensity.HEAVY, WeatherEffects.selectSpec(true, 65).intensity)
    }

    @Test
    fun forCodeMatchesSpec() {
        (-2..110).forEach { assertEquals("code $it", WeatherEffects.specFor(it).effect, WeatherEffects.forCode(it)) }
    }

    @Test
    fun snowIntensityPerCode() {
        assertEquals(Intensity.LIGHT, WeatherEffects.specFor(71).intensity)
        assertEquals(Intensity.MODERATE, WeatherEffects.specFor(73).intensity)
        assertEquals(Intensity.HEAVY, WeatherEffects.specFor(75).intensity)
    }

    @Test
    fun shouldAnimateNeedsHomeEffectAndAnimators() {
        assertTrue(WeatherEffects.shouldAnimate(true, WeatherEffect.RAIN, true))
        assertFalse(WeatherEffects.shouldAnimate(false, WeatherEffect.RAIN, true))
        assertFalse(WeatherEffects.shouldAnimate(true, WeatherEffect.NONE, true))
        assertFalse(WeatherEffects.shouldAnimate(true, WeatherEffect.SNOW, false))
    }
}
