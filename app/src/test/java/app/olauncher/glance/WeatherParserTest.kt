package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class WeatherParserTest {

    private fun loadResource(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: javaClass.getResourceAsStream("/$name")
            ?: File("app/src/test/resources/$name").takeIf { it.exists() }?.inputStream()
            ?: File("src/test/resources/$name").takeIf { it.exists() }?.inputStream()
            ?: error("Resource $name not found")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `parsePlaces parses real Open-Meteo geocoding fixture`() {
        val json = loadResource("openmeteo_geocoding.json")
        val places = WeatherParser.parsePlaces(json)

        assertEquals(2, places.size)

        val berlin1 = places[0]
        assertEquals("Berlin", berlin1.name)
        assertEquals("State of Berlin", berlin1.admin1)
        assertEquals("Germany", berlin1.country)
        assertEquals(52.52437, berlin1.lat, 0.00001)
        assertEquals(13.41053, berlin1.lon, 0.00001)
        assertEquals("Berlin, State of Berlin, Germany", berlin1.displayName())

        val berlin2 = places[1]
        assertEquals("Berlin", berlin2.name)
        assertEquals("Maryland", berlin2.admin1)
        assertEquals("United States", berlin2.country)
        assertEquals(38.32261, berlin2.lat, 0.00001)
        assertEquals(-75.21769, berlin2.lon, 0.00001)
        assertEquals("Berlin, Maryland, United States", berlin2.displayName())
    }

    @Test
    fun `parsePlaces returns empty list on invalid or missing json`() {
        assertTrue(WeatherParser.parsePlaces(null).isEmpty())
        assertTrue(WeatherParser.parsePlaces("").isEmpty())
        assertTrue(WeatherParser.parsePlaces("   ").isEmpty())
        assertTrue(WeatherParser.parsePlaces("not-json").isEmpty())
        assertTrue(WeatherParser.parsePlaces("{}").isEmpty())
        assertTrue(WeatherParser.parsePlaces("""{"results": []}""").isEmpty())
    }

    @Test
    fun `parseForecast parses real Open-Meteo forecast fixture`() {
        val json = loadResource("openmeteo_forecast.json")
        val forecast = WeatherParser.parseForecast(json)

        assertNotNull(forecast)
        forecast!!

        assertEquals(18.2, forecast.nowTemp, 0.001)
        assertEquals(1, forecast.nowCode)
        assertTrue(forecast.isDay)
        assertEquals(21.4, forecast.todayMax, 0.001)
        assertEquals(10.1, forecast.todayMin, 0.001)

        assertEquals(12, forecast.hours.size)

        val firstHour = forecast.hours[0]
        assertEquals(LocalDateTime.of(2024, 5, 18, 14, 0), firstHour.time)
        assertEquals(18.2, firstHour.temp, 0.001)
        assertEquals(0, firstHour.precipProb)
        assertEquals(1, firstHour.code)

        val thirdHour = forecast.hours[2]
        assertEquals(LocalDateTime.of(2024, 5, 18, 16, 0), thirdHour.time)
        assertEquals(17.9, thirdHour.temp, 0.001)
        assertEquals(10, thirdHour.precipProb)
        assertEquals(2, thirdHour.code)
    }

    @Test
    fun `parseForecast returns null on invalid or missing json`() {
        assertNull(WeatherParser.parseForecast(null))
        assertNull(WeatherParser.parseForecast(""))
        assertNull(WeatherParser.parseForecast("   "))
        assertNull(WeatherParser.parseForecast("invalid-json"))
        assertNull(WeatherParser.parseForecast("{}"))
        assertNull(WeatherParser.parseForecast("""{"current": {"temperature_2m": 20.0}}"""))
    }
}
