package app.olauncher.glance

import androidx.annotation.DrawableRes
import app.olauncher.R
import app.olauncher.helper.WeatherIconStyle
import app.olauncher.helper.WeatherLook
import app.olauncher.helper.WeatherLooks

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

    /** Vector icon resource for an animated weather look. */
    @DrawableRes
    fun iconFor(look: WeatherLook): Int = when (look) {
        WeatherLook.CLEAR -> R.drawable.ic_weather_clear_day
        WeatherLook.PARTLY -> R.drawable.ic_weather_partly_cloudy
        WeatherLook.OVERCAST -> R.drawable.ic_weather_overcast
        WeatherLook.LIGHT_RAIN -> R.drawable.ic_weather_light_rain
        WeatherLook.HEAVY_RAIN -> R.drawable.ic_weather_heavy_rain
        WeatherLook.STORM -> R.drawable.ic_weather_thunderstorm
        WeatherLook.SNOW -> R.drawable.ic_weather_snow
        WeatherLook.FOG -> R.drawable.ic_weather_fog
        WeatherLook.CLEAR_NIGHT -> R.drawable.ic_weather_clear_night
    }

    /** Pure resolver returning the colour or monochrome drawable for a look and style on Home. */
    @DrawableRes
    fun homeIconFor(look: WeatherLook, style: WeatherIconStyle = WeatherIconStyle.DEFAULT): Int = when (style) {
        WeatherIconStyle.MONOCHROME -> iconFor(look)
        WeatherIconStyle.COLOUR -> when (look) {
            WeatherLook.CLEAR -> R.drawable.ic_weather_clear_day_color
            WeatherLook.PARTLY -> R.drawable.ic_weather_partly_cloudy_color
            WeatherLook.OVERCAST -> R.drawable.ic_weather_overcast_color
            WeatherLook.LIGHT_RAIN -> R.drawable.ic_weather_light_rain_color
            WeatherLook.HEAVY_RAIN -> R.drawable.ic_weather_heavy_rain_color
            WeatherLook.STORM -> R.drawable.ic_weather_thunderstorm_color
            WeatherLook.SNOW -> R.drawable.ic_weather_snow_color
            WeatherLook.FOG -> R.drawable.ic_weather_fog_color
            WeatherLook.CLEAR_NIGHT -> R.drawable.ic_weather_clear_night_color
        }
    }

    /** Vector icon resource for a WMO weather code and day/night state. Null when unknown. */
    @DrawableRes
    fun icon(code: Int, isDay: Boolean = true): Int? =
        WeatherLooks.forForecast(code, isDay)?.let(::iconFor)
}
