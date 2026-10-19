package app.olauncher.quiet

import app.olauncher.wellbeing.ScheduleMath
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Pure scheduling and formatting logic for held notification quiet releases.
 */
object QuietRelease {

    const val DEFAULT_RELEASE_TIMES = "750,1110"

    /**
     * Calculates the next release time in epoch milliseconds.
     *
     * Times are sorted and evaluated in [zone]. Returns the next slot strictly after [nowMillis]
     * today, or rolls to tomorrow's earliest slot if no remaining slots exist today.
     * DST transitions are handled safely by ZoneId and ScheduleMath.
     *
     * @throws IllegalArgumentException if [times] is empty.
     */
    fun nextRelease(
        nowMillis: Long,
        zone: ZoneId,
        times: List<Int>,
    ): Long {
        require(times.isNotEmpty()) { "times must not be empty" }
        val sortedTimes = times.distinct().sorted()

        val currentZonedDateTime = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val today = currentZonedDateTime.toLocalDate()

        for (minuteOfDay in sortedTimes) {
            val slotMillis = ScheduleMath.slotToEpochMillis(today, minuteOfDay, zone)
            if (slotMillis > nowMillis) {
                return slotMillis
            }
        }

        val tomorrow = today.plusDays(1)
        return ScheduleMath.slotToEpochMillis(tomorrow, sortedTimes.first(), zone)
    }

    /**
     * Calculates the next release time formatted as "HH:mm".
     */
    fun nextReleaseFormatted(
        nowMillis: Long,
        zone: ZoneId,
        times: List<Int>,
    ): String {
        val nextMillis = nextRelease(nowMillis, zone, times)
        val zonedDateTime = Instant.ofEpochMilli(nextMillis).atZone(zone)
        return formatTime(zonedDateTime.hour * 60 + zonedDateTime.minute)
    }

    private data class AppCount(val name: String, val count: Int)

    /**
     * Builds the quiet summary string for held items.
     * Format: "6 held · Instagram 4, YouTube 2" (top 3 apps by count, then "+X more").
     * Returns empty string if [items] is empty.
     */
    fun summary(items: List<HeldItem>): String {
        if (items.isEmpty()) return ""

        val groups = items.groupBy { it.pkg }
        val appCounts = groups.map { (pkg, groupItems) ->
            val name = groupItems.firstOrNull { it.label.isNotBlank() }?.label?.trim()
                ?.takeIf { it.isNotEmpty() } ?: pkg
            AppCount(name = name, count = groupItems.size)
        }

        val sorted = appCounts.sortedWith(
            compareByDescending<AppCount> { it.count }.thenBy { it.name }
        )

        val top3 = sorted.take(3)
        val remaining = sorted.size - top3.size
        val parts = top3.map { "${it.name} ${it.count}" }.toMutableList()
        if (remaining > 0) {
            parts.add("+$remaining more")
        }

        return "${items.size} held · ${parts.joinToString(", ")}"
    }

    /**
     * Parses a comma-separated string of minutes-of-day into a sorted list of 1 to 3 valid integers.
     * Falls back to [DEFAULT_RELEASE_TIMES] (12:30 and 18:30) if input is empty or invalid.
     */
    fun parseTimes(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) {
            return parseTimesInternal(DEFAULT_RELEASE_TIMES)
        }
        val parsed = parseTimesInternal(raw)
        return if (parsed.isNotEmpty()) parsed else parseTimesInternal(DEFAULT_RELEASE_TIMES)
    }

    private fun parseTimesInternal(raw: String): List<Int> {
        return raw.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0 until 1440 }
            .distinct()
            .sorted()
            .take(3)
    }

    /**
     * Formats a list of minutes-of-day into a comma-separated string of up to 3 entries.
     */
    fun formatTimes(times: List<Int>): String {
        val validTimes = times.filter { it in 0 until 1440 }.distinct().sorted().take(3)
        return if (validTimes.isNotEmpty()) {
            validTimes.joinToString(",")
        } else {
            DEFAULT_RELEASE_TIMES
        }
    }

    /**
     * Formats minute-of-day (0..1439) as "HH:mm".
     */
    fun formatTime(minuteOfDay: Int): String {
        val hours = (minuteOfDay / 60) % 24
        val minutes = minuteOfDay % 60
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes)
    }

    /**
     * Formats release times for the Settings row, e.g. "12:30 · 18:30".
     */
    fun formatReleaseTimesRow(times: List<Int>): String {
        val validTimes = times.filter { it in 0 until 1440 }.distinct().sorted().take(3)
        val target = if (validTimes.isNotEmpty()) validTimes else parseTimes(DEFAULT_RELEASE_TIMES)
        return target.joinToString(" · ") { formatTime(it) }
    }
}
