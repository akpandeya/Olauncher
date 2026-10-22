package app.olauncher.helper

import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Parses the text after `:e`: title words, then any mix of date (`tomorrow`, `fri`, `24.10`,
 * `24.10.2026`), time (`13`, `13:00`, `9.30`, `19-19:30`), length (`1h`, `1h30`, `45m`) and
 * `@place` (rest of line). The first word is always part of the title.
 * Pure Kotlin: the current moment (with its zone) is passed in so results are deterministic.
 */
object EventCommand {
    private const val DEFAULT_MINUTES = 60L

    data class Event(
        val title: String,
        val place: String,
        /** Epoch millis. For all-day events: UTC midnight of the first day, as the calendar expects. */
        val beginMillis: Long,
        val endMillis: Long,
        val allDay: Boolean,
        /** Local start/end for display; times are null for all-day events. */
        val date: LocalDate,
        val startTime: LocalTime?,
        val endTime: LocalTime?,
    )

    sealed class Parsed {
        data class Ok(val event: Event) : Parsed()
        /** [token] is the text that looked like a date or time but is invalid; null for an empty title. */
        data class Error(val token: String?) : Parsed()
    }

    private val WHITESPACE = Regex("""\s+""")
    private val LOOKS_NUMERIC = Regex("""^\d[\d:.\-]*$""")
    private val TIME = Regex("""^(\d{1,2})(?:[:.](\d{2}))?$""")
    private val DATE = Regex("""^(\d{1,2})\.(\d{1,2})(?:\.(\d{4}))?$""")
    private val LENGTH = Regex("""^(?:(\d{1,3})h(\d{1,2})?m?|(\d{1,4})m)$""", RegexOption.IGNORE_CASE)

    private val TODAY = setOf("today", "heute")
    private val TOMORROW = setOf("tomorrow", "tmrw", "morgen")
    private val DAY_AFTER = setOf("übermorgen", "uebermorgen")
    private val WEEKDAYS = mapOf(
        "mo" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY, "montag" to DayOfWeek.MONDAY,
        "di" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY, "dienstag" to DayOfWeek.TUESDAY,
        "mi" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
        "do" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY, "donnerstag" to DayOfWeek.THURSDAY,
        "fr" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY, "freitag" to DayOfWeek.FRIDAY,
        "sa" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY, "samstag" to DayOfWeek.SATURDAY,
        "so" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY, "sonntag" to DayOfWeek.SUNDAY,
    )

    private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    fun parse(args: String, now: ZonedDateTime): Parsed {
        val words = args.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val placeAt = words.indexOfFirst { it.startsWith("@") }
        val place = if (placeAt >= 0) {
            (listOf(words[placeAt].drop(1)) + words.drop(placeAt + 1)).joinToString(" ").trim()
        } else ""
        val tokens = if (placeAt >= 0) words.take(placeAt) else words

        val title = ArrayList<String>()
        var dayOffset: Long? = null
        var weekday: DayOfWeek? = null
        var explicitDate: LocalDate? = null
        var explicitHasYear = false
        var start: LocalTime? = null
        var end: LocalTime? = null
        var length: Long? = null

        for ((i, token) in tokens.withIndex()) {
            val lower = token.lowercase()
            val hasDate = dayOffset != null || weekday != null || explicitDate != null
            when {
                i > 0 && lower in TODAY && !hasDate -> dayOffset = 0
                i > 0 && lower in TOMORROW && !hasDate -> dayOffset = 1
                i > 0 && lower in DAY_AFTER && !hasDate -> dayOffset = 2
                i > 0 && lower in WEEKDAYS && !hasDate -> weekday = WEEKDAYS.getValue(lower)
                i > 0 && LENGTH.matches(token) -> {
                    if (length != null) return Parsed.Error(token)
                    length = parseLength(token) ?: return Parsed.Error(token)
                }
                i > 0 && LOOKS_NUMERIC.matches(token) -> {
                    val dm = DATE.matchEntire(token)
                    val date = dm?.let { toDate(it, now.toLocalDate()) }
                    if (date != null) {
                        if (hasDate) return Parsed.Error(token)
                        explicitDate = date.first
                        explicitHasYear = date.second
                    } else {
                        if (start != null || dm?.groupValues?.get(3)?.isNotEmpty() == true) return Parsed.Error(token)
                        val range = token.split('-')
                        when (range.size) {
                            1 -> start = parseTime(token) ?: return Parsed.Error(token)
                            2 -> {
                                start = parseTime(range[0]) ?: return Parsed.Error(token)
                                end = parseTime(range[1]) ?: return Parsed.Error(token)
                            }
                            else -> return Parsed.Error(token)
                        }
                    }
                }
                else -> title.add(token)
            }
        }
        if (title.isEmpty()) return Parsed.Error(null)

        val today = now.toLocalDate()
        val date: LocalDate = when {
            dayOffset != null -> today.plusDays(dayOffset)
            explicitDate != null ->
                if (!explicitHasYear && explicitDate.isBefore(today)) explicitDate.plusYears(1) else explicitDate
            weekday != null -> {
                val todayFits = start == null || LocalDateTime.of(today, start).isAfter(now.toLocalDateTime())
                if (today.dayOfWeek == weekday && todayFits) today else today.with(TemporalAdjusters.next(weekday))
            }
            start == null -> today
            LocalDateTime.of(today, start).isAfter(now.toLocalDateTime()) -> today
            else -> today.plusDays(1)
        }

        val titleText = title.joinToString(" ")
        if (start == null) {
            val begin = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val finish = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            return Parsed.Ok(Event(titleText, place, begin, finish, true, date, null, null))
        }
        val beginLocal = LocalDateTime.of(date, start)
        val endLocal = if (end != null) {
            LocalDateTime.of(date, end).let { if (it.isAfter(beginLocal)) it else it.plusDays(1) }
        } else {
            beginLocal.plusMinutes(length ?: DEFAULT_MINUTES)
        }
        val zone = now.zone
        return Parsed.Ok(
            Event(
                titleText, place,
                beginLocal.atZone(zone).toInstant().toEpochMilli(),
                endLocal.atZone(zone).toInstant().toEpochMilli(),
                false, date, start, endLocal.toLocalTime(),
            )
        )
    }

    private fun parseTime(s: String): LocalTime? {
        val m = TIME.matchEntire(s) ?: return null
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    /** Date and whether it had an explicit year; null when the numbers are not a real date. */
    private fun toDate(m: MatchResult, today: LocalDate): Pair<LocalDate, Boolean>? {
        val yearText = m.groupValues[3]
        val year = yearText.toIntOrNull() ?: today.year
        return try {
            LocalDate.of(year, m.groupValues[2].toInt(), m.groupValues[1].toInt()) to yearText.isNotEmpty()
        } catch (e: DateTimeException) {
            null
        }
    }

    private fun parseLength(token: String): Long? {
        val g = LENGTH.matchEntire(token)?.groupValues ?: return null
        val minutes = if (g[3].isNotEmpty()) g[3].toLong() else g[1].toLong() * 60 + (g[2].toLongOrNull() ?: 0)
        return if (minutes in 1..Duration.ofDays(7).toMinutes()) minutes else null
    }

    fun errorText(error: Parsed.Error): String =
        if (error.token == null) "Can't add an event without a title" else "Can't read '${error.token}'"

    /** Preview row text: `📅 Dentist · Fri 24 Oct · 15:00–16:00 · Main St`. */
    fun previewText(event: Event): String {
        val parts = ArrayList<String>()
        parts.add(event.title)
        parts.add(event.date.format(DAY_FORMAT))
        if (event.startTime != null && event.endTime != null) {
            parts.add("${event.startTime.format(CLOCK)}–${event.endTime.format(CLOCK)}")
        }
        if (event.place.isNotEmpty()) parts.add(event.place)
        return "📅 " + parts.joinToString(" · ")
    }
}
