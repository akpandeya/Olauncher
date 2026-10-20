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
    val heartRate: Long? = null,
)

/** A sleep session as epoch milliseconds. It may start the evening before. */
data class SleepSpan(val startMs: Long, val endMs: Long)

/** Display templates for the Health line. Each holds one %1$s for its value. */
data class HealthLabels(
    val steps: String,
    val slept: String,
    val restingHr: String,
    val exercise: String,
    val heartRate: String,
)

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

    /** Average of heart rate samples in bpm, rounded half up, or null when there are none. */
    fun averageBpm(samples: List<Long>): Long? =
        if (samples.isEmpty()) null else (samples.sum() * 2 + samples.size) / (samples.size * 2L)

    /** Total of today's exercise sessions in minutes, or null when there are none. */
    fun exerciseMinutesTotal(sessionMinutes: List<Long>): Long? {
        val total = sessionMinutes.filter { it > 0 }.sum()
        return if (total > 0) total else null
    }

    /**
     * One line like "6,240 steps · Slept 7 h 10 · Resting HR 58 · Heart rate 72 · Exercise 30 min".
     * Parts with no data (null or zero) are left out. Returns null when no part remains,
     * so the caller hides the line.
     */
    fun formatLine(summary: HealthSummary, labels: HealthLabels): String? {
        val parts = buildList {
            summary.steps?.takeIf { it > 0 }?.let { add(labels.steps.format(String.format(Locale.US, "%,d", it))) }
            summary.sleepMinutes?.takeIf { it > 0 }?.let { add(labels.slept.format(formatDuration(it))) }
            summary.restingHeartRate?.takeIf { it > 0 }?.let { add(labels.restingHr.format(it.toString())) }
            summary.heartRate?.takeIf { it > 0 }?.let { add(labels.heartRate.format(it.toString())) }
            summary.exerciseMinutes?.takeIf { it > 0 }?.let { add(labels.exercise.format(it.toString())) }
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
