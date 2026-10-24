package app.olauncher.glance

import androidx.annotation.DrawableRes
import app.olauncher.R

object WeatherCodes {

    fun text(code: Int, isDay: Boolean = true): String = when (code) {
        0 -> if (isDay) "clear" else "clear night"
        1 -> "mainly clear"
        2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55 -> "drizzle"
        56, 57 -> "freezing drizzle"
        61 -> "light rain"
        63 -> "rain"
        65 -> "heavy rain"
        66, 67 -> "freezing rain"
        71, 73, 75 -> "snow"
        77 -> "snow grains"
        80, 81, 82 -> "showers"
        85, 86 -> "snow showers"
        95, 96, 99 -> "thunderstorm"
        else -> "—"
    }

    /** Emoji for a WMO weather code; empty when the code is unknown so callers can skip it. */
    fun emoji(code: Int, isDay: Boolean = true): String = when (code) {
        0, 1 -> if (isDay) (if (code == 0) "☀️" else "🌤") else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫"
        51, 53, 55, 56, 57 -> "🌦"
        61, 63, 65, 66, 67, 80, 81, 82 -> "🌧"
        71, 73, 75, 77, 85, 86 -> "🌨"
        95, 96, 99 -> "⛈"
        else -> ""
    }

    /** Vector icon resource for a WMO weather code and day/night state. Null when unknown. */
    @DrawableRes
    fun icon(code: Int, isDay: Boolean = true): Int? = when (code) {
        0 -> if (isDay) R.drawable.ic_weather_clear_day else R.drawable.ic_weather_clear_night
        1 -> if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_clear_night
        2 -> if (isDay) R.drawable.ic_weather_partly_cloudy else R.drawable.ic_weather_overcast
        3 -> R.drawable.ic_weather_overcast
        45, 48 -> R.drawable.ic_weather_fog
        51, 53, 55, 56, 57, 61 -> R.drawable.ic_weather_light_rain
        63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_weather_heavy_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_weather_snow
        95, 96, 99 -> R.drawable.ic_weather_thunderstorm
        else -> null
    }
}
