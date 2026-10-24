package app.olauncher.wellbeing

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Thin read-only wrapper around the Health Connect client.
 * Every call returns null without touching Health Connect when [isEnabled] is false
 * or the permission for that data type is missing.
 */
class HealthRepository(
    private val context: Context,
    private val isEnabled: () -> Boolean,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val client: HealthConnectClient? by lazy {
        if (isAvailable(context)) HealthConnectClient.getOrCreate(context) else null
    }

    /** Today's summary, or null when the switch is off or Health Connect is unavailable. */
    suspend fun readToday(now: Instant = Instant.now()): HealthSummary? {
        if (!isEnabled()) return null
        val c = client ?: return null
        val granted = try {
            c.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            return null
        }
        val today = LocalDate.ofInstant(now, zone)
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val sinceStart = TimeRangeFilter.between(dayStart, now)

        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            safe { c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), sinceStart))[StepsRecord.COUNT_TOTAL] }
        } else null

        val sleepNight = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            safe {
                // Look back 36 h so a night that began before midnight is found.
                val range = TimeRangeFilter.between(dayStart.minus(Duration.ofHours(12)), dayEnd)
                val spans = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, range)).records
                    .map { SleepSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
                HealthSummaryFormatting.sleepNightEndingToday(spans, dayStart.toEpochMilli(), dayEnd.toEpochMilli())
            }
        } else null
        val sleep = sleepNight?.minutes
        val sleepBedTimeMs = sleepNight?.startMs
        val sleepWakeTimeMs = sleepNight?.endMs

        val resting = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
            safe {
                c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, sinceStart)).records
                    .maxByOrNull { it.time }?.beatsPerMinute
            }
        } else null

        val heartRate = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            safe {
                val samples = c.readRecords(ReadRecordsRequest(HeartRateRecord::class, sinceStart)).records
                    .flatMap { record -> record.samples.map { it.beatsPerMinute } }
                HealthSummaryFormatting.averageBpm(samples)
            }
        } else null

        val exercise = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            safe {
                val minutes = c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, sinceStart)).records
                    .map { Duration.between(it.startTime, it.endTime).toMinutes() }
                HealthSummaryFormatting.exerciseMinutesTotal(minutes)
            }
        } else null

        return HealthSummary(steps, sleep, resting, exercise, heartRate, sleepBedTimeMs, sleepWakeTimeMs)
    }

    /**
     * Last 7 days summary (exercise minutes, resting HR, sleep per night).
     * Returns null when the switch is off, Health Connect is unavailable, or permissions cannot be read.
     * Each metric within the summary is null when its permission is missing.
     */
    suspend fun readLast7Days(now: Instant = Instant.now()): HealthWeekSummary? {
        if (!isEnabled()) return null
        val c = client ?: return null
        val granted = try {
            c.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            return null
        }
        val today = LocalDate.ofInstant(now, zone)
        val days = (0L..6L).map { today.minusDays(6 - it) }
        val windowStart = days.first().atStartOfDay(zone).toInstant()
        val windowEnd = today.plusDays(1).atStartOfDay(zone).toInstant()

        val exercise = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            safe {
                readAllRecords<ExerciseSessionRecord>(c, TimeRangeFilter.between(windowStart, now))
                    .map { ExerciseSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
            }
        } else null

        val resting = if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
            safe {
                readAllRecords<RestingHeartRateRecord>(c, TimeRangeFilter.between(windowStart, now))
                    .map { RestingSample(it.time.toEpochMilli(), it.beatsPerMinute) }
            }
        } else null

        val sleep = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            safe {
                val sleepStart = windowStart.minus(Duration.ofHours(12))
                readAllRecords<SleepSessionRecord>(c, TimeRangeFilter.between(sleepStart, windowEnd))
                    .map { SleepSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
            }
        } else null

        return HealthSummaryFormatting.bucketAndAverageWeek(
            days = days,
            zone = zone,
            exerciseSessions = exercise,
            restingRecords = resting,
            sleepRecords = sleep,
        )
    }

    /**
     * Steps and exercise sessions in [now - intervalMin, now]. Null when the switch is off,
     * Health Connect is unavailable, or nothing could be read.
     */
    suspend fun readWalkWindow(now: Instant, intervalMin: Int): WalkWindowReading? {
        if (!isEnabled()) return null
        val c = client ?: return null
        val granted = try {
            c.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            return null
        }
        val range = TimeRangeFilter.between(now.minus(Duration.ofMinutes(intervalMin.toLong())), now)

        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            safe { c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL] ?: 0L }
        } else null

        val exercise = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            safe {
                c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
                    .map { ExerciseSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
            }
        } else null

        if (steps == null && exercise == null) return null
        return WalkWindowReading(steps, exercise)
    }

    /**
     * Walks detected today (walking sessions plus step bouts). Null when the switch is off,
     * Health Connect is unavailable, or the steps and exercise permissions are missing.
     */
    suspend fun readWalkDay(now: Instant = Instant.now()): WalkDetection? {
        if (!isEnabled()) return null
        val c = client ?: return null
        val granted = try {
            c.permissionController.getGrantedPermissions()
        } catch (_: Exception) {
            return null
        }
        val today = LocalDate.ofInstant(now, zone)
        val dayStart = today.atStartOfDay(zone).toInstant()
        val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(dayStart, now)

        val steps = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            safe {
                readAllPages { token ->
                    val response = c.readRecords(ReadRecordsRequest(StepsRecord::class, range, pageToken = token))
                    response.records.map {
                        StepSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.count, it.metadata.dataOrigin.packageName)
                    } to response.pageToken
                }
            }
        } else null

        val sessions = if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
            safe {
                c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
                    .filter { it.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING }
                    .map { WalkSpan(it.startTime.toEpochMilli(), it.endTime.toEpochMilli()) }
            }
        } else null

        if (steps == null && sessions == null) return null
        return WalkDetector.detect(steps.orEmpty(), sessions.orEmpty(), dayStart.toEpochMilli(), dayEnd.toEpochMilli())
    }

    private suspend inline fun <reified T : androidx.health.connect.client.records.Record> readAllRecords(
        client: HealthConnectClient,
        filter: TimeRangeFilter,
    ): List<T> {
        val results = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = client.readRecords(ReadRecordsRequest(T::class, filter, pageToken = pageToken))
            results.addAll(response.records)
            pageToken = response.pageToken
        } while (pageToken != null)
        return results
    }

    private inline fun <T> safe(block: () -> T?): T? = try {
        block()
    } catch (_: Exception) {
        null
    }

    companion object {
        val READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        )

        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

        /** Result is the set of permissions that were granted (partial grants allowed). */
        fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
            PermissionController.createRequestPermissionResultContract()
    }
}
