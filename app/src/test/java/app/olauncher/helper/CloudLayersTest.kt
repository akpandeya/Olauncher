package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class CloudLayersTest {

    @Test
    fun cloudLayersCountPerLook() {
        assertEquals(1, WeatherLook.PARTLY.params.cloudLayers)
        assertEquals(2, WeatherLook.OVERCAST.params.cloudLayers)

        val otherLooks = WeatherLook.values().filter {
            it != WeatherLook.PARTLY && it != WeatherLook.OVERCAST
        }
        for (look in otherLooks) {
            assertEquals("Look $look must have 0 cloud layers", 0, look.params.cloudLayers)
        }
    }

    @Test
    fun cloudsPerLayerFormulaAndSimPopulation() {
        // Clouds per layer formula: round(2 + clouds * 5)
        val partlyExpectedPerLayer = (2f + WeatherLook.PARTLY.params.clouds * 5f).roundToInt()
        val overcastExpectedPerLayer = (2f + WeatherLook.OVERCAST.params.clouds * 5f).roundToInt()

        val simPartly = WeatherSim(
            look = WeatherLook.PARTLY,
            width = 390f,
            height = 844f,
            seed = 100L,
        )
        val partlyLayer0 = simPartly.clouds.filter { it.layer == 0 }
        val partlyLayer1 = simPartly.clouds.filter { it.layer == 1 }
        assertEquals(partlyExpectedPerLayer, partlyLayer0.size)
        assertEquals(0, partlyLayer1.size)
        assertEquals(partlyExpectedPerLayer, simPartly.clouds.size)

        val simOvercast = WeatherSim(
            look = WeatherLook.OVERCAST,
            width = 390f,
            height = 844f,
            seed = 200L,
        )
        val overcastLayer0 = simOvercast.clouds.filter { it.layer == 0 }
        val overcastLayer1 = simOvercast.clouds.filter { it.layer == 1 }
        assertEquals(overcastExpectedPerLayer, overcastLayer0.size)
        assertEquals(overcastExpectedPerLayer, overcastLayer1.size)
        assertEquals(overcastExpectedPerLayer * 2, simOvercast.clouds.size)

        // All other looks produce 0 clouds
        val otherLooks = WeatherLook.values().filter {
            it != WeatherLook.PARTLY && it != WeatherLook.OVERCAST
        }
        for (look in otherLooks) {
            val simOther = WeatherSim(look = look, width = 390f, height = 844f, seed = 300L)
            assertTrue("Look $look must have 0 clouds", simOther.clouds.isEmpty())
        }
    }

    @Test
    fun eachCloudHasFiveToEightPuffs() {
        val seeds = listOf(1L, 42L, 999L, 123456L)
        for (seed in seeds) {
            val sim = WeatherSim(
                look = WeatherLook.OVERCAST,
                width = 390f,
                height = 844f,
                seed = seed,
            )
            assertTrue(sim.clouds.isNotEmpty())
            for (cloud in sim.clouds) {
                assertTrue(
                    "Cloud puffs count must be between 5 and 8, but was ${cloud.puffs.size}",
                    cloud.puffs.size in 5..8,
                )
            }
        }
    }

    @Test
    fun cloudColorTokens() {
        val overcastColor = WeatherLookColors.cloudColor(WeatherLook.OVERCAST)
        assertEquals(WeatherLookColors.CLOUD_GREY, overcastColor)
        assertEquals(WeatherLookColors.OVERCAST_GREY, overcastColor)
        assertEquals(0xFFA8B0BE.toInt(), overcastColor)

        val partlyColor = WeatherLookColors.cloudColor(WeatherLook.PARTLY)
        assertEquals(WeatherLookColors.CLOUDS, partlyColor)
        assertEquals(0xFFF2F4F7.toInt(), partlyColor)

        for (look in WeatherLook.values()) {
            val color = WeatherLookColors.cloudColor(look)
            if (look == WeatherLook.OVERCAST) {
                assertEquals(WeatherLookColors.CLOUD_GREY, color)
            } else {
                assertEquals(WeatherLookColors.CLOUDS, color)
            }
        }
    }
}
