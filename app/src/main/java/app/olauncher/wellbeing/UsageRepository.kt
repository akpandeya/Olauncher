package app.olauncher.wellbeing

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * Repository for querying application usage and open counts today.
 * Methods return null when usage stats permission has not been granted.
 */
open class UsageRepository(private val context: Context? = null) {

    /**
     * Returns the number of times [pkg] was opened today using UsageEvents ACTIVITY_RESUMED,
     * de-duplicating resumes less than 2 seconds apart.
     * Returns null if usage stats permission is not granted.
     */
    open suspend fun openCountToday(pkg: String): Int? = openCountToday(setOf(pkg))

    /**
     * Returns total number of times [pkgs] were opened today using UsageEvents ACTIVITY_RESUMED,
     * de-duplicating resumes less than 2 seconds apart per package.
     * Returns null if usage stats permission is not granted.
     */
    open suspend fun openCountToday(pkgs: Set<String>): Int? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (!ctx.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext 0

        val startTime = startOfTodayMillis()
        val endTime = System.currentTimeMillis()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext null

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val eventList = mutableListOf<Pair<String, Long>>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName in pkgs && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                eventList.add(event.packageName to event.timeStamp)
            }
        }

        countOpensForPackages(eventList, pkgs, RESUME_GAP_MS)
    }

    /**
     * Returns total foreground time in milliseconds for [pkgs] today using [EventLogWrapper].
     * Returns null if usage stats permission is not granted.
     */
    open suspend fun foregroundMillisToday(pkgs: Set<String>): Long? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (!ctx.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext 0L

        val startTime = startOfTodayMillis()
        val endTime = System.currentTimeMillis()

        val eventLogWrapper = EventLogWrapper(ctx)
        val foregroundStats = eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
        val aggregated = eventLogWrapper.aggregateForegroundStats(foregroundStats)
        val matchingStats = aggregated.filter { it.applicationId in pkgs }
        eventLogWrapper.aggregateSimpleUsageStats(matchingStats)
    }

    /**
     * Returns total foreground time in milliseconds across all apps today using [EventLogWrapper].
     * Returns null if usage stats permission is not granted.
     */
    open suspend fun totalForegroundMillisToday(): Long? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (!ctx.appUsagePermissionGranted()) return@withContext null

        val startTime = startOfTodayMillis()
        val endTime = System.currentTimeMillis()

        val eventLogWrapper = EventLogWrapper(ctx)
        val foregroundStats = eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
        val aggregated = eventLogWrapper.aggregateForegroundStats(foregroundStats)
        eventLogWrapper.aggregateSimpleUsageStats(aggregated)
    }

    open suspend fun foregroundMillisToday(): Long? = totalForegroundMillisToday()

    /**
     * Checks if any of the given [pkgs] had an ACTIVITY_RESUMED event in the last [withinMillis] ms.
     * Returns false if usage access is not granted or if [pkgs] is empty.
     */
    suspend fun hasRecentResume(
        pkgs: Set<String>,
        withinMillis: Long = RECENT_RESUME_WINDOW_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext false
        if (!ctx.appUsagePermissionGranted()) return@withContext false
        if (pkgs.isEmpty()) return@withContext false

        val endTime = System.currentTimeMillis()
        val startTime = endTime - withinMillis

        val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext false

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName in pkgs && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                return@withContext true
            }
        }
        false
    }

    /**
     * Returns a map of package name to foreground time in milliseconds for [pkgs] today.
     * Returns null if usage stats permission is not granted.
     */
    suspend fun foregroundMillisByPackageToday(pkgs: Set<String>): Map<String, Long>? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (!ctx.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext emptyMap()

        val startTime = startOfTodayMillis()
        val endTime = System.currentTimeMillis()

        val eventLogWrapper = EventLogWrapper(ctx)
        val foregroundStats = eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
        val aggregated = eventLogWrapper.aggregateForegroundStats(foregroundStats)
        val matchingStats = aggregated.filter { it.applicationId in pkgs }
        matchingStats.associate { it.applicationId to it.timeUsed }
    }

    /**
     * Returns total number of times [pkgs] were opened on [epochDay] using UsageEvents ACTIVITY_RESUMED,
     * de-duplicating resumes less than 2 seconds apart per package.
     * Returns null if usage stats permission is not granted.
     */
    open suspend fun openCountForDay(pkgs: Set<String>, epochDay: Long): Int? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (!ctx.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext 0

        val (startTime, dayEnd) = dayStartAndEndMillis(epochDay)
        val now = System.currentTimeMillis()
        if (startTime > now) return@withContext 0
        val endTime = minOf(dayEnd, now)

        val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext null

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val eventList = mutableListOf<Pair<String, Long>>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName in pkgs && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                eventList.add(event.packageName to event.timeStamp)
            }
        }

        countOpensForPackages(eventList, pkgs, RESUME_GAP_MS)
    }

    open suspend fun openCountForDay(pkg: String, epochDay: Long): Int? = openCountForDay(setOf(pkg), epochDay)

    /**
     * Returns a map of package name to foreground time in milliseconds for [pkgs] on [epochDay].
     * Returns null if usage stats permission is not granted.
     */
    open suspend fun foregroundMillisByPackageForDay(
        pkgs: Set<String>,
        epochDay: Long,
    ): Map<String, Long>? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (!ctx.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext emptyMap()

        val (startTime, dayEnd) = dayStartAndEndMillis(epochDay)
        val now = System.currentTimeMillis()
        if (startTime > now) return@withContext emptyMap()
        val endTime = minOf(dayEnd, now)

        val eventLogWrapper = EventLogWrapper(ctx)
        val foregroundStats = eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
        val aggregated = eventLogWrapper.aggregateForegroundStats(foregroundStats)
        val matchingStats = aggregated.filter { it.applicationId in pkgs }
        matchingStats.associate { it.applicationId to it.timeUsed }
    }

    /**
     * Returns 24 hourly buckets of distracting minutes today for [pkgs] using UsageEvents.
     * Sessions spanning hour boundaries are split between hours.
     * Future hours are empty (0 minutes).
     * Returns null if usage stats permission is not granted.
     */
    open suspend fun hourlyDistractingMinutesToday(
        pkgs: Set<String>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Int>? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        if (!ctx.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext List(HourlyUsage.HOURS_IN_DAY) { 0 }

        val today = LocalDate.now(zone)
        val startTime = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endTime = now

        val usageStatsManager = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext null

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val eventList = mutableListOf<UsageEventRecord>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName in pkgs) {
                eventList.add(UsageEventRecord(event.packageName, event.timeStamp, event.eventType))
            }
        }

        HourlyUsage.bucketEvents(eventList, pkgs, today, zone, now)
    }

    private fun startOfTodayMillis(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        const val RESUME_GAP_MS: Long = 2000L
        const val RECENT_RESUME_WINDOW_MS: Long = 5 * 60 * 1000L

        fun countOpens(timestamps: List<Long>, gapMs: Long = RESUME_GAP_MS): Int =
            app.olauncher.wellbeing.countOpens(timestamps, gapMs)

        fun countOpensForPackages(
            events: List<Pair<String, Long>>,
            pkgs: Set<String>,
            gapMs: Long = RESUME_GAP_MS,
        ): Int = app.olauncher.wellbeing.countOpensForPackages(events, pkgs, gapMs)

        fun bucketHourlyMinutes(
            events: List<UsageEventRecord>,
            pkgs: Set<String>,
            date: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
            now: Long = System.currentTimeMillis(),
        ): List<Int> = HourlyUsage.bucketEvents(events, pkgs, date, zone, now)
    }
}

/**
 * Pure function to count open occurrences from event [timestamps] (e.g. ACTIVITY_RESUMED),
 * de-duplicating events occurring less than [gapMs] apart.
 */
fun countOpens(timestamps: List<Long>, gapMs: Long = UsageRepository.RESUME_GAP_MS): Int {
    if (timestamps.isEmpty()) return 0
    val sorted = timestamps.sorted()
    var count = 0
    var lastCounted: Long? = null
    for (timestamp in sorted) {
        if (lastCounted == null || timestamp - lastCounted >= gapMs) {
            count++
            lastCounted = timestamp
        }
    }
    return count
}

/**
 * Pure function to count total opens for [pkgs] from event pairs of (packageName, timestamp),
 * de-duplicating resumes less than [gapMs] apart per package.
 */
fun countOpensForPackages(
    events: List<Pair<String, Long>>,
    pkgs: Set<String>,
    gapMs: Long = UsageRepository.RESUME_GAP_MS,
): Int {
    if (pkgs.isEmpty() || events.isEmpty()) return 0
    val byPkg = mutableMapOf<String, MutableList<Long>>()
    for ((pkg, time) in events) {
        if (pkg in pkgs) {
            byPkg.getOrPut(pkg) { mutableListOf() }.add(time)
        }
    }
    return byPkg.values.sumOf { countOpens(it, gapMs) }
}

/**
 * Pure function to check whether any event matches an ACTIVITY_RESUMED for one of [pkgs].
 */
fun hasRecentResumeEvent(
    events: List<Pair<String, Int>>,
    pkgs: Set<String>,
): Boolean {
    if (pkgs.isEmpty()) return false
    return events.any { (pkg, eventType) ->
        pkg in pkgs && eventType == UsageEvents.Event.ACTIVITY_RESUMED
    }
}

/**
 * Pure function to return the start and end timestamps in milliseconds for [epochDay] in [zone].
 */
fun dayStartAndEndMillis(epochDay: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
    val date = LocalDate.ofEpochDay(epochDay)
    val startTime = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val endTime = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return startTime to endTime
}

