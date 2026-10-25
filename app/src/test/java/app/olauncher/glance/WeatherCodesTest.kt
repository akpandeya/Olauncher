package app.olauncher.glance

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import app.olauncher.R
import app.olauncher.helper.WeatherLook

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

    @Test
    fun `emoji maps every code group`() {
        val groups = mapOf(
            "☁️" to listOf(3),
            "🌫" to listOf(45, 48),
            "🌦" to listOf(51, 53, 55, 56, 57),
            "🌧" to listOf(61, 63, 65, 66, 67, 80, 81, 82),
            "🌨" to listOf(71, 73, 75, 77, 85, 86),
            "⛈" to listOf(95, 96, 99),
        )
        for ((emoji, codes) in groups) {
            for (code in codes) {
                assertEquals("code $code", emoji, WeatherCodes.emoji(code, isDay = true))
                assertEquals("code $code night", emoji, WeatherCodes.emoji(code, isDay = false))
            }
        }
    }

    @Test
    fun `emoji differs between day and night for codes 0 1 2`() {
        assertEquals("☀️", WeatherCodes.emoji(0, isDay = true))
        assertEquals("🌙", WeatherCodes.emoji(0, isDay = false))
        assertEquals("🌤", WeatherCodes.emoji(1, isDay = true))
        assertEquals("🌙", WeatherCodes.emoji(1, isDay = false))
        assertEquals("⛅", WeatherCodes.emoji(2, isDay = true))
        assertEquals("☁️", WeatherCodes.emoji(2, isDay = false))
    }

    @Test
    fun `emoji defaults to day`() {
        assertEquals("☀️", WeatherCodes.emoji(0))
    }

    @Test
    fun `emoji is empty for unknown codes`() {
        for (code in listOf(-1, 4, 10, 100)) {
            assertEquals("", WeatherCodes.emoji(code))
        }
    }

    @Test
    fun `iconFor maps each WeatherLook to expected drawable`() {
        assertEquals(R.drawable.ic_weather_clear_day, WeatherCodes.iconFor(WeatherLook.CLEAR))
        assertEquals(R.drawable.ic_weather_partly_cloudy, WeatherCodes.iconFor(WeatherLook.PARTLY))
        assertEquals(R.drawable.ic_weather_overcast, WeatherCodes.iconFor(WeatherLook.OVERCAST))
        assertEquals(R.drawable.ic_weather_light_rain, WeatherCodes.iconFor(WeatherLook.LIGHT_RAIN))
        assertEquals(R.drawable.ic_weather_heavy_rain, WeatherCodes.iconFor(WeatherLook.HEAVY_RAIN))
        assertEquals(R.drawable.ic_weather_thunderstorm, WeatherCodes.iconFor(WeatherLook.STORM))
        assertEquals(R.drawable.ic_weather_snow, WeatherCodes.iconFor(WeatherLook.SNOW))
        assertEquals(R.drawable.ic_weather_fog, WeatherCodes.iconFor(WeatherLook.FOG))
        assertEquals(R.drawable.ic_weather_clear_night, WeatherCodes.iconFor(WeatherLook.CLEAR_NIGHT))
    }

    @Test
    fun `icon maps every code group WeatherCodes emoji handles`() {
        val dayNightGroups = mapOf(
            R.drawable.ic_weather_overcast to listOf(3),
            R.drawable.ic_weather_fog to listOf(45, 48),
            R.drawable.ic_weather_light_rain to listOf(51, 53, 55, 56, 57, 61),
            R.drawable.ic_weather_heavy_rain to listOf(63, 65, 66, 67, 80, 81, 82),
            R.drawable.ic_weather_snow to listOf(71, 73, 75, 77, 85, 86),
            R.drawable.ic_weather_thunderstorm to listOf(95, 96, 99),
        )
        for ((iconRes, codes) in dayNightGroups) {
            for (code in codes) {
                assertEquals("code $code by day", iconRes, WeatherCodes.icon(code, isDay = true))
                assertEquals("code $code by night", iconRes, WeatherCodes.icon(code, isDay = false))
            }
        }
    }

    @Test
    fun `icon distinguishes day and night for codes 0 1 2`() {
        assertEquals(R.drawable.ic_weather_clear_day, WeatherCodes.icon(0, isDay = true))
        assertEquals(R.drawable.ic_weather_clear_night, WeatherCodes.icon(0, isDay = false))
        assertEquals(R.drawable.ic_weather_partly_cloudy, WeatherCodes.icon(1, isDay = true))
        assertEquals(R.drawable.ic_weather_clear_night, WeatherCodes.icon(1, isDay = false))
        assertEquals(R.drawable.ic_weather_partly_cloudy, WeatherCodes.icon(2, isDay = true))
        assertEquals(R.drawable.ic_weather_overcast, WeatherCodes.icon(2, isDay = false))
    }

    @Test
    fun `icon defaults to day`() {
        assertEquals(R.drawable.ic_weather_clear_day, WeatherCodes.icon(0))
        assertEquals(R.drawable.ic_weather_partly_cloudy, WeatherCodes.icon(1))
        assertEquals(R.drawable.ic_weather_partly_cloudy, WeatherCodes.icon(2))
    }

    @Test
    fun `icon is null for unknown codes`() {
        for (code in listOf(-1, 4, 10, 100)) {
            assertNull(WeatherCodes.icon(code))
            assertNull(WeatherCodes.icon(code, isDay = false))
        }
    }

    @Test
    fun `every code handled by emoji maps to a non-null icon in day and night`() {
        val documentedCodes = listOf(
            0, 1, 2, 3,
            45, 48,
            51, 53, 55, 56, 57,
            61, 63, 65, 66, 67,
            71, 73, 75, 77,
            80, 81, 82,
            85, 86,
            95, 96, 99,
        )
        for (code in documentedCodes) {
            assertTrue("emoji should handle code $code", WeatherCodes.emoji(code).isNotEmpty())
            assertNotNull("icon should handle code $code by day", WeatherCodes.icon(code, isDay = true))
            assertNotNull("icon should handle code $code by night", WeatherCodes.icon(code, isDay = false))
        }
    }

    @Test
    fun `all nine weather drawables are reachable from icon mapping`() {
        val expectedDrawables = setOf(
            R.drawable.ic_weather_clear_day,
            R.drawable.ic_weather_clear_night,
            R.drawable.ic_weather_partly_cloudy,
            R.drawable.ic_weather_overcast,
            R.drawable.ic_weather_light_rain,
            R.drawable.ic_weather_heavy_rain,
            R.drawable.ic_weather_thunderstorm,
            R.drawable.ic_weather_snow,
            R.drawable.ic_weather_fog,
        )
        assertEquals(9, expectedDrawables.size)

        val reachable = mutableSetOf<Int>()
        for (code in 0..100) {
            WeatherCodes.icon(code, isDay = true)?.let { reachable.add(it) }
            WeatherCodes.icon(code, isDay = false)?.let { reachable.add(it) }
        }
        assertEquals(expectedDrawables, reachable)
    }

    @Test
    fun `snow icon uses filled snowflake under the cloud`() {
        val rel = "src/main/res/drawable/ic_weather_snow.xml"
        val file = listOf(File(rel), File("app/$rel")).first { it.exists() }
        val xml = file.readText()
        assertTrue("snow icon should have filled snowflake path", xml.contains("android:fillColor=\"?attr/primaryColor\""))
        assertTrue("cloud should retain 1.75 stroke width", xml.contains("android:strokeWidth=\"1.75\""))
        assertTrue("viewport width is 24", xml.contains("android:viewportWidth=\"24\""))
        assertTrue("viewport height is 24", xml.contains("android:viewportHeight=\"24\""))
    }
}
