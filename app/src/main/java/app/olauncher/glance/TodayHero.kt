package app.olauncher.glance

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** Picks the hours shown in the hero's hourly row. */
object HourlyStrip {

    const val DEFAULT_COUNT = 6

    /** Up to [count] hours starting at the hour containing [now]; earlier hours are skipped. */
    fun next(hours: List<Hour>, now: LocalDateTime, count: Int = DEFAULT_COUNT): List<Hour> {
        if (count <= 0) return emptyList()
        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        return hours.filter { !it.time.isBefore(currentHour) }.take(count)
    }
}

/** Context-free text for the Today hero (date block and weather block). */
object HeroFormatting {

    private const val DAY_FIRST_HOUR = 6
    private const val DAY_LAST_HOUR = 20

    fun dayName(date: LocalDate, locale: Locale): String =
        capitalise(date.format(DateTimeFormatter.ofPattern("EEEE", locale)))

    fun dateLine(date: LocalDate, locale: Locale): String {
        val pattern = if (locale.language == Locale.GERMAN.language) "d. MMMM" else "d MMMM"
        return date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    fun capitalise(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

    /** "🌦 12°", or "12°" when the code has no emoji. */
    fun bigTemperature(forecast: Forecast): String {
        val temp = forecast.nowTemp.roundToInt()
        val emoji = WeatherCodes.emoji(forecast.nowCode, forecast.isDay)
        return if (emoji.isEmpty()) "$temp°" else "$emoji $temp°"
    }

    /** "Light rain · 15° / 8°". */
    fun conditionLine(forecast: Forecast): String {
        val condition = capitalise(WeatherCodes.text(forecast.nowCode, forecast.isDay))
        return "$condition · ${forecast.todayMax.roundToInt()}° / ${forecast.todayMin.roundToInt()}°"
    }

    fun hourLabel(hour: Hour): String = String.format(Locale.ROOT, "%02d", hour.time.hour)

    fun hourTemperature(hour: Hour): String = "${hour.temp.roundToInt()}°"

    /** Hours carry no day/night flag, so a fixed daylight window picks sun or moon. */
    fun hourEmoji(hour: Hour): String =
        WeatherCodes.emoji(hour.code, hour.time.hour in DAY_FIRST_HOUR..DAY_LAST_HOUR)
}
