package app.olauncher.ui.today.cards

import android.view.View
import app.olauncher.R
import app.olauncher.databinding.CardWeatherBinding
import app.olauncher.glance.Forecast
import app.olauncher.ui.today.TodayCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WeatherCardTest {

    @Test
    fun `weather card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == WeatherCard.KEY }
        assertNotNull(card)
        assertEquals("weather", card?.key)
        assertEquals(R.string.weather, card?.titleRes)
    }

    @Test
    fun `isAvailable follows weatherEnabled and cache existence`() {
        // True only when weather is on and cache exists
        assertTrue(WeatherCardLogic.isAvailable(weatherEnabled = true, hasCache = true))
        assertFalse(WeatherCardLogic.isAvailable(weatherEnabled = false, hasCache = true))
        assertFalse(WeatherCardLogic.isAvailable(weatherEnabled = true, hasCache = false))
        assertFalse(WeatherCardLogic.isAvailable(weatherEnabled = false, hasCache = false))
    }

    @Test
    fun `formatLine1 produces expected string for spec example`() {
        val forecast = Forecast(
            nowTemp = 12.0,
            nowCode = 61, // light rain
            isDay = true,
            todayMax = 15.0,
            todayMin = 8.0,
            hours = emptyList(),
        )

        val line1 = WeatherCardLogic.formatLine1(forecast)
        assertEquals("12° light rain · 15° / 8°", line1)
    }

    @Test
    fun `formatLine1 rounds temperatures correctly`() {
        val line1 = WeatherCardLogic.formatLine1(
            nowTemp = 12.4,
            nowCode = 61,
            isDay = true,
            todayMax = 15.6,
            todayMin = 7.7,
        )
        assertEquals("12° light rain · 16° / 8°", line1)
    }

    @Test
    fun `formatLine3 produces expected string for Berlin as of 14_05 Open-Meteo`() {
        val zoneId = ZoneId.of("Europe/Berlin")
        val zonedDateTime = ZonedDateTime.of(
            LocalDateTime.of(2024, 5, 18, 14, 5),
            zoneId,
        )
        val fetchedAt = zonedDateTime.toInstant().toEpochMilli()

        val line3 = WeatherCardLogic.formatLine3(
            placeName = "Berlin",
            fetchedAt = fetchedAt,
            asOfTemplate = "as of %1\$s · Open-Meteo",
            zoneId = zoneId,
        )
        assertEquals("Berlin · as of 14:05 · Open-Meteo", line3)
    }

    @Test
    fun `formatLine3 without place name formats cleanly`() {
        val zoneId = ZoneId.of("Europe/Berlin")
        val zonedDateTime = ZonedDateTime.of(
            LocalDateTime.of(2024, 5, 18, 14, 5),
            zoneId,
        )
        val fetchedAt = zonedDateTime.toInstant().toEpochMilli()

        val line3 = WeatherCardLogic.formatLine3(
            placeName = "",
            fetchedAt = fetchedAt,
            asOfTemplate = "as of %1\$s · Open-Meteo",
            zoneId = zoneId,
        )
        assertEquals("as of 14:05 · Open-Meteo", line3)
    }

    @Test
    fun `WeatherCard singleton holds no per-view state`() {
        val perViewTypes = setOf(
            CardWeatherBinding::class.java,
        )
        val leaked = WeatherCard::class.java.declaredFields.filter { field ->
            perViewTypes.any { it.isAssignableFrom(field.type) } || View::class.java.isAssignableFrom(field.type)
        }
        assertTrue("per-view fields on WeatherCard: ${leaked.map { it.name }}", leaked.isEmpty())
    }
}
