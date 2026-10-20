package app.olauncher.wellbeing

import java.util.Locale

/**
 * Today's Health Connect values. Each field is null when there is no data or no permission.
 */
data class HealthSummary(
    val steps: Long? = null,
    val sleepMinutes: Long? = null,
    val restingHeartRate: Long? = null,
    val exerciseMinutes: Long? = null,
)

/** A sleep session as epoch milliseconds. It may start the evening before. */
data class SleepSpan(val startMs: Long, val endMs: Long)

object HealthSummaryFormatting {

    /**
     * Sleep minutes for the night that ended today: sessions whose END falls in
     * [dayStartMs, dayEndMs). The session may begin before midnight; its full length counts.
     * Returns null when no session ends today.
     */
    fun sleepMinutesEndingToday(sessions: List<SleepSpan>, dayStartMs: Long, dayEndMs: Long): Long? {
        val total = sessions
            .filter { it.endMs >= dayStartMs && it.endMs < dayEndMs && it.endMs > it.startMs }
            .sumOf { (it.endMs - it.startMs) / 60_000L }
        return if (total > 0) total else null
    }

    /** Total of today's exercise sessions in minutes, or null when there are none. */
    fun exerciseMinutesTotal(sessionMinutes: List<Long>): Long? {
        val total = sessionMinutes.filter { it > 0 }.sum()
        return if (total > 0) total else null
    }

    /**
     * One line like "6,240 steps · Slept 7 h 10 · Resting HR 58 · Exercise 30 min".
     * Parts with no data (null or zero) are left out. Returns null when no part remains,
     * so the caller hides the line.
     */
    fun formatLine(summary: HealthSummary): String? {
        val parts = buildList {
            summary.steps?.takeIf { it > 0 }?.let { add(String.format(Locale.US, "%,d steps", it)) }
            summary.sleepMinutes?.takeIf { it > 0 }?.let { add("Slept ${formatDuration(it)}") }
            summary.restingHeartRate?.takeIf { it > 0 }?.let { add("Resting HR $it") }
            summary.exerciseMinutes?.takeIf { it > 0 }?.let { add("Exercise $it min") }
        }
        return if (parts.isEmpty()) null else parts.joinToString(" · ")
    }

    /** "7 h 10", "7 h", or "45 min". */
    fun formatDuration(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> String.format(Locale.ROOT, "%d h %02d", h, m)
        }
    }
}
