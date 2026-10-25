package app.olauncher.wellbeing

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val sleepBedTimeMs: Long? = null,
    val sleepWakeTimeMs: Long? = null,
)

/** A sleep session as epoch milliseconds. It may start the evening before. */
data class SleepSpan(val startMs: Long, val endMs: Long)

/** A sleep night ending on a given day, with total minutes and span bounds. */
data class SleepNight(val minutes: Long, val startMs: Long, val endMs: Long)

/** A resting heart rate sample as epoch milliseconds and bpm. */
data class RestingSample(val timeMs: Long, val bpm: Long)

/** Daily Health Connect summary for one day in the 7-day window. */
data class HealthDay(
    val date: LocalDate,
    val exerciseMinutes: Long?,
    val restingHeartRate: Long?,
    val sleep: SleepNight?,
)

/** Last 7 days Health Connect summary with daily values and averages. */
data class HealthWeekSummary(
    val exerciseMinutesAvg: Long? = null,
    val exerciseMinutesTotal: Long? = null,
    val restingHeartRateAvg: Long? = null,
    val sleepMinutesAvg: Long? = null,
    val sleepBedTime: LocalTime? = null,
    val sleepWakeTime: LocalTime? = null,
    val days: List<HealthDay> = emptyList(),
)

/** Display templates for the Health line. Each holds one %1$s for its value. */
data class HealthLabels(
    val steps: String,
    val slept: String,
    val restingHr: String,
    val exercise: String,
    val heartRate: String,
)

object HealthSummaryFormatting {

    const val DASH = "—"
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

    /**
     * Sleep night ending today: sessions whose END falls in [dayStartMs, dayEndMs).
     * The session may begin before midnight; its full length counts.
     * Returns null when no session ends today.
     */
    fun sleepNightEndingToday(sessions: List<SleepSpan>, dayStartMs: Long, dayEndMs: Long): SleepNight? {
        val inRange = sessions.filter { it.endMs >= dayStartMs && it.endMs < dayEndMs && it.endMs > it.startMs }
        val total = inRange.sumOf { (it.endMs - it.startMs) / 60_000L }
        return if (total > 0) {
            SleepNight(
                minutes = total,
                startMs = inRange.minOf { it.startMs },
                endMs = inRange.maxOf { it.endMs },
            )
        } else null
    }

    /**
     * Sleep minutes for the night that ended today: sessions whose END falls in
     * [dayStartMs, dayEndMs). The session may begin before midnight; its full length counts.
     * Returns null when no session ends today.
     */
    fun sleepMinutesEndingToday(sessions: List<SleepSpan>, dayStartMs: Long, dayEndMs: Long): Long? =
        sleepNightEndingToday(sessions, dayStartMs, dayEndMs)?.minutes

    /** Average of heart rate samples in bpm, rounded half up, or null when there are none. */
    fun averageBpm(samples: List<Long>): Long? =
        if (samples.isEmpty()) null else (samples.sum() * 2 + samples.size) / (samples.size * 2L)

    /** Total of today's exercise sessions in minutes, or null when there are none. */
    fun exerciseMinutesTotal(sessionMinutes: List<Long>): Long? {
        val total = sessionMinutes.filter { it > 0 }.sum()
        return if (total > 0) total else null
    }

    /**
     * Buckets exercise sessions, resting HR samples, and sleep sessions into the specified days
     * and computes 7-day averages. Null inputs indicate the feature is off or permission was missing,
     * which results in null averages and null per-day values for that metric.
     */
    fun bucketAndAverageWeek(
        days: List<LocalDate>,
        zone: ZoneId,
        exerciseSessions: List<ExerciseSpan>?,
        restingRecords: List<RestingSample>?,
        sleepRecords: List<SleepSpan>?,
    ): HealthWeekSummary {
        val daySummaries = days.map { date ->
            val dayStartMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEndMs = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val exerciseMinutes = exerciseSessions?.let { list ->
                val daySessions = list.filter { it.startMs >= dayStartMs && it.startMs < dayEndMs && it.endMs > it.startMs }
                daySessions.sumOf { (it.endMs - it.startMs) / 60_000L }
            }

            val restingHr = restingRecords?.let { list ->
                val daySamples = list.filter { it.timeMs >= dayStartMs && it.timeMs < dayEndMs }
                daySamples.maxByOrNull { it.timeMs }?.bpm
            }

            val sleep = sleepRecords?.let { list ->
                sleepNightEndingToday(list, dayStartMs, dayEndMs)
            }

            HealthDay(
                date = date,
                exerciseMinutes = exerciseMinutes,
                restingHeartRate = restingHr,
                sleep = sleep,
            )
        }

        val exerciseTotal = if (exerciseSessions == null || exerciseSessions.isEmpty()) {
            null
        } else {
            daySummaries.sumOf { it.exerciseMinutes ?: 0L }.takeIf { it > 0L }
        }
        val exerciseAvg = if (exerciseTotal != null && days.isNotEmpty()) {
            (exerciseTotal * 2 + days.size) / (days.size * 2L)
        } else null

        val restingAvg = if (restingRecords == null) {
            null
        } else {
            val measured = daySummaries.mapNotNull { it.restingHeartRate }
            averageBpm(measured)
        }

        val (sleepAvg, bedAvg, wakeAvg) = if (sleepRecords == null) {
            Triple(null, null, null)
        } else {
            val nights = daySummaries.mapNotNull { it.sleep }
            if (nights.isEmpty()) {
                Triple(null, null, null)
            } else {
                val avgMins = (nights.sumOf { it.minutes } * 2 + nights.size) / (nights.size * 2L)
                val (bed, wake) = averageBedAndWakeTimes(nights, zone)
                Triple(avgMins, bed, wake)
            }
        }

        return HealthWeekSummary(
            exerciseMinutesAvg = exerciseAvg,
            exerciseMinutesTotal = exerciseTotal,
            restingHeartRateAvg = restingAvg,
            sleepMinutesAvg = sleepAvg,
            sleepBedTime = bedAvg,
            sleepWakeTime = wakeAvg,
            days = daySummaries,
        )
    }

    /**
     * Calculates the average bedtime and wake time across the provided nights, handling
     * bedtimes that cross midnight (e.g., 23:30 and 00:30 average to 00:00).
     */
    fun averageBedAndWakeTimes(nights: List<SleepNight>, zone: ZoneId): Pair<LocalTime, LocalTime> {
        require(nights.isNotEmpty())

        val wakeMinutes = nights.map {
            val local = LocalTime.ofInstant(Instant.ofEpochMilli(it.endMs), zone)
            local.hour * 60 + local.minute
        }
        val avgWakeMinute = (wakeMinutes.sum() * 2 + wakeMinutes.size) / (wakeMinutes.size * 2)
        val wakeLocal = LocalTime.of((avgWakeMinute / 60) % 24, avgWakeMinute % 60)

        val bedRelativeMinutes = nights.map {
            val local = LocalTime.ofInstant(Instant.ofEpochMilli(it.startMs), zone)
            val minOfDay = local.hour * 60 + local.minute
            if (minOfDay >= 12 * 60) minOfDay - 24 * 60 else minOfDay
        }
        val sumBed = bedRelativeMinutes.sum()
        val count = bedRelativeMinutes.size
        val avgBedRelative = if (sumBed >= 0) {
            (sumBed * 2 + count) / (count * 2)
        } else {
            (sumBed * 2 - count) / (count * 2)
        }
        val normalizedBed = ((avgBedRelative % 1440) + 1440) % 1440
        val bedLocal = LocalTime.of(normalizedBed / 60, normalizedBed % 60)

        return Pair(bedLocal, wakeLocal)
    }

    /** Formats bed and wake times as "HH:mm – HH:mm". */
    fun formatBedWakeTime(bed: LocalTime, wake: LocalTime): String =
        "${bed.format(TIME_FORMATTER)} – ${wake.format(TIME_FORMATTER)}"

    fun formatBedWakeTime(startMs: Long, endMs: Long, zone: ZoneId): String {
        val bed = LocalTime.ofInstant(Instant.ofEpochMilli(startMs), zone)
        val wake = LocalTime.ofInstant(Instant.ofEpochMilli(endMs), zone)
        return formatBedWakeTime(bed, wake)
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
