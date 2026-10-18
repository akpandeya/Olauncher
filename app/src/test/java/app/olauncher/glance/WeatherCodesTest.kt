package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {

    @Test
    fun `code 0 maps to clear by day and clear night by night`() {
        assertEquals("clear", WeatherCodes.text(0, isDay = true))
        assertEquals("clear night", WeatherCodes.text(0, isDay = false))
    }

    @Test
    fun `tests cover every code Open-Meteo documents`() {
        val expected = mapOf(
            0 to "clear",
            1 to "mainly clear",
            2 to "partly cloudy",
            3 to "overcast",
            45 to "fog",
            48 to "fog",
            51 to "drizzle",
            53 to "drizzle",
            55 to "drizzle",
            56 to "freezing drizzle",
            57 to "freezing drizzle",
            61 to "light rain",
            63 to "rain",
            65 to "heavy rain",
            66 to "freezing rain",
            67 to "freezing rain",
            71 to "snow",
            73 to "snow",
            75 to "snow",
            77 to "snow grains",
            80 to "showers",
            81 to "showers",
            82 to "showers",
            85 to "snow showers",
            86 to "snow showers",
            95 to "thunderstorm",
            96 to "thunderstorm",
            99 to "thunderstorm",
        )

        assertEquals(28, expected.size)

        for ((code, text) in expected) {
            assertEquals("Code $code should map to '$text'", text, WeatherCodes.text(code, isDay = true))
        }
    }

    @Test
    fun `unknown code maps to em dash`() {
        assertEquals("—", WeatherCodes.text(-1, isDay = true))
        assertEquals("—", WeatherCodes.text(-1, isDay = false))
        assertEquals("—", WeatherCodes.text(4, isDay = true))
        assertEquals("—", WeatherCodes.text(10, isDay = true))
        assertEquals("—", WeatherCodes.text(100, isDay = true))
    }
}
