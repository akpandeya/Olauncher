package app.olauncher.glance

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
}
