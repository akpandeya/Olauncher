package app.olauncher.wellbeing

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Pure maths for timed breaks from the pause: an end time in epoch millis, nothing else. */
object PauseSnooze {

    enum class Choice { HOUR, TOMORROW }

    const val HOUR_MILLIS: Long = 60L * 60L * 1000L
    private const val TOMORROW_HOUR = 4
    private const val SEPARATOR = '|'

    /** End time for [choice]. "Until tomorrow" is the next 04:00 local time after [now]. */
    fun until(choice: Choice, now: Long, zone: ZoneId): Long = when (choice) {
        Choice.HOUR -> now + HOUR_MILLIS
        Choice.TOMORROW -> {
            val local = Instant.ofEpochMilli(now).atZone(zone)
            var next = local.toLocalDate().atTime(LocalTime.of(TOMORROW_HOUR, 0)).atZone(zone)
            if (!next.toInstant().isAfter(local.toInstant())) {
                next = local.toLocalDate().plusDays(1).atTime(LocalTime.of(TOMORROW_HOUR, 0)).atZone(zone)
            }
            next.toInstant().toEpochMilli()
        }
    }

    fun isActive(until: Long, now: Long): Boolean = until > now

    /** "15:40" style clock text for the row "Off until 15:40". */
    fun formatUntil(until: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(until).atZone(zone))

    /** Encodes package -> until as "package|millis" entries. */
    fun encode(snoozes: Map<String, Long>): Set<String> =
        snoozes.filter { (pkg, until) -> isValidPackage(pkg) && until > 0L }
            .map { (pkg, until) -> "$pkg$SEPARATOR$until" }
            .toSet()

    /** Strict decode: any malformed entry is dropped. */
    fun decode(entries: Set<String>): Map<String, Long> {
        val result = HashMap<String, Long>()
        for (entry in entries) {
            val parts = entry.split(SEPARATOR)
            if (parts.size != 2) continue
            val pkg = parts[0]
            val until = parts[1].takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull() ?: continue
            if (!isValidPackage(pkg) || until <= 0L) continue
            result[pkg] = until
        }
        return result
    }

    /** Adds or replaces [pkg]'s break and drops breaks that already ended, so the set never grows. */
    fun withAppSnooze(snoozes: Map<String, Long>, pkg: String, until: Long, now: Long): Map<String, Long> =
        snoozes.filterValues { isActive(it, now) } + (pkg to until)

    fun isAppSnoozed(pkg: String, snoozes: Map<String, Long>, now: Long): Boolean =
        snoozes[pkg]?.let { isActive(it, now) } ?: false

    private fun isValidPackage(pkg: String): Boolean =
        pkg.isNotBlank() && pkg.none { it == SEPARATOR || it.isWhitespace() }
}
