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

/**
 * A sleep night ending on a given day, with total minutes and span bounds.
 * [startMs] and [endMs] cover every session ending that day, naps included.
 * [mainStartMs] and [mainEndMs] are the longest session only: the real bed and wake times.
 */
data class SleepNight(
    val minutes: Long,
    val startMs: Long,
    val endMs: Long,
    val mainStartMs: Long = startMs,
    val mainEndMs: Long = endMs,
)

/** Average bed and wake clock times over several nights. */
data class BedWakeAverage(val bed: LocalTime, val wake: LocalTime)

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

/**
 * Text for the 7 days Health tiles. [bedTime] and [wakeTime] are both set or both null;
 * they are null when there is no trustworthy average, and the tile then hides the line.
 */
data class WeekHealthValues(
    val exercise: String,
    val restingHr: String,
    val sleep: String,
    val bedTime: String?,
    val wakeTime: String?,
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
            val main = inRange.maxBy { it.endMs - it.startMs }
            SleepNight(
                minutes = total,
                startMs = inRange.minOf { it.startMs },
                endMs = inRange.maxOf { it.endMs },
                mainStartMs = main.startMs,
                mainEndMs = main.endMs,
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
                val times = averageBedAndWakeTimes(nights, zone)
                Triple(avgMins, times?.bed, times?.wake)
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
     * Average bed and wake time over [nights], using each night's main (longest) session so a
     * nap does not count as the wake time. Both are circular means on the 24-hour clock, so
     * 23:00 and 01:00 average to 00:00 and 23:50 and 00:10 to 00:00.
     * Returns null when the times are too scattered to have a meaningful average, or when the
     * average bed-to-wake gap is not a plausible night (outside [MIN_SLEEP_GAP_MIN]..[MAX_SLEEP_GAP_MIN]).
     */
    fun averageBedAndWakeTimes(nights: List<SleepNight>, zone: ZoneId): BedWakeAverage? {
        if (nights.isEmpty()) return null
        val bed = circularMeanMinuteOfDay(nights.map { minuteOfDay(it.mainStartMs, zone) }) ?: return null
        val wake = circularMeanMinuteOfDay(nights.map { minuteOfDay(it.mainEndMs, zone) }) ?: return null
        val gap = Math.floorMod(wake - bed, MINUTES_PER_DAY)
        if (gap !in MIN_SLEEP_GAP_MIN..MAX_SLEEP_GAP_MIN) return null
        return BedWakeAverage(
            bed = LocalTime.of(bed / 60, bed % 60),
            wake = LocalTime.of(wake / 60, wake % 60),
        )
    }

    /**
     * Circular mean of clock times given as minutes since midnight, rounded to the minute.
     * Each time is a point on a 24-hour circle; the mean is the direction of their sum.
     * Returns null for an empty list or when the points spread so far that the mean is
     * not meaningful (mean resultant length below [MIN_CLOCK_CONCENTRATION]).
     */
    fun circularMeanMinuteOfDay(minutes: List<Int>): Int? {
        if (minutes.isEmpty()) return null
        var sumSin = 0.0
        var sumCos = 0.0
        for (m in minutes) {
            val angle = 2 * Math.PI * m / MINUTES_PER_DAY
            sumSin += Math.sin(angle)
            sumCos += Math.cos(angle)
        }
        val concentration = Math.hypot(sumSin, sumCos) / minutes.size
        if (concentration < MIN_CLOCK_CONCENTRATION) return null
        val meanAngle = Math.atan2(sumSin, sumCos)
        val meanMinute = Math.round(meanAngle / (2 * Math.PI) * MINUTES_PER_DAY).toInt()
        return Math.floorMod(meanMinute, MINUTES_PER_DAY)
    }

    private fun minuteOfDay(epochMs: Long, zone: ZoneId): Int {
        val local = LocalTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        return local.hour * 60 + local.minute
    }

    private const val MINUTES_PER_DAY = 24 * 60

    /** Two times 8 h apart give 0.5; more scatter than that has no useful average. */
    private const val MIN_CLOCK_CONCENTRATION = 0.5

    private const val MIN_SLEEP_GAP_MIN = 2 * 60
    private const val MAX_SLEEP_GAP_MIN = 16 * 60

    /**
     * Values for the 7 days Health tiles: weekly exercise total and average sleep as
     * "20 h 27" (one short line), average resting HR, and average bed and wake times.
     */
    fun weekValues(week: HealthWeekSummary?): WeekHealthValues {
        val bed = week?.sleepBedTime
        val wake = week?.sleepWakeTime
        val timesValid = bed != null && wake != null
        return WeekHealthValues(
            exercise = week?.exerciseMinutesTotal?.let { formatDuration(it) } ?: DASH,
            restingHr = week?.restingHeartRateAvg?.toString() ?: DASH,
            sleep = week?.sleepMinutesAvg?.let { formatDuration(it) } ?: DASH,
            bedTime = if (timesValid) formatClock(bed!!) else null,
            wakeTime = if (timesValid) formatClock(wake!!) else null,
        )
    }

    /** A clock time as "HH:mm", for example "07:10". */
    fun formatClock(time: LocalTime): String = time.format(TIME_FORMATTER)

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
