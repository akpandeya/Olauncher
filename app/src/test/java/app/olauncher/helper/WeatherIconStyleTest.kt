package app.olauncher.helper

import app.olauncher.glance.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherIconStyleTest {

    @Test
    fun `default is colour`() {
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.DEFAULT)
    }

    @Test
    fun `unknown or missing stored value falls back to colour`() {
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from(null))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from(""))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from("   "))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from("unknown"))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from("invalid"))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.parse(null))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.parse("???"))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.fromStored(null))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.fromStored("other"))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.fromKey(null))
    }

    @Test
    fun `stored value parses known styles case-insensitively`() {
        assertEquals(WeatherIconStyle.MONOCHROME, WeatherIconStyle.from("monochrome"))
        assertEquals(WeatherIconStyle.MONOCHROME, WeatherIconStyle.from("MONOCHROME"))
        assertEquals(WeatherIconStyle.MONOCHROME, WeatherIconStyle.from(" Monochrome "))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from("colour"))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from("COLOUR"))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from("color"))
        assertEquals(WeatherIconStyle.COLOUR, WeatherIconStyle.from("COLOR"))
    }

    @Test
    fun `for every look colour and monochrome resolve to different drawables and monochrome equals iconFor`() {
        for (look in WeatherLook.values()) {
            val colorRes = WeatherCodes.homeIconFor(look, WeatherIconStyle.COLOUR)
            val monoRes = WeatherCodes.homeIconFor(look, WeatherIconStyle.MONOCHROME)

            assertNotNull(colorRes)
            assertNotNull(monoRes)
            assertNotEquals("Colour and monochrome drawables must differ for $look", colorRes, monoRes)
            assertEquals("Monochrome drawable must equal WeatherCodes.iconFor for $look", WeatherCodes.iconFor(look), monoRes)
        }
    }

    @Test
    fun `default parameter of homeIconFor resolves to colour`() {
        for (look in WeatherLook.values()) {
            val defaultRes = WeatherCodes.homeIconFor(look)
            val colorRes = WeatherCodes.homeIconFor(look, WeatherIconStyle.COLOUR)
            assertEquals("Default homeIconFor must equal COLOUR for $look", colorRes, defaultRes)
        }
    }

    @Test
    fun `today card mapping is unchanged by the style`() {
        val testCodes = listOf(0, 1, 2, 3, 45, 51, 61, 65, 71, 95, 999)
        for (code in testCodes) {
            for (isDay in listOf(true, false)) {
                val expectedLook = WeatherLooks.forForecast(code, isDay)
                val expectedIcon = expectedLook?.let { WeatherCodes.iconFor(it) }
                assertEquals(
                    "Today card WeatherCodes.icon for code $code (isDay=$isDay) should match iconFor(look)",
                    expectedIcon,
                    WeatherCodes.icon(code, isDay)
                )
            }
        }
    }
}
