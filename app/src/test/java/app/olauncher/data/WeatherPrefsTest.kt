package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class WeatherPrefsTest {

    @Test
    fun `weather preference keys and default constants match expected values`() {
        assertEquals("WEATHER_ENABLED", Prefs.WEATHER_ENABLED)
        assertEquals("WEATHER_PLACE_NAME", Prefs.WEATHER_PLACE_NAME)
        assertEquals("WEATHER_LAT", Prefs.WEATHER_LAT)
        assertEquals("WEATHER_LON", Prefs.WEATHER_LON)
        assertEquals("WEATHER_UNIT", Prefs.WEATHER_UNIT)
        assertEquals("WEATHER_FETCHED_AT", Prefs.WEATHER_FETCHED_AT)
        assertEquals("WEATHER_USE_LOCATION", Prefs.WEATHER_USE_LOCATION)
        assertEquals("WEATHER_LOCATION_AT", Prefs.WEATHER_LOCATION_AT)
        assertEquals("WEATHER_APP_PACKAGE", Prefs.WEATHER_APP_PACKAGE)
        assertEquals("WEATHER_APP_USER", Prefs.WEATHER_APP_USER)
        assertEquals("WEATHER_APP_CLASS_NAME", Prefs.WEATHER_APP_CLASS_NAME)
        assertEquals("C", Prefs.WEATHER_UNIT_C)
        assertEquals("F", Prefs.WEATHER_UNIT_F)
    }

    @Test
    fun `default weather unit is F only for US country and C for others`() {
        assertEquals("F", Prefs.defaultWeatherUnit("US"))
        assertEquals("C", Prefs.defaultWeatherUnit("DE"))
        assertEquals("C", Prefs.defaultWeatherUnit("GB"))
        assertEquals("C", Prefs.defaultWeatherUnit("IN"))
        assertEquals("C", Prefs.defaultWeatherUnit(""))
    }
}
