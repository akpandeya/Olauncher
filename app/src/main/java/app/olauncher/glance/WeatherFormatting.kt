package app.olauncher.glance

import kotlin.math.roundToInt

object WeatherFormatting {

    const val MAX_AGE_MILLIS = 6 * 60 * 60 * 1000L

    @JvmOverloads
    fun homeLine(
        forecast: Forecast?,
        fetchedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): String? {
        if (forecast == null || fetchedAt <= 0L) return null
        if (forecast.nowTemp.isNaN()) return null
        if (now - fetchedAt > MAX_AGE_MILLIS) return null
        val temp = forecast.nowTemp.roundToInt()
        val emoji = WeatherCodes.emoji(forecast.nowCode, forecast.isDay)
        return if (emoji.isEmpty()) "$temp°" else "$emoji $temp°"
    }
}
