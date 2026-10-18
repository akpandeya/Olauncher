package app.olauncher.glance

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class RainOutlookLabels(
    val dryNext12h: String,
    val rainAllDay: String,
    val rainUntil: String,
    val rainFrom: String,
)

object RainOutlook {

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    fun isRainy(hour: Hour): Boolean = hour.code >= 51 || hour.precipProb >= 60

    fun summary(
        hours: List<Hour>,
        labels: RainOutlookLabels,
        now: LocalDateTime = LocalDateTime.now(),
    ): String {
        if (hours.isEmpty()) return labels.dryNext12h

        val currentHourTime = now.truncatedTo(ChronoUnit.HOURS)
        val window = hours.filter { !it.time.isBefore(currentHourTime) }.ifEmpty { hours }.take(12)

        if (window.isEmpty()) return labels.dryNext12h

        val rainingNow = isRainy(window.first())

        return if (rainingNow) {
            val firstDry = window.drop(1).firstOrNull { !isRainy(it) }
            if (firstDry != null) {
                labels.rainUntil.format(firstDry.time.format(timeFormatter))
            } else {
                labels.rainAllDay
            }
        } else {
            val firstRain = window.drop(1).firstOrNull { isRainy(it) }
            if (firstRain != null) {
                labels.rainFrom.format(firstRain.time.format(timeFormatter))
            } else {
                labels.dryNext12h
            }
        }
    }
}
