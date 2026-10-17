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
