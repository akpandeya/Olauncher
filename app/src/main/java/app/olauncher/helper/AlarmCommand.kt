package app.olauncher.helper

import java.time.LocalTime
import java.util.Calendar

/**
 * Parses the text after `:a`, e.g. `7:30`, `07.30`, `730`, `7`, `19`, `+20`, then optional days
 * (`mo-fr`, `sa,su`, `we` = weekend) and label words (`6:45 mo-fr gym`).
 * Pure Kotlin: the current time is passed in so results are deterministic.
 */
object AlarmCommand {
    const val MAX_RELATIVE_MINUTES = 24 * 60

    /** [days] are `java.util.Calendar` constants (SUNDAY = 1 .. SATURDAY = 7), in week order Mon..Sun. */
    data class Alarm(
        val hour: Int,
        val minute: Int,
        val days: List<Int>,
        val label: String,
        /** Minutes from now when the time was given as `+N`; null otherwise. */
        val relativeMinutes: Int? = null,
        /** True when a one-off absolute time has already passed today. */
        val tomorrow: Boolean = false,
    )

    private val ABSOLUTE = Regex("""^(\d{1,2})(?:[:.](\d{2}))?$|^(\d)(\d{2})$|^(\d{2})(\d{2})$""")
    private val RELATIVE = Regex("""^\+(\d{1,4})$""")
    private val DAYS_TOKEN = Regex("""^[a-z]+(?:[,-][a-z]+)*$""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("""\s+""")

    private val WEEK = listOf(
        Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
        Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
    )
    private val DAY_NAMES = mapOf(
        "mo" to Calendar.MONDAY, "mon" to Calendar.MONDAY,
        "di" to Calendar.TUESDAY, "tue" to Calendar.TUESDAY,
        "mi" to Calendar.WEDNESDAY, "wed" to Calendar.WEDNESDAY,
        "do" to Calendar.THURSDAY, "thu" to Calendar.THURSDAY,
        "fr" to Calendar.FRIDAY, "fri" to Calendar.FRIDAY,
        "sa" to Calendar.SATURDAY, "sat" to Calendar.SATURDAY,
        "so" to Calendar.SUNDAY, "su" to Calendar.SUNDAY, "sun" to Calendar.SUNDAY,
    )
    private val SHORT_LABELS = mapOf(
        Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue", Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri", Calendar.SATURDAY to "Sat",
        Calendar.SUNDAY to "Sun",
    )

    /** Null when the time is missing or invalid, or a day list contains an unknown word. */
    fun parse(args: String, now: LocalTime): Alarm? {
        val words = args.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val first = words.firstOrNull() ?: return null
        var rest = words.drop(1)

        var relative: Int? = null
        val hour: Int
        val minute: Int
        val rel = RELATIVE.matchEntire(first)
        if (rel != null) {
            val minutes = rel.groupValues[1].toInt()
            if (minutes < 1 || minutes > MAX_RELATIVE_MINUTES) return null
            val target = now.withSecond(0).withNano(0).plusMinutes(minutes.toLong())
            hour = target.hour
            minute = target.minute
            relative = minutes
        } else {
            val m = ABSOLUTE.matchEntire(first) ?: return null
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> { hour = g[1].toInt(); minute = g[2].toIntOrNull() ?: 0 }
                g[3].isNotEmpty() -> { hour = g[3].toInt(); minute = g[4].toInt() }
                else -> { hour = g[5].toInt(); minute = g[6].toInt() }
            }
            if (hour !in 0..23 || minute !in 0..59) return null
        }

        var days = emptyList<Int>()
        val daysWord = rest.firstOrNull()
        if (daysWord != null && DAYS_TOKEN.matches(daysWord)) {
            val lower = daysWord.lowercase()
            val parsed = parseDays(lower)
            if (parsed != null) {
                days = parsed
                rest = rest.drop(1)
            } else if (',' in lower || '-' in lower) {
                return null // looks like a day list but has an unknown word
            }
        }

        val tomorrow = relative == null && days.isEmpty() &&
            (hour * 60 + minute) <= (now.hour * 60 + now.minute)
        return Alarm(hour, minute, days, rest.joinToString(" "), relative, tomorrow)
    }

    /** Null when any word is unknown. `we` means weekend (Sa, Su). */
    private fun parseDays(token: String): List<Int>? {
        val result = HashSet<Int>()
        for (part in token.split(',')) {
            if (part.isEmpty()) return null
            val ends = part.split('-')
            if (ends.size > 2) return null
            if (ends.size == 1) {
                if (part == "we") {
                    result.add(Calendar.SATURDAY)
                    result.add(Calendar.SUNDAY)
                } else {
                    result.add(DAY_NAMES[part] ?: return null)
                }
            } else {
                val from = WEEK.indexOf(DAY_NAMES[ends[0]] ?: return null)
                val to = WEEK.indexOf(DAY_NAMES[ends[1]] ?: return null)
                var i = from
                while (true) {
                    result.add(WEEK[i])
                    if (i == to) break
                    i = (i + 1) % 7
                }
            }
        }
        return WEEK.filter { it in result }
    }

    /** Preview row text: `⏰ 07:30 tomorrow · gym`, `⏰ 07:00 Mon–Fri`, `⏰ in 20 min (14:52)`. */
    fun previewText(alarm: Alarm): String {
        val clock = "%02d:%02d".format(alarm.hour, alarm.minute)
        val timing = when {
            alarm.relativeMinutes != null -> "in ${alarm.relativeMinutes} min ($clock)"
            alarm.days.isNotEmpty() -> "$clock ${formatDays(alarm.days)}"
            alarm.tomorrow -> "$clock tomorrow"
            else -> clock
        }
        return "⏰ " + timing + if (alarm.label.isEmpty()) "" else " · ${alarm.label}"
    }

    private fun formatDays(days: List<Int>): String {
        val idx = days.map { WEEK.indexOf(it) }
        val contiguous = idx.zipWithNext().all { (a, b) -> b == a + 1 }
        return if (days.size >= 3 && contiguous) {
            SHORT_LABELS.getValue(days.first()) + "–" + SHORT_LABELS.getValue(days.last())
        } else {
            days.joinToString(", ") { SHORT_LABELS.getValue(it) }
        }
    }
}
