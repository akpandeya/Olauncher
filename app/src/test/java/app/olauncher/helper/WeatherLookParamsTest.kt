package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLookParamsTest {

    private val delta = 1e-6f

    @Test
    fun clearParamsMatchTable() {
        val p = WeatherLook.CLEAR.params
        assertEquals(0f, p.density, delta)
        assertEquals(0.2f, p.fallSpeed, delta)
        assertEquals(0f, p.glassDrops, delta)
        assertEquals(0f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(0.3f, p.wind, delta)
        assertEquals(0f, p.gustiness, delta)
        assertEquals(1f, p.sun, delta)
        assertEquals(0f, p.clouds, delta)
        assertEquals(0, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun partlyParamsMatchTable() {
        val p = WeatherLook.PARTLY.params
        assertEquals(0f, p.density, delta)
        assertEquals(0.2f, p.fallSpeed, delta)
        assertEquals(0f, p.glassDrops, delta)
        assertEquals(0f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(0.6f, p.wind, delta)
        assertEquals(0.3f, p.gustiness, delta)
        assertEquals(0.6f, p.sun, delta)
        assertEquals(0.45f, p.clouds, delta)
        assertEquals(1, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun overcastParamsMatchTable() {
        val p = WeatherLook.OVERCAST.params
        assertEquals(0f, p.density, delta)
        assertEquals(0.2f, p.fallSpeed, delta)
        assertEquals(0f, p.glassDrops, delta)
        assertEquals(0f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(0.4f, p.wind, delta)
        assertEquals(0.2f, p.gustiness, delta)
        assertEquals(0f, p.sun, delta)
        assertEquals(1f, p.clouds, delta)
        assertEquals(2, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun lightRainParamsMatchTable() {
        val p = WeatherLook.LIGHT_RAIN.params
        assertEquals(0.35f, p.density, delta)
        assertEquals(1.0f, p.fallSpeed, delta)
        assertEquals(6f, p.glassDrops, delta)
        assertEquals(0.3f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(0.5f, p.wind, delta)
        assertEquals(0.5f, p.gustiness, delta)
        assertEquals(0f, p.sun, delta)
        assertEquals(0f, p.clouds, delta)
        assertEquals(0, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun heavyRainParamsMatchTable() {
        val p = WeatherLook.HEAVY_RAIN.params
        assertEquals(0.9f, p.density, delta)
        assertEquals(2.2f, p.fallSpeed, delta)
        assertEquals(26f, p.glassDrops, delta)
        assertEquals(1.0f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(1f, p.wind, delta)
        assertEquals(0.8f, p.gustiness, delta)
        assertEquals(0f, p.sun, delta)
        assertEquals(0f, p.clouds, delta)
        assertEquals(0, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun stormParamsMatchTable() {
        val p = WeatherLook.STORM.params
        assertEquals(0.95f, p.density, delta)
        assertEquals(2.5f, p.fallSpeed, delta)
        assertEquals(30f, p.glassDrops, delta)
        assertEquals(1.0f, p.slideSpeed, delta)
        assertEquals(1f, p.bolt, delta)
        assertEquals(1f, p.wind, delta)
        assertEquals(1f, p.gustiness, delta)
        assertEquals(0f, p.sun, delta)
        assertEquals(0f, p.clouds, delta)
        assertEquals(0, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun snowParamsMatchTable() {
        val p = WeatherLook.SNOW.params
        assertEquals(0.6f, p.density, delta)
        assertEquals(0.5f, p.fallSpeed, delta)
        assertEquals(0f, p.glassDrops, delta)
        assertEquals(0f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(0.8f, p.wind, delta)
        assertEquals(0.6f, p.gustiness, delta)
        assertEquals(0f, p.sun, delta)
        assertEquals(0f, p.clouds, delta)
        assertEquals(0, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertTrue(p.snow)
    }

    @Test
    fun fogParamsMatchTable() {
        val p = WeatherLook.FOG.params
        assertEquals(0f, p.density, delta)
        assertEquals(0.2f, p.fallSpeed, delta)
        assertEquals(0f, p.glassDrops, delta)
        assertEquals(0f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(0.2f, p.wind, delta)
        assertEquals(0.1f, p.gustiness, delta)
        assertEquals(0f, p.sun, delta)
        assertEquals(0f, p.clouds, delta)
        assertEquals(0, p.cloudLayers)
        assertEquals(1f, p.fog, delta)
        assertEquals(0f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun clearNightParamsMatchTable() {
        val p = WeatherLook.CLEAR_NIGHT.params
        assertEquals(0f, p.density, delta)
        assertEquals(0.2f, p.fallSpeed, delta)
        assertEquals(0f, p.glassDrops, delta)
        assertEquals(0f, p.slideSpeed, delta)
        assertEquals(0f, p.bolt, delta)
        assertEquals(0f, p.wind, delta)
        assertEquals(0f, p.gustiness, delta)
        assertEquals(0f, p.sun, delta)
        assertEquals(0f, p.clouds, delta)
        assertEquals(0, p.cloudLayers)
        assertEquals(0f, p.fog, delta)
        assertEquals(1f, p.stars, delta)
        assertFalse(p.snow)
    }

    @Test
    fun allLookParamsStayWithinValidRanges() {
        for (look in WeatherLook.values()) {
            val p = WeatherLooks.params(look)
            val tag = look.name
            assertTrue("$tag density in 0..1 (${p.density})", p.density in 0f..1f)
            assertTrue("$tag fallSpeed in 0.2..3 (${p.fallSpeed})", p.fallSpeed in 0.2f..3f)
            assertTrue("$tag glassDrops in 0..30 (${p.glassDrops})", p.glassDrops in 0f..30f)
            assertTrue("$tag slideSpeed in 0..1.2 (${p.slideSpeed})", p.slideSpeed in 0f..1.2f)
            assertTrue("$tag bolt is 0 or 1 (${p.bolt})", p.bolt == 0f || p.bolt == 1f)
            assertTrue("$tag wind in 0..1 (${p.wind})", p.wind in 0f..1f)
            assertTrue("$tag gustiness in 0..1 (${p.gustiness})", p.gustiness in 0f..1f)
            assertTrue("$tag sun in 0..1 (${p.sun})", p.sun in 0f..1f)
            assertTrue("$tag clouds in 0..1 (${p.clouds})", p.clouds in 0f..1f)
            assertTrue("$tag cloudLayers in 0..2 (${p.cloudLayers})", p.cloudLayers in 0..2)
            assertTrue("$tag fog in 0..1 (${p.fog})", p.fog in 0f..1f)
            assertTrue("$tag stars in 0..1 (${p.stars})", p.stars in 0f..1f)
        }
    }

    @Test
    fun snowDensityOverridesPerCode() {
        val lightSnow = WeatherLooks.paramsFor(71, isDay = true)
        assertNotNull(lightSnow)
        assertEquals(0.3f, lightSnow!!.density, delta)
        assertTrue(lightSnow.snow)

        val moderateSnow = WeatherLooks.paramsFor(73, isDay = true)
        assertNotNull(moderateSnow)
        assertEquals(0.6f, moderateSnow!!.density, delta)
        assertTrue(moderateSnow.snow)

        listOf(75, 77, 85, 86).forEach { code ->
            val heavySnowDay = WeatherLooks.paramsFor(code, isDay = true)
            assertNotNull("code $code by day", heavySnowDay)
            assertEquals("code $code by day", 0.9f, heavySnowDay!!.density, delta)
            assertTrue("code $code by day", heavySnowDay.snow)

            val heavySnowNight = WeatherLooks.paramsFor(code, isDay = false)
            assertNotNull("code $code by night", heavySnowNight)
            assertEquals("code $code by night", 0.9f, heavySnowNight!!.density, delta)
            assertTrue("code $code by night", heavySnowNight.snow)
        }
    }

    @Test
    fun paramsForUnknownCodeReturnsNull() {
        assertNull(WeatherLooks.paramsFor(-1, isDay = true))
        assertNull(WeatherLooks.paramsFor(4, isDay = true))
        assertNull(WeatherLooks.paramsFor(10, isDay = true))
        assertNull(WeatherLooks.paramsFor(100, isDay = true))
    }

    @Test
    fun isRainOnlyTrueForRainLooks() {
        assertTrue(WeatherLook.LIGHT_RAIN.isRain)
        assertTrue(WeatherLook.HEAVY_RAIN.isRain)
        assertTrue(WeatherLook.STORM.isRain)

        val nonRain = listOf(
            WeatherLook.CLEAR,
            WeatherLook.PARTLY,
            WeatherLook.OVERCAST,
            WeatherLook.SNOW,
            WeatherLook.FOG,
            WeatherLook.CLEAR_NIGHT,
        )
        nonRain.forEach { look ->
            assertFalse("${look.name} should not be rain", look.isRain)
        }
    }
}
