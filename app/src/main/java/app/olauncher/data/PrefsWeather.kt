package app.olauncher.data

import android.content.SharedPreferences
import androidx.core.content.edit
import app.olauncher.data.Prefs.Companion.WEATHER_ENABLED
import app.olauncher.data.Prefs.Companion.WEATHER_FETCHED_AT
import app.olauncher.data.Prefs.Companion.WEATHER_LAT
import app.olauncher.data.Prefs.Companion.WEATHER_LOCATION_AT
import app.olauncher.data.Prefs.Companion.WEATHER_LON
import app.olauncher.data.Prefs.Companion.WEATHER_PLACE_NAME
import app.olauncher.data.Prefs.Companion.WEATHER_UNIT
import app.olauncher.data.Prefs.Companion.WEATHER_USE_LOCATION
import app.olauncher.data.Prefs.Companion.defaultWeatherUnit

/** Settings group: Weather (see [Prefs]). */
interface WeatherPrefs {
    var weatherEnabled: Boolean

    var weatherPlaceName: String

    var weatherLat: String

    var weatherLon: String

    var weatherUnit: String

    var weatherFetchedAt: Long

    var weatherUseLocation: Boolean

    var weatherLocationAt: Long
}

internal class WeatherPrefsStore(private val prefs: SharedPreferences) : WeatherPrefs {
    override var weatherEnabled: Boolean
        get() = prefs.getBoolean(WEATHER_ENABLED, false)
        set(value) = prefs.edit { putBoolean(WEATHER_ENABLED, value).apply() }

    override var weatherPlaceName: String
        get() = prefs.getString(WEATHER_PLACE_NAME, "") ?: ""
        set(value) = prefs.edit { putString(WEATHER_PLACE_NAME, value).apply() }

    override var weatherLat: String
        get() = prefs.getString(WEATHER_LAT, "") ?: ""
        set(value) = prefs.edit { putString(WEATHER_LAT, value).apply() }

    override var weatherLon: String
        get() = prefs.getString(WEATHER_LON, "") ?: ""
        set(value) = prefs.edit { putString(WEATHER_LON, value).apply() }

    override var weatherUnit: String
        get() = prefs.getString(WEATHER_UNIT, defaultWeatherUnit()) ?: defaultWeatherUnit()
        set(value) = prefs.edit { putString(WEATHER_UNIT, value).apply() }

    override var weatherFetchedAt: Long
        get() = prefs.getLong(WEATHER_FETCHED_AT, 0L)
        set(value) = prefs.edit { putLong(WEATHER_FETCHED_AT, value).apply() }

    override var weatherUseLocation: Boolean
        get() = prefs.getBoolean(WEATHER_USE_LOCATION, false)
        set(value) = prefs.edit { putBoolean(WEATHER_USE_LOCATION, value).apply() }

    override var weatherLocationAt: Long
        get() = prefs.getLong(WEATHER_LOCATION_AT, 0L)
        set(value) = prefs.edit { putLong(WEATHER_LOCATION_AT, value).apply() }
}
