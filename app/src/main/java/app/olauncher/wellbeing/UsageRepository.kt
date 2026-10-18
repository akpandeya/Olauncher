package app.olauncher.wellbeing

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Repository for querying application usage and open counts today.
 * Methods return null when usage stats permission has not been granted.
 */
class UsageRepository(private val context: Context) {

    /**
     * Returns the number of times [pkg] was opened today using UsageEvents ACTIVITY_RESUMED,
     * de-duplicating resumes less than 2 seconds apart.
     * Returns null if usage stats permission is not granted.
     */
    suspend fun openCountToday(pkg: String): Int? = withContext(Dispatchers.IO) {
        if (!context.appUsagePermissionGranted()) return@withContext null

        val startTime = startOfTodayMillis()
        val endTime = System.currentTimeMillis()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext null

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        val timestamps = mutableListOf<Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == pkg && event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                timestamps.add(event.timeStamp)
            }
        }

        countOpens(timestamps, RESUME_GAP_MS)
    }

    /**
     * Returns total foreground time in milliseconds for [pkgs] today using [EventLogWrapper].
     * Returns null if usage stats permission is not granted.
     */
    suspend fun foregroundMillisToday(pkgs: Set<String>): Long? = withContext(Dispatchers.IO) {
        if (!context.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext 0L

        val startTime = startOfTodayMillis()
        val endTime = System.currentTimeMillis()

        val eventLogWrapper = EventLogWrapper(context)
        val foregroundStats = eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
        val aggregated = eventLogWrapper.aggregateForegroundStats(foregroundStats)
        val matchingStats = aggregated.filter { it.applicationId in pkgs }
        eventLogWrapper.aggregateSimpleUsageStats(matchingStats)
    }

    /**
     * Checks if any of the given [pkgs] had an ACTIVITY_RESUMED event in the last [withinMillis] ms.
     * Returns false if usage access is not granted or if [pkgs] is empty.
     */
    suspend fun hasRecentResume(
        pkgs: Set<String>,
        withinMillis: Long = RECENT_RESUME_WINDOW_MS,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!context.appUsagePermissionGranted()) return@withContext false
        if (pkgs.isEmpty()) return@withContext false

        val endTime = System.currentTimeMillis()
        val startTime = endTime - withinMillis

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
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
        if (!context.appUsagePermissionGranted()) return@withContext null
        if (pkgs.isEmpty()) return@withContext emptyMap()

        val startTime = startOfTodayMillis()
        val endTime = System.currentTimeMillis()

        val eventLogWrapper = EventLogWrapper(context)
        val foregroundStats = eventLogWrapper.getForegroundStatsByTimestamps(startTime, endTime)
        val aggregated = eventLogWrapper.aggregateForegroundStats(foregroundStats)
        val matchingStats = aggregated.filter { it.applicationId in pkgs }
        matchingStats.associate { it.applicationId to it.timeUsed }
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

