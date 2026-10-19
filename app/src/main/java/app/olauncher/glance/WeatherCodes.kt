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
}
