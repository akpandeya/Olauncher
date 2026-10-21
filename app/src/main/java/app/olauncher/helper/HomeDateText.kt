package app.olauncher.helper

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Formats the home date line. Context-free. */
object HomeDateText {
    private val cache = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    fun format(date: LocalDate, german: Boolean, combined: Boolean, locale: Locale): String {
        val pattern = when {
            german && combined -> "EEEE d. MMMM"
            german -> "EEEE, d. MMMM"
            combined -> "EEE d MMM"
            else -> "EEE, d MMM"
        }
        val effectiveLocale = if (german) Locale.GERMAN else locale
        return cache.getOrPut(pattern to effectiveLocale) {
            DateTimeFormatter.ofPattern(pattern, effectiveLocale)
        }.format(date)
    }
}
